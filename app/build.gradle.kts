import java.net.URL

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.signlanguagetranslatorapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.signlanguagetranslatorapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Keep model files uncompressed so they can be memory-mapped straight from the APK.
    androidResources {
        noCompress += listOf("task", "bin")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mediapipe.tasks.vision)

    testImplementation(libs.junit)
}

// ---------------------------------------------------------------------------
// MediaPipe's hand_landmarker.task (~8 MB, Google's official model) is not kept
// in git. It is downloaded automatically before every build if it is missing,
// both on your PC and on GitHub Actions.
// ---------------------------------------------------------------------------
val handLandmarkerUrl =
    "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"
val handLandmarkerFile = layout.projectDirectory.file("src/main/assets/hand_landmarker.task").asFile

val downloadHandLandmarker by tasks.registering {
    group = "build setup"
    description = "Downloads MediaPipe hand_landmarker.task into src/main/assets if it is missing"
    onlyIf { !handLandmarkerFile.exists() || handLandmarkerFile.length() < 1_000_000L }
    doLast {
        handLandmarkerFile.parentFile.mkdirs()
        val tmp = File(handLandmarkerFile.parentFile, "hand_landmarker.task.part")
        logger.lifecycle("Downloading hand_landmarker.task ...")
        URL(handLandmarkerUrl).openStream().use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        check(tmp.length() > 1_000_000L) { "hand_landmarker.task download looks incomplete (${tmp.length()} bytes)" }
        if (handLandmarkerFile.exists()) handLandmarkerFile.delete()
        check(tmp.renameTo(handLandmarkerFile)) { "Could not move downloaded model into place" }
    }
}

tasks.named("preBuild") { dependsOn(downloadHandLandmarker) }
