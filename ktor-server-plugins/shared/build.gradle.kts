plugins {
    id("module.publication")
    alias(libs.plugins.kotlinMultiplatform)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
}

kotlin {
    // applyDefaultHierarchyTemplate(), explicitly setting the hierarchy for learning purposes
    jvm()
    // linuxX64() TODO: variant is not found even though it is the only linux target supported by ktor server
    sourceSets {

        // Source Set Category: Common
        // use `by creating` if a source set does not exist yet
        val commonMain by getting {
            dependencies {
                // api (former compile) is used to expose the dependency to the consumers
                api(project(":kresil-lib:lib"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.server.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
