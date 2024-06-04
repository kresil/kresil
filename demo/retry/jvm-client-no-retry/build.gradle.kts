plugins {
    alias(libs.plugins.kotlinJvm)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":ktor-client-plugins:shared"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
