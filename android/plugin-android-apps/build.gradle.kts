// Halone's Android apps: the Halone Android Integration plugin
// (github.com/HaloneXR/halone-android-integration, MIT, see LICENSE), built
// into the app so it needs no separate install or enabling. Halone finds it
// through the same plugin service the standalone plugin declares.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.halone.plugin.android"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        minSdk = 29
    }

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
}

dependencies {
    implementation(libs.halone.plugin.sdk)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
