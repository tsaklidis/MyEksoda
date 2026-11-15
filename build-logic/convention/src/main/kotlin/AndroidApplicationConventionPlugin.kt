import com.android.build.api.dsl.ApplicationExtension
import io.eksoda.buildlogic.Sdk
import io.eksoda.buildlogic.configureAndroid
import io.eksoda.buildlogic.configureQuality
import io.eksoda.buildlogic.registerNoTelemetryCheck
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                defaultConfig.targetSdk = Sdk.TARGET
            }
            configureQuality()
            registerNoTelemetryCheck()
        }
    }
}
