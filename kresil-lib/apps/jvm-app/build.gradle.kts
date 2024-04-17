plugins {
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":kresil-lib:lib"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.test {
    useJUnitPlatform()
}
