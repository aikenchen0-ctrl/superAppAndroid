import java.util.Properties

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("maven-publish")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use(::load)
    }
}

fun localOrEnv(propertyName: String, envName: String): String {
    return localProperties.getProperty(propertyName)
        ?: System.getenv(envName)
        ?: ""
}

fun buildConfigString(value: String): String {
    return "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"") + "\""
}

android {
    namespace = "com.paifa.ubikitouch.accessibility"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "SCRM_AUTO_BASE_URL",
                buildConfigString(localOrEnv("scrm.auto.baseUrl", "SCRM_AUTO_BASE_URL"))
            )
            buildConfigField(
                "String",
                "SCRM_AUTO_USERNAME",
                buildConfigString(localOrEnv("scrm.auto.username", "SCRM_AUTO_USERNAME"))
            )
            buildConfigField(
                "String",
                "SCRM_AUTO_PASSWORD",
                buildConfigString(localOrEnv("scrm.auto.password", "SCRM_AUTO_PASSWORD"))
            )
        }
        release {
            buildConfigField("String", "SCRM_AUTO_BASE_URL", buildConfigString(""))
            buildConfigField("String", "SCRM_AUTO_USERNAME", buildConfigString(""))
            buildConfigField("String", "SCRM_AUTO_PASSWORD", buildConfigString(""))
        }
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
        buildConfig = true
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
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.xerial:sqlite-jdbc:3.53.2.0")
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
