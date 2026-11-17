import androidx.room.gradle.RoomExtension
import io.eksoda.buildlogic.libs
import io.eksoda.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room with exported schemas in `<module>/schemas`. */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("androidx.room")
            pluginManager.apply("com.google.devtools.ksp")
            extensions.configure<RoomExtension> {
                schemaDirectory("$projectDir/schemas")
            }
            dependencies {
                "implementation"(libs.library("room-runtime"))
                "implementation"(libs.library("room-ktx"))
                "ksp"(libs.library("room-compiler"))
                "testImplementation"(libs.library("room-testing"))
            }
        }
    }
}
