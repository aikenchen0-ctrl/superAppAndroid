plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
}

android {
    namespace = "com.paifa.ubikitouch.overlay"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
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

dependencies {
    implementation(project(":ubiki-core"))
    implementation("androidx.core:core-ktx:1.15.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                artifactId = "ubiki-overlay"
                from(components["release"])
            }
        }
    }
}
