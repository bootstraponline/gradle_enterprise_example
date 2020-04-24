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
        classpath("com.gradle:gradle-enterprise-gradle-plugin:$pluginVersion")
    }
}

pluginManager.apply(GradleEnterprisePlugin::class)

//  https://github.com/gradle/gradle-build-scan-snippets/blob/master/guided-trials-default-custom-user-data/default-custom-user-data.gradle

val isJenkins = "JENKINS_URL" in System.getenv()
val isBuildkite = "BUILDKITE" in System.getenv()
val isCi = isJenkins || isBuildkite
fun String.encodeUrl() = encode(this, "UTF-8")
fun String.trimAtEnd() = ("x$this").trim().substring(1)

fun BuildScanExtension.tagOs() {
    this.tag(System.getProperty("os.name"))
}

// Note: settings.gradle.kts does not have access to the `project` object.
// https://github.com/gradle/gradle-build-scan-snippets/blob/master/guided-trials-default-custom-user-data/default-custom-user-data.gradle
fun BuildScanExtension.tagIde() {
    // example value: studio
    val ideaExecutable = System.getProperty("idea.executable")
    // example value: AndroidStudioPreview4.1
    val ideaSelector = System.getProperty("idea.paths.selector")

    if (ideaExecutable != null) this.tag(ideaExecutable)
    if (ideaSelector != null) this.tag(ideaSelector)

    if (ideaExecutable == null &&
        ideaSelector == null &&
        !isCi
    ) this.tag("Cmd Line")
}

fun BuildScanExtension.tagCiOrLocal() {
    this.tag(if (isCi) "CI" else "local")
}

val ciName = when {
        isJenkins -> "Jenkins"
        isBuildkite -> "Buildkite"
        else -> ""
    }

fun BuildScanExtension.addCiMetadata(
    buildUrl: String,
    buildNumber: String,
    nodeName: String,
    jobName: String,
    stageName: String
) {
    if (ciName.isNotBlank()) this.tag(ciName)

    val buildUrl = System.getenv(buildUrl)
    if (buildUrl != null) {
        this.link("$ciName build", buildUrl)
    }

    val buildNumber = System.getenv(buildNumber)
    if (buildNumber != null) {
        this.value("CI build number", buildNumber)
    }

    val nodeName = System.getenv(nodeName)
    if (nodeName != null) {
        val agentName = if (nodeName == "master") "master-node" else nodeName
        this.tag(agentName)
        this.value("CI node name", agentName)
    }

    val jobName = System.getenv(jobName)
    if (jobName != null) {
        val jobNameLabel = "CI job"
        this.value(jobNameLabel, jobName)
        this.addCustomValueSearchLink(
            "CI job build scans",
            mapOf(jobNameLabel to jobName)
        )
    }

    val stageName = System.getenv(stageName)
    if (stageName != null) {
        val stageNameLabel = "CI stage"
        this.value(stageNameLabel, stageName)
        this.addCustomValueSearchLink(
            "CI stage build scans",
            mapOf(stageNameLabel to stageName)
        )
    }
}

fun BuildScanExtension.addJenkinsMetadata() {
    addCiMetadata(
        buildUrl = "BUILD_URL",
        buildNumber = "BUILD_NUMBER",
        nodeName = "NODE_NAME",
        jobName = "JOB_NAME",
        stageName = "STAGE_NAME"
    )
}

fun BuildScanExtension.addBuildkiteMetadata() {
    addCiMetadata(
        buildUrl = "BUILDKITE_BUILD_URL",
        buildNumber = "BUILDKITE_BUILD_NUMBER",
        nodeName = "BUILDKITE_AGENT_NAME",
        jobName = "BUILDKITE_LABEL",
        stageName = "BUILDKITE_PIPELINE_NAME"
    )
}

fun BuildScanExtension.addCiMetadata() {
    when {
        isJenkins -> addJenkinsMetadata()
        isBuildkite -> addBuildkiteMetadata()
    }
}

fun isGitRepo(): Boolean {
    return try {
        exec {
            commandLine("git", "status")
            workingDir = rootDir
        }
        true
    } catch (_: Throwable) {
        false
    }
}

fun BuildScanExtension.addGitMetadata() {
    // Ensure we're in a git repo before calling git commands
    // Otherwise Gradle will complain because the git exit code is non-zero
    if (!isGitRepo()) return

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
            this.addCustomValueSearchLink(
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

fun BuildScanExtension.customValueSearchUrl(search: Map<String, String>): String {
    val query = search.map { (name: String, value: String) ->
        "search.names=${name.encodeUrl()}&search.values=${value.encodeUrl()}"
    }.joinToString("&")
    return "${appendIfMissing(this.server)}scans?$query"
}

fun BuildScanExtension.addCustomValueSearchLink(
    title: String,
    search: Map<String, String>
) {
    if (this.server?.isNotBlank() == true) {
        this.link(title, this.customValueSearchUrl(search))
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

        with(buildScan) {
            tagOs()
            tagCiOrLocal()
            tagIde()
            addCiMetadata()
            addGitMetadata()
        }

        publishAlways()
        isCaptureTaskInputFiles = true

        buildFinished {
            if (this.failure != null) {
                value("Failed with", this.failure.message)
            }
        }
    }
}
