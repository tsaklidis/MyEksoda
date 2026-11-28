import io.eksoda.buildlogic.libs
import io.eksoda.buildlogic.library
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature module. Features see only core:data's interfaces, core:model, core:ui and
 * core:designsystem.
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
                // MainDispatcherRule and the fake repositories the ViewModel tests use.
                "testImplementation"(project(":core:testing"))
            }
            configureViewModelCoverage()
        }
    }

    /**
     * Kover over the module's ViewModels: the screen logic the unit tests reach. Composables are
     * left out (their behavior is the ViewModel's state; their look is checked by eye and
     * previews), as is the code Hilt generates. `make check` verifies the floors.
     */
    private fun Project.configureViewModelCoverage() {
        pluginManager.apply("org.jetbrains.kotlinx.kover")
        extensions.configure<KoverProjectExtension> {
            reports {
                filters {
                    includes { classes("*ViewModel", "*ViewModel$*") }
                    excludes {
                        classes("*_Factory", "*_HiltModules*", "*_MembersInjector", "Hilt_*")
                    }
                }
                verify {
                    // Floors, not targets: the lowest feature is at 94.6% of lines (scan) and
                    // 81.0% of branches (insights) (2026-11-28). Raise them as tests are added,
                    // never lower them to let a change through. A new ViewModel ships with its
                    // test, or its feature fails here.
                    rule("ViewModel line coverage") { minBound(VIEW_MODEL_LINES) }
                    rule("ViewModel branch coverage") {
                        minBound(VIEW_MODEL_BRANCHES, CoverageUnit.BRANCH)
                    }
                }
            }
        }
    }

    private companion object {
        const val VIEW_MODEL_LINES = 94
        const val VIEW_MODEL_BRANCHES = 80
    }
}
