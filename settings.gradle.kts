pluginManagement {
    includeBuild("convention-plugins")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kresil"
// kresil lib
include(":kresil-lib:lib")
// kresil lib-apps
include(":kresil-lib:apps:android-app")
include(":kresil-lib:apps:jvm-app")
include(":kresil-lib:apps:kotlin-jvm-app")

// ktor-plugins lib
include(":ktor-plugins:shared")
// ktor-plugins lib-apps
include(":ktor-plugins:apps:android-app")
include(":ktor-plugins:apps:jvm-app")
include(":ktor-plugins:apps:kotlin-jvm-app")
