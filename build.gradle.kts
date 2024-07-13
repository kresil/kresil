import org.jetbrains.dokka.gradle.*
import org.jetbrains.dokka.DokkaConfiguration.Visibility
import java.net.URL

plugins {
    id("root.publication")
    // trick: for the same plugin versions in all sub-modules (apply false)
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.dokka)
}

val repoBaseUrl = "https://github.com/kresil/kresil/tree/main"
val projectsToGenerateApiDocsFor = mapOf(
    ":kresil-lib:lib" to "kresil-lib",
    ":ktor-server-plugins:shared" to "ktor-server-plugins",
    ":ktor-client-plugins:shared" to "ktor-client-plugins"
)

subprojects {
    val displayName = projectsToGenerateApiDocsFor[path]
    if (displayName != null) {
        // apply and configure Dokka plugin
        apply(plugin = "org.jetbrains.dokka")
        configureDokkaSetup(displayName)
    }
}

// configure Dokka setup
// ref: https://kotlin.github.io/dokka/1.5.30/user_guide/gradle/usage/#configuration-options
private fun Project.configureDokkaSetup(moduleDisplayName: String) {
    tasks.withType<DokkaTaskPartial>().configureEach {
        moduleName.set(moduleDisplayName)
        dokkaSourceSets.configureEach {
            documentedVisibilities.set(setOf(
                Visibility.PUBLIC,
                Visibility.INTERNAL,
                Visibility.PROTECTED,
                Visibility.PRIVATE,
            ))
            // Read docs for more details: https://kotlinlang.org/docs/dokka-gradle.html#source-link-configuration
            sourceLink {
                localDirectory.set(rootProject.projectDir)
                remoteUrl.set(URL(repoBaseUrl))
                remoteLineSuffix.set("#L")
            }
        }
    }
}
