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
// apps
include(":kresil-lib:apps:android-app")
include(":kresil-lib:apps:jvm-app")
include(":kresil-lib:apps:kotlin-jvm-app")
