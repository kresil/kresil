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
// lib
include(":kresil-lib:lib")
// lib-apps
include(":kresil-lib:apps:android-app")
include(":kresil-lib:apps:jvm-app")
include(":kresil-lib:apps:kotlin-jvm-app")

// ktor plugins
include(":ktor-plugins:lib")
// lib-apps
include(":ktor-plugins:apps:android-app")
include(":ktor-plugins:apps:jvm-app")
include(":ktor-plugins:apps:kotlin-jvm-app")
