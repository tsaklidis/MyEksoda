package io.eksoda.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Spotless (ktlint) and detekt for one module (docs/android-standalone.md §5.1, §12.2), plus the
 * rule that only core:engine may depend on OkHttp (§8.1: the engine's fetcher is the only HTTP
 * client in the app).
 */
internal fun Project.configureQuality() {
    pluginManager.apply("com.diffplug.spotless")
    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.versionOf("ktlint"))
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versionOf("ktlint"))
        }
    }

    pluginManager.apply("io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
    }
    // detekt 1.23 runs its own Kotlin compiler. Gradle would otherwise align it with the
    // project's Kotlin version and detekt refuses to start.
    val detektKotlin = libs.versionOf("detektKotlin")
    configurations.matching { it.name == "detekt" }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion(detektKotlin)
        }
    }

    if (path != ENGINE_PATH) {
        configurations.configureEach {
            val configurationName = name
            dependencies.configureEach {
                if (group == OKHTTP_GROUP) {
                    throw GradleException(
                        "$path declares $group:$name in '$configurationName'. Only $ENGINE_PATH " +
                            "may use OkHttp (docs/android-standalone.md §8.1).",
                    )
                }
            }
        }
    }
}

private const val ENGINE_PATH = ":core:engine"
private const val OKHTTP_GROUP = "com.squareup.okhttp3"
