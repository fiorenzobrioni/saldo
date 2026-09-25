// Opt-in, never applied by the build itself: `./gradlew --init-script gradle/google-maven-mirror.init.gradle.kts …`
//
// For sandboxes whose proxy gets HTTP 429 (Too Many Requests) from Maven Central, as the Claude
// Code cloud environment does: Gradle fires many parallel requests and repo.maven.apache.org
// rate-limits them, so resolution fails at random. Google's mirror of Maven Central serves the
// same artifacts without the limit. It is put ahead of the repositories in settings.gradle.kts,
// after Google's own; the checked-in build, CI and Android Studio are unchanged.
val mavenCentralMirror = "https://maven-central.storage-download.googleapis.com/maven2/"

beforeSettings {
    pluginManagement.repositories.google()
    pluginManagement.repositories.maven { url = uri(mavenCentralMirror) }
    // The portal stays reachable for the plugins resolved outside the two repositories above.
    pluginManagement.repositories.gradlePluginPortal()
    dependencyResolutionManagement.repositories.google()
    dependencyResolutionManagement.repositories.maven { url = uri(mavenCentralMirror) }
}

// Robolectric fetches its android-all jar from Maven Central itself, at test time and outside
// Gradle's resolution, so it needs pointing at the mirror too.
allprojects {
    tasks.withType<Test>().configureEach {
        systemProperty("robolectric.dependency.repo.url", mavenCentralMirror)
    }
}
