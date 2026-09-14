pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "UbikiTouch"
include(":app")
include(":blink-api")
include(":univerge-core")
include(":univerge-heavy-drag-core")
include(":univerge-heavy-drag-android")
include(":univerge-heavy-drag-compose")
include(":univerge-overlay")
include(":univerge-accessibility")
include(":gesture-server")
include(":benchmark")
include(":adbcore")
