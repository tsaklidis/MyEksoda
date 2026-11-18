import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.eksoda.android.application)
    alias(libs.plugins.eksoda.android.compose)
    alias(libs.plugins.eksoda.hilt)
    alias(libs.plugins.kotlin.serialization)
}

/** A properties file, or null when it does not exist (read through Gradle, so it is cached). */
fun loadProperties(file: File): Properties? =
    providers.fileContents(layout.projectDirectory.file(file.absolutePath)).asText.orNull
        ?.let { text -> Properties().apply { load(text.reader()) } }

/** The app version, which `make new_release` bumps. */
val appVersion: Properties =
    checkNotNull(loadProperties(rootProject.file("version.properties"))) {
        "version.properties is missing"
    }

/**
 * The release signing key, kept outside the repository (`make release_key` creates it):
 * ~/.eksoda/keystore.properties, or the file named by the `eksoda.keystore` Gradle property.
 */
val releaseKey: Properties? = loadProperties(
    providers.gradleProperty("eksoda.keystore").orNull?.let(::File)
        ?: File(providers.systemProperty("user.home").get(), ".eksoda/keystore.properties"),
)

android {
    namespace = "io.eksoda"

    defaultConfig {
        applicationId = "io.eksoda"
        versionCode = appVersion.getProperty("versionCode").toInt()
        versionName = appVersion.getProperty("versionName")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        releaseKey?.let { key ->
            create("release") {
                storeFile = File(key.getProperty("storeFile"))
                storePassword = key.getProperty("storePassword")
                keyAlias = key.getProperty("keyAlias")
                keyPassword = key.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // R8 and reproducible builds are not set up yet.
            isMinifyEnabled = false
            // The release key when this machine has it. Without it, the debug key, for local
            // installs only: `make new_release` refuses to publish such an APK.
            signingConfig =
                signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":feature:home"))
    implementation(project(":feature:insights"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:products"))
    implementation(project(":feature:receipts"))
    implementation(project(":feature:scan"))
    implementation(project(":feature:settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)

    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
