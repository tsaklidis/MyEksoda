import io.eksoda.buildlogic.libs
import io.eksoda.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module. Features see only core:data's interfaces, core:model, core:ui and
 * core:designsystem (docs/android-standalone.md §4.1).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("eksoda.android.library")
            pluginManager.apply("eksoda.android.compose")
            pluginManager.apply("eksoda.hilt")
            dependencies {
                "implementation"(project(":core:data"))
                "implementation"(project(":core:model"))
                "implementation"(project(":core:ui"))
                "implementation"(project(":core:designsystem"))
                "implementation"(libs.library("androidx-lifecycle-runtime-compose"))
                "implementation"(libs.library("androidx-lifecycle-viewmodel-compose"))
                "implementation"(libs.library("androidx-navigation-compose"))
                "implementation"(libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                "implementation"(libs.library("androidx-compose-material-icons-extended"))
                "testImplementation"(libs.library("turbine"))
            }
        }
    }
}
