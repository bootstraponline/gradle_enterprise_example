# Gradle Enterprise Example

<img src="./png/tag_cmd_line.png" width="50%" />
<img src="./png/tag_idea.png" width="50%" />
<img src="./png/tag_studio.png" width="50%" />

`enterprise.gradle.kts` provides rich metadata for builds published to gradle enterprise.

`settings.gradle.kts` uses `apply(from = "enterprise.gradle.kts")` to configure Gradle Enterprise.

[default-custom-user-data.gradle](https://github.com/gradle/gradle-build-scan-snippets/blob/master/guided-trials-default-custom-user-data/default-custom-user-data.gradle) has been converted to Kotlin (with non-Jenkins CI removed).

- [settings.gradle.kts](https://github.com/bootstraponline/gradle_enterprise_example/blob/master/app/settings.gradle.kts)
- [enterprise.gradle.kts](https://github.com/bootstraponline/gradle_enterprise_example/blob/master/app/enterprise.gradle.kts)

## Testing

- Run `export JENKINS_URL=true` so gradle thinks we're running on Jenkins. By default, the script will publish only on CI.
- `./gradlew clean` will produce a build scan.

## Jenkins integration

- `Credentials` then click `System`. Add a Global credential.
- Kind: Secret Text. Set text using the `domain=accesskey` format
- Specify an id. `gradle-enterprise`

Update the Jenkinsfile with

```
environment {
    GRADLE_ENTERPRISE_ACCESS_KEY = credentials('gradle-enterprise')
}
```

## Research

Detecting IntelliJ vs Android Studio

Android Studio:
- `idea.executable=studio`
- `idea.paths.selector=AndroidStudioPreview4.1`
- `idea.platform.prefix=AndroidStudio`

IntelliJ Community:
- `idea.executable=idea`
- `idea.paths.selector=IdeaIC2019.3`

Dump system properties:

```kotlin
var props = System.getProperties()
val writer = java.io.FileWriter(file("community.properties"))
writer.use { writer ->
    props.store(writer, "")
    writer.flush()
}
```
