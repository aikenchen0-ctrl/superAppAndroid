plugins {
    id("org.jetbrains.kotlin.jvm")
    id("maven-publish")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            artifactId = "ubiki-core"
            from(components["java"])
        }
    }
}
