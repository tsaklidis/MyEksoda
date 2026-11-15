import com.android.build.api.dsl.CommonExtension
import io.eksoda.buildlogic.libs
import io.eksoda.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Compose for an Android module. Apply after eksoda.android.library or .application. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.getByType<CommonExtension>().buildFeatures.compose = true
            dependencies {
                val bom = platform(libs.library("androidx-compose-bom"))
                "implementation"(bom)
                "implementation"(libs.library("androidx-compose-ui"))
                "implementation"(libs.library("androidx-compose-ui-graphics"))
                "implementation"(libs.library("androidx-compose-ui-tooling-preview"))
                "implementation"(libs.library("androidx-compose-material3"))
                "debugImplementation"(libs.library("androidx-compose-ui-tooling"))
                "debugImplementation"(libs.library("androidx-compose-ui-test-manifest"))
                "testImplementation"(bom)
                "testImplementation"(libs.library("androidx-compose-ui-test-junit4"))
                "androidTestImplementation"(bom)
                "androidTestImplementation"(libs.library("androidx-compose-ui-test-junit4"))
            }
        }
    }
}
