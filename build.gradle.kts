import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

// Plugins used by one or more modules — declared here apply-false so each module applies what it needs.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}

// Shared publishing for every module — configured once here instead of a copy-pasted block per
// module. Each module still publishes `com.siddharth.kmp:<module>` (+ platform variants) to GitHub
// Packages; credentials come from env (CI) or gradle properties (gitignored) — never committed.
subprojects {
    group = "com.siddharth.kmp"
    version = "1.0.0"
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType(MavenPublication::class.java).configureEach {
                // KMP modules expose a "kotlinMultiplatform" root publication + one per target
                // (android/jvm/…); the Android-only module (security) exposes a single "release".
                artifactId = when (name) {
                    "kotlinMultiplatform", "release" -> project.name
                    else -> "${project.name}-$name"
                }
            }
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = uri("https://maven.pkg.github.com/darkpandawarrior/kmp-toolkit")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                        password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
                    }
                }
            }
        }
    }
}
