import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.callbackdev.saldo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.callbackdev.saldo"
        minSdk = 33
        targetSdk = 36
        versionCode = 183
        versionName = "2.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Shared debug keystore (committed on purpose: a debug cert has no
        // release value) so every build - local or CI - produces the same
        // signature. Without it each machine's own ~/.android/debug.keystore
        // would sign differently and Android would refuse to update the app
        // in place, forcing an uninstall that wipes the test device's data.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }

        // Real release key (ADR 47, supersedes the artifact choice of ADR 38).
        // The keystore lives OUTSIDE the repo; the four properties come from
        // ~/.gradle/gradle.properties locally and from ORG_GRADLE_PROJECT_*
        // env vars (GitHub Secrets) in the release workflow. Only created when
        // fully configured, so a clean checkout still builds.
        val releaseStore = findProperty("SALDO_KEYSTORE") as String?
        val releaseStorePassword = findProperty("SALDO_KEYSTORE_PASSWORD") as String?
        val releaseKeyAlias = findProperty("SALDO_KEY_ALIAS") as String?
        val releaseKeyPassword = findProperty("SALDO_KEY_PASSWORD") as String?
        if (!releaseStore.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()
        ) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The real key wins whenever it is configured. Otherwise the
            // debug-key opt-in: with the flag, per-push CI signs the minified
            // build so it can actually be installed and smoke-tested (R8
            // breakage only shows up in a release build). Off by default so an
            // unconfigured checkout can never produce an installable release
            // by accident.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
                    .takeIf { project.hasProperty("signReleaseWithDebugKey") }
        }
        debug {
            // Dev builds are a different app id, so they install side-by-side with
            // the release-signed app instead of being uninstallable over it (same
            // id + different signature = no install at all). Series decision
            // (Aug 2026, ADR 48); snake had it from its skeleton. A debug res
            // overlay relabels the launcher icon "Saldo (dev)" to tell the two apart.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME feeds the version shown in Settings and About.
        buildConfig = true
    }

    // Exposes the exported Room schemas to MigrationTestHelper (instrumented tests).
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // KT-73255: opt in to the future default target of annotations on
        // constructor val/var parameters (param + property/field). Our only
        // affected annotations are Hilt qualifiers, inert on fields.
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

ksp {
    // Export the Room schema so future migrations can be diffed and tested (ADR: no destructive migrations).
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "17"
}

dependencies {
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)


    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager (recurring generation in the background) + Hilt integration
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    ksp(libs.hilt.compiler)

    // Kotlinx
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Charts
    implementation(libs.vico.compose.m3)

    // Debug
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Unit tests (JVM, JUnit 5)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests (JUnit 4, required by Compose UI Test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
