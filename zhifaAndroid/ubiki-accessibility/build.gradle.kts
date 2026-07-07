plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

android {
    namespace = "com.paifa.ubikitouch.accessibility"
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    api(project(":ubiki-core"))
    implementation(project(":ubiki-overlay"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.emoji2:emoji2-emojipicker:1.6.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.savedstate:savedstate:1.2.1")
    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                artifactId = "ubiki-accessibility"
                from(components["release"])
            }
        }
    }
}
