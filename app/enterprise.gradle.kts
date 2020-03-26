import com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin
import java.io.ByteArrayOutputStream

buildscript {
    val pluginVersion = "3.2"
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.gradle:gradle-enterprise-gradle-plugin:${pluginVersion}")
    }
}

pluginManager.apply(GradleEnterprisePlugin::class)

val isCi = "JENKINS_URL" in System.getenv()
fun String.encodeURL() = java.net.URLEncoder.encode(this, "UTF-8")
fun String.trimAtEnd() = ("x$this").trim().substring(1)

// https://github.com/gradle/gradle-build-scan-snippets/blob/master/guided-trials-default-custom-user-data/default-custom-user-data.gradle
class BuildScanUtils(val buildScan: com.gradle.scan.plugin.BuildScanExtension) {
    private fun appendIfMissing(str: String, suffix: String): String {
        return if (str.endsWith(suffix)) str else str + suffix
    }

    private fun customValueSearchUrl(search: Map<String, String>): String {
        val query = search.map { (name, value) ->
            "search.names=${name.encodeURL()}&search.values=${value.encodeURL()}"
        }.joinToString("&")
        return "${appendIfMissing(buildScan.server, "/")}scans?$query"
    }

    private fun addCustomValueSearchLink(title: String, search: Map<String, String>) {
        if (buildScan.server.isNullOrBlank().not()) {
            buildScan.link(title, customValueSearchUrl(search))
        }
    }

    private fun execAndGetStdout(args: Array<String>): String {
        val stdout = java.io.ByteArrayOutputStream()
        exec {
            commandLine(*args)
            standardOutput = stdout
            workingDir = rootDir
        }
        return stdout.toString().trimAtEnd()
    }

    fun tagOs() {
        buildScan.tag(System.getProperty("os.name"))
    }

    fun tagIde() {
        // TODO: broken?
        // https://github.com/gradle/gradle-build-scan-snippets/blob/master/guided-trials-default-custom-user-data/default-custom-user-data.gradle
//            if (project.hasProperty("android.injected.invoked.from.ide")) {
//                buildScan.tag("Android Studio")
//            } else if (System.getProperty("idea.version") != null) {
//                buildScan.tag("IntelliJ IDEA")
//            } else if (!isCi) {
//                buildScan.tag("Cmd Line")
//            }
    }

    fun tagCiOrLocal() {
        buildScan.tag(if (isCi) "CI" else "local")
    }

    fun addJenkinsMetadata() {
        val buildUrl = System.getenv("BUILD_URL")
        if (buildUrl != null) {
            buildScan.link("Jenkins build", buildUrl)
        }

        val buildNumber = System.getenv("BUILD_NUMBER")
        if (buildNumber != null) {
            buildScan.value("CI build number", buildNumber)
        }

        val nodeName = System.getenv("NODE_NAME")
        if (nodeName != null) {
            val agentName = if (nodeName == "master") "master-node" else nodeName
            buildScan.tag(agentName)
            buildScan.value("CI node name", agentName)
        }

        val jobName = System.getenv("JOB_NAME")
        if (jobName != null) {
            val jobNameLabel = "CI job"
            buildScan.value(jobNameLabel, jobName)
            addCustomValueSearchLink("CI job build scans", mapOf(jobNameLabel to jobName))
        }

        val stageName = System.getenv("STAGE_NAME")
        if (stageName != null) {
            val stageNameLabel = "CI stage"
            buildScan.value(stageNameLabel, stageName)
            addCustomValueSearchLink("CI stage build scans", mapOf(stageNameLabel to stageName))
        }
    }

    fun addGitMetadata() {
        buildScan.background {
            val gitCommitId =
                execAndGetStdout(arrayOf("git", "rev-parse", "--short=8", "--verify", "HEAD"))
            val gitBranchName =
                execAndGetStdout(arrayOf("git", "rev-parse", "--abbrev-ref", "HEAD"))
            val gitStatus = execAndGetStdout(arrayOf("git", "status", "--porcelain"))

            if (gitCommitId.isNotBlank()) {
                val commitIdLabel = "Git commit id"
                value(commitIdLabel, gitCommitId)
                addCustomValueSearchLink(
                    "Git commit id build scans",
                    mapOf(commitIdLabel to gitCommitId)
                )
                val originUrl =
                    execAndGetStdout(arrayOf("git", "config", "--get", "remote.origin.url"))
                if (originUrl.contains("github.com")) { // only for GitHub
                    val repoRgx = "(.*)github\\.com[\\/|:](.*)".toRegex()
                    val repoRgxMatch = repoRgx.matchEntire(originUrl)
                    var repoPath = repoRgxMatch?.groupValues?.get(2) ?: ""

                    if (repoPath.endsWith(".git")) {
                        repoPath = repoPath.substring(0, repoPath.length - 4)
                    }
                    link("Github Source", "https://github.com/$repoPath/tree/$gitCommitId")
                }
            }

            if (gitBranchName.isNotBlank()) {
                tag(gitBranchName)
                value("Git branch", gitBranchName)
            }

            if (gitStatus.isNotBlank()) {
                tag("Dirty")
                value("Git status", gitStatus)
            }
        }
    }
}


gradleEnterprise {
    buildScan {
        val scan = BuildScanUtils(buildScan)

        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"

        if (isCi) {
            scan.tagOs()
            scan.tagIde()
            scan.tagCiOrLocal()
            scan.addJenkinsMetadata()
            scan.addGitMetadata()

            publishAlways()
            isCaptureTaskInputFiles = true

            background {
                val os = java.io.ByteArrayOutputStream()
                exec {
                    commandLine("git", "rev-parse", "--verify", "HEAD")
                    standardOutput = os
                    workingDir = rootDir
                }
                value("Git Commit ID", os.toString())
            }

            // Jenkins metadata
            scan.addJenkinsMetadata()
        }

        buildFinished {
            if (this.failure != null) {
                value("Failed with", this.failure.message)
            }
        }
    }
    server = "https://enterprise-training.gradle.com/"
}
