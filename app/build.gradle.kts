plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.zahranet.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.zahranet.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed so the release APK installs for testing. Use your own keystore for distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // The Aether core is executed as a process, so native libs must be extracted to disk.
    packaging { jniLibs { useLegacyPackaging = true } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}

// The app has no fake mode: refuse to build an APK that lacks the real native components.
val checkNativeLibs = tasks.register("checkNativeLibs") {
    doLast {
        val dir = file("src/main/jniLibs/arm64-v8a")
        listOf("libaether.so", "libhev-socks5-tunnel.so").forEach {
            if (!File(dir, it).exists()) {
                throw GradleException("Missing $it in ${dir.path}. Run scripts/fetch_native.sh or copy the real files there (see README).")
            }
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(checkNativeLibs) }
