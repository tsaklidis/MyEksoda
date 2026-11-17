package io.eksoda.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/**
 * Spotless (ktlint) and detekt for one module, plus the rule that only core:engine may depend on
 * OkHttp (the engine's fetcher is the only HTTP client in the app).
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

    pluginManager.apply("dev.detekt")
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin")
    }
    tasks.withType<Detekt>().configureEach { jvmTarget.set("17") }

    if (path != ENGINE_PATH) {
        configurations.configureEach {
            val configurationName = name
            dependencies.configureEach {
                if (group == OKHTTP_GROUP) {
                    throw GradleException(
                        "$path declares $group:$name in '$configurationName'. Only $ENGINE_PATH " +
                            "may use OkHttp.",
                    )
                }
            }
        }
    }
}

private const val ENGINE_PATH = ":core:engine"
private const val OKHTTP_GROUP = "com.squareup.okhttp3"
