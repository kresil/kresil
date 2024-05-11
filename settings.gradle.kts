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
include(":kresil-lib:apps:android-app")
include(":kresil-lib:apps:jvm-app")
include(":kresil-lib:apps:kotlin-jvm-app")

// ktor-client-plugins
include(":ktor-client-plugins:shared")
include(":ktor-client-plugins:apps:android-app")
include(":ktor-client-plugins:apps:jvm-app")
include(":ktor-client-plugins:apps:kotlin-jvm-app")

// ktor-server-plugins
include(":ktor-server-plugins:shared")
include(":ktor-server-plugins:apps:android-app")
include(":ktor-server-plugins:apps:jvm-app")
include(":ktor-server-plugins:apps:kotlin-jvm-app")
