plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.pihole.android.core.tor"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

val ndkHome = file("${rootDir}/.android-sdk/ndk/26.3.11579264")
val artiBridgeDir = file("${projectDir}/arti-bridge")
val jniLibsOutDir = file("${projectDir}/src/main/jniLibs")

val buildArtiJni by tasks.registering(Exec::class) {
    description = "Build Arti JNI bridge via cargo-ndk"
    workingDir = artiBridgeDir
    environment("ANDROID_NDK_HOME", ndkHome.absolutePath)
    environment("NDK_HOME", ndkHome.absolutePath)

    // Build all common ABIs. This can be trimmed later.
    commandLine(
        "cargo",
        "ndk",
        "--target",
        "arm64-v8a",
        "--target",
        "armeabi-v7a",
        "--target",
        "x86",
        "--target",
        "x86_64",
        "--output-dir",
        jniLibsOutDir.absolutePath,
        "build",
        "--release",
    )
}

// Ensure JNI libs exist before compiling Kotlin (so System.loadLibrary works on-device).
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(buildArtiJni)
}

dependencies {
    implementation(project(":core:upstream"))
    implementation(libs.tor.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.truth)
}
