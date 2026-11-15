import com.android.build.api.dsl.LibraryExtension
import io.eksoda.buildlogic.Sdk
import io.eksoda.buildlogic.configureAndroid
import io.eksoda.buildlogic.configureQuality
import io.eksoda.buildlogic.libs
import io.eksoda.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroid(this)
                testOptions.targetSdk = Sdk.TARGET
                lint.targetSdk = Sdk.TARGET
                // Each module gets its own namespace from its path: :core:ui → io.eksoda.core.ui.
                namespace = "io.eksoda" + path.replace(':', '.')
            }
            configureQuality()
            dependencies {
                "testImplementation"(libs.library("junit4"))
                "testImplementation"(libs.library("assertk"))
                "testImplementation"(libs.library("robolectric"))
                "testImplementation"(libs.library("androidx-test-core"))
                "testImplementation"(libs.library("kotlinx-coroutines-test"))
            }
        }
    }
}
