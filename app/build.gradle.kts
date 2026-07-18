import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.singular.crystalball"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.singular.crystalball"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Keep the git sha out of the APK. F-Droid rebuilds the *tagged* commit and compares
            // byte-for-byte with the published binary, so an embedded sha turns any slip between
            // "what was built" and "what was tagged" into an unreproducible release. Without it,
            // the two can't disagree.
            vcsInfo { include = false }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.12.4")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    // 2.9.x is compiled against API 36; 2.10+/2.11 require compileSdk 37.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Local JVM unit tests — the recogniser and chord library are pure Kotlin.
    testImplementation("junit:junit:4.13.2")
    // The android.jar stub throws "not mocked" for org.json, and the song library's format is
    // exactly the thing worth testing. This is the same API, as an actual implementation.
    testImplementation("org.json:json:20240303")
}
