# Gradle Enterprise Example

`settings.gradle.kts` uses `apply(from = "enterprise.gradle.kts")` to configure Gradle Enterprise.

[default-custom-user-data.gradle](https://github.com/gradle/gradle-build-scan-snippets/blob/master/guided-trials-default-custom-user-data/default-custom-user-data.gradle) has been converted to Kotlin (with non-Jenkins CI removed).

- [settings.gradle.kts](https://github.com/bootstraponline/gradle_enterprise_example/blob/master/app/settings.gradle.kts)
- [enterprise.gradle.kts](https://github.com/bootstraponline/gradle_enterprise_example/blob/master/app/enterprise.gradle.kts)

## Testing

- Run `export JENKINS_URL=true` so gradle thinks we're running on Jenkins. By default, the script will publish only on CI.
- `./gradlew clean` will produce a build scan.
