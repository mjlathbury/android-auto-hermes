import java.util.Properties
import java.io.File

// Auto-incrementing build number, persisted in version.properties so it survives across builds.
val versionPropsFile = File(rootDir, "version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) load(versionPropsFile.inputStream())
}
// Bump versionCode on every build so each APK is uniquely identifiable.
var buildNumber = (versionProps["BUILD_NUMBER"]?.toString()?.toIntOrNull() ?: 1) + 1
versionProps["BUILD_NUMBER"] = buildNumber.toString()
versionProps.store(versionPropsFile.writer(), "Hermes Drive auto-incremented build number")

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hermes.drive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hermes.drive"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.2.0-b$buildNumber"
        // Expose build metadata to the app (Settings screen + debug log).
        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())
        buildConfigField("String", "VERSION_NAME", "\"$versionName\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.datastore:datastore-preferences:1.1.4")
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

    testImplementation("junit:junit:4.13.2")
}
