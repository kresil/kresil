plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

application {
    mainClass = "application.MainKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.configyaml)
    implementation(libs.logback.classic)
}
