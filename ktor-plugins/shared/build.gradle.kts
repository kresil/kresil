import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("module.publication")
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
}

kotlin {
    // applyDefaultHierarchyTemplate(), explicitly setting the hierarchy for learning purposes
    jvm()
    androidTarget {
        // Needed for the Android library artifact to be published
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions {
                jvmTarget = "1.8"
            }
        }
    }
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("native")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("native")
        hostOs == "Linux" && isArm64 -> linuxArm64("native")
        hostOs == "Linux" && !isArm64 -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    /*nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }*/

    sourceSets {
        /**
         * - common
         *    - jvm
         *    - android
         *    - native
         *      - linuxX64
         *    - js
         *      - node
         *      - browser
         */

        // Source Set Category: Common
        // use `by creating` if a source set does not exist yet
        val commonMain by getting {
            dependencies {
                // api (former compile) is used to expose the dependency to the consumers
                api(project(":kresil-lib:lib"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        // Source Set Category: Intermediary
        val nativeMain by getting {
            dependsOn(commonMain)
        }

        val nativeTest by getting {
            dependsOn(commonTest)
        }

        // Source Set Category: Platform
        val androidMain by getting {
            dependsOn(commonMain)
        }

        val androidUnitTest by getting {
            dependsOn(commonTest)
        }
    }
}

android {
    namespace = "kresil.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
dependencies {
    implementation(project(":kresil-lib:lib"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<AbstractTestTask> {
    // see all logs even from passed tests
    testLogging {
        events("standardOut", "started", "passed", "skipped", "failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
