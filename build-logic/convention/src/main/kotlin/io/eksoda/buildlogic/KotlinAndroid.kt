package io.eksoda.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * SDK levels. compileSdk is 37 because the current AndroidX releases require it; the target
 * stays 36, so runtime behavior is unchanged.
 */
internal object Sdk {
    const val COMPILE = 37
    const val TARGET = 36
    const val MIN = 26
}

/**
 * Settings every Android module shares. The build runs on Android Studio's JBR (JDK 21) and
 * targets Java 17 bytecode, so no separate JDK 17 toolchain has to be installed.
 */
internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = Sdk.COMPILE
        defaultConfig.minSdk = Sdk.MIN
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        testOptions.unitTests.isIncludeAndroidResources = true
        lint.abortOnError = true
        lint.checkReleaseBuilds = false
        lint.lintConfig = rootProject.file("config/lint/lint.xml")
    }
    configureKotlinJvmTarget()
    // Most feature modules have no tests until their milestone; an empty test task is not an error.
    tasks.withType<Test>().configureEach { failOnNoDiscoveredTests.set(false) }
}

internal fun Project.configureKotlinJvmTarget() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }
}
