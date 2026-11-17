import io.eksoda.buildlogic.configureKotlinJvmTarget
import io.eksoda.buildlogic.configureQuality
import io.eksoda.buildlogic.libs
import io.eksoda.buildlogic.library
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * A plain Kotlin/JVM module with no Android (core:engine, core:model). Explicit API mode and
 * JUnit 5.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            extensions.configure<KotlinJvmProjectExtension> {
                explicitApi()
                compilerOptions.freeCompilerArgs.add("-Xjdk-release=17")
            }
            configureKotlinJvmTarget()
            configureQuality()
            tasks.withType<Test>().configureEach {
                useJUnitPlatform {
                    // Tests that fetch real receipt pages run only when asked for (-Plive).
                    if (!providers.gradleProperty("live").isPresent) excludeTags("live")
                }
            }
            dependencies {
                "testImplementation"(platform(libs.library("junit5-bom")))
                "testImplementation"(libs.library("junit5-jupiter"))
                "testImplementation"(libs.library("assertk"))
                "testRuntimeOnly"(libs.library("junit5-platform-launcher"))
            }
        }
    }
}
