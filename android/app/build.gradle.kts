import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Alpha builds are versioned by date: 2026.10.11-alpha. Pass -Phalone.date=2026.10.11 to pin one.
val buildDate: String = (findProperty("halone.date") as String?)
    ?: LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))

android {
    namespace = "dev.halone.app"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "dev.halone.app"
        minSdk = 29
        targetSdk = 36
        versionCode = buildDate.replace(".", "").toInt()
        versionName = "$buildDate-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    packaging {
        // Unpacked on install, as files: the NPU half of Qualcomm's runtime
        // (the "skel" libraries) is loaded by the NPU itself from
        // nativeLibraryDir, and can't be read out of the APK.
        jniLibs.useLegacyPackaging = true
        // Parts of Qualcomm's runtime Halone never loads: its own GPU
        // backend (LiteRT has one) and the NPU of the Snapdragon 888 (V68),
        // older than the delegate supports. ~7 MB of the APK.
        jniLibs.excludes += setOf("**/libQnnGpu*.so", "**/libQnnHtpV68*.so")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // The system apps, served from https://halone.local/ by WebViewAssetLoader.
    // Built by `pnpm build` in web/; absent until then, and the shell says so.
    sourceSets {
        getByName("main").assets.srcDir("../../web/apps/system/dist")
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
    implementation(project(":core-glasses"))
    implementation(project(":core-display"))
    implementation(project(":core-compositor"))
    implementation(project(":engine-webview"))
    implementation(project(":engine-plugin"))
    implementation(project(":core-shell"))
    // Android apps on the glasses, built in rather than a separate plugin install.
    implementation(project(":plugin-android-apps"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.webkit)
    implementation(libs.geckoview)
    // 2D-to-3D conversion: on-device depth estimation.
    implementation(libs.litert)
    implementation(libs.litert.gpu)
    implementation(libs.litert.gpu.api)
    // The Snapdragon NPU, for estimating depth off the GPU.
    implementation(libs.qnn.runtime)
    implementation(libs.qnn.litert.delegate)

    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
