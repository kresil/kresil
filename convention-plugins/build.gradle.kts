plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.nexus.publish)
}

kotlin {
    jvmToolchain(17)
}