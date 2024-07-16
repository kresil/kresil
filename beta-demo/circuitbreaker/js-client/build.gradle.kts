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
    implementation(npm("chart.js", "3.5.1"))
    implementation(npm("chartjs-adapter-date-fns", "3.0.0"))
}

kotlin {
    js(IR) {
        binaries.executable()
        browser {

        }
    }
    jvmToolchain(21)
}
