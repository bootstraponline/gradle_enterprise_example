import com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin
import java.io.ByteArrayOutputStream
import java.net.URLEncoder.encode
import com.gradle.scan.plugin.BuildScanExtension

// id("com.gradle.enterprise").version("3.2") is only usable from build.gradle or settings.gradle
// buildscript {...} / pluginManager.apply() is the equivalent for a script plugin.
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

//  https://github.com/gradle/gradle-build-scan-snippets/blob/master/guided-trials-default-custom-user-data/default-custom-user-data.gradle
val isCi = "JENKINS_URL" in System.getenv()
fun String.encodeURL() = encode(this, "UTF-8")
fun String.trimAtEnd() = ("x$this").trim().substring(1)

fun BuildScanExtension.tagOs() {
    this.tag(System.getProperty("os.name"))
}

fun BuildScanExtension.tagIde() {
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

fun BuildScanExtension.tagCiOrLocal() {
    this.tag(if (isCi) "CI" else "local")
}

fun BuildScanExtension.addJenkinsMetadata() {
    val buildUrl = System.getenv("BUILD_URL")
    if (buildUrl != null) {
        this.link("Jenkins build", buildUrl)
    }

    val buildNumber = System.getenv("BUILD_NUMBER")
    if (buildNumber != null) {
        this.value("CI build number", buildNumber)
    }

    val nodeName = System.getenv("NODE_NAME")
    if (nodeName != null) {
        val agentName = if (nodeName == "master") "master-node" else nodeName
        this.tag(agentName)
        this.value("CI node name", agentName)
    }

    val jobName = System.getenv("JOB_NAME")
    if (jobName != null) {
        val jobNameLabel = "CI job"
        this.value(jobNameLabel, jobName)
        addCustomValueSearchLink(
            this,
            "CI job build scans",
            mapOf(jobNameLabel to jobName)
        )
    }

    val stageName = System.getenv("STAGE_NAME")
    if (stageName != null) {
        val stageNameLabel = "CI stage"
        this.value(stageNameLabel, stageName)
        addCustomValueSearchLink(
            this,
            "CI stage build scans",
            mapOf(stageNameLabel to stageName)
        )
    }
}

fun BuildScanExtension.addGitMetadata() {
    this.background {
        val gitCommitId =
            execAndGetStdout(
                arrayOf(
                    "git",
                    "rev-parse",
                    "--short=8",
                    "--verify",
                    "HEAD"
                )
            )
        val gitBranchName =
            execAndGetStdout(arrayOf("git", "rev-parse", "--abbrev-ref", "HEAD"))
        val gitStatus = execAndGetStdout(arrayOf("git", "status", "--porcelain"))

        if (gitCommitId.isNotBlank()) {
            val commitIdLabel = "Git commit id"
            value(commitIdLabel, gitCommitId)
            addCustomValueSearchLink(
                this,
                "Git commit id build scans",
                mapOf(commitIdLabel to gitCommitId)
            )
            val originUrl =
                execAndGetStdout(
                    arrayOf(
                        "git",
                        "config",
                        "--get",
                        "remote.origin.url"
                    )
                )
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

fun appendIfMissing(str: String): String {
    val suffix = "/"
    return if (str.endsWith(suffix)) str else str + suffix
}

fun customValueSearchUrl(buildScan: BuildScanExtension, search: Map<String, String>): String {
    val query = search.map { (name: String, value: String) ->
        "search.names=${name.encodeURL()}&search.values=${value.encodeURL()}"
    }.joinToString("&")
    return "${appendIfMissing(buildScan.server)}scans?$query"
}

fun addCustomValueSearchLink(
    buildScan: BuildScanExtension,
    title: String,
    search: Map<String, String>
) {
    if (buildScan.server.isNullOrBlank().not()) {
        buildScan.link(title, customValueSearchUrl(buildScan, search))
    }
}

fun execAndGetStdout(args: Array<String>): String {
    val stdout = ByteArrayOutputStream()
    exec {
        commandLine(*args)
        standardOutput = stdout
        workingDir = rootDir
    }
    return stdout.toString().trimAtEnd()
}

gradleEnterprise {
    buildScan {
        server = "https://enterprise-training.gradle.com/"
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"

        if (isCi) {
            with(buildScan) {
                tagOs()
                tagIde()
                tagCiOrLocal()
                addJenkinsMetadata()
                addGitMetadata()
            }

            publishAlways()
            isCaptureTaskInputFiles = true
        }

        buildFinished {
            if (this.failure != null) {
                value("Failed with", this.failure.message)
            }
        }
    }
}
