plugins {
    kotlin("js")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ktor-client-plugins:shared"))
    implementation(libs.kotlin.stdlib.js)
    implementation(libs.ktor.client.js)
}

kotlin {
    js(IR) {
        binaries.executable()
        browser {

        }
    }
    jvmToolchain(21)
}
