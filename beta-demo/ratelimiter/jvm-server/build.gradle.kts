plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
    application
}

application {
    mainClass = "application.MainKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.redis.lettuce)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    api(project(":kresil-lib:lib"))
}
