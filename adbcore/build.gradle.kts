// AGP 9.0+ includes built-in Kotlin support, so do NOT apply
// "org.jetbrains.kotlin.android" separately.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adbcore"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 26  // Wireless debugging APIs are guarded at runtime; host app supports API 26.
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=none"
            }
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    buildFeatures {
        prefab = true
        aidl = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")

    // Hidden API stub — provides com.android.org.conscrypt.Conscrypt,
    // android.content.IContentProvider, android.app.IActivityManager, etc.
    // Only needed at compile time; resolved by the Android runtime.
    compileOnly("dev.rikka.hidden:stub:4.4.0")

    // For ADB key signing / X.509 certificate generation
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")

    // BoringSSL prefab provides SPAKE2 used by libadb.so
    implementation("io.github.vvb2060.ndk:boringssl:20250114")
    implementation("org.lsposed.libcxx:libcxx:27.0.12077973")
}
