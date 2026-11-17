package io.eksoda.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

/**
 * Fails the build when the app's runtime classpath contains a library that sends data off the
 * phone, or an HTTP client library the app does not use.
 */
@CacheableTask
internal abstract class NoTelemetryCheckTask : DefaultTask() {
    /** `group:name:version` of every external module on the checked classpaths. */
    @get:Input
    abstract val modules: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val all = modules.get().distinct().sorted()
        val banned = all.filter(::isBanned)
        report.get().asFile.writeText(
            buildString {
                appendLine("Checked ${all.size} modules.")
                banned.forEach { appendLine("BANNED: $it") }
            },
        )
        if (banned.isNotEmpty()) {
            throw GradleException(
                "The app's runtime classpath contains libraries that break the no-telemetry " +
                    "promise:\n" +
                    banned.joinToString("\n") { "  - $it" },
            )
        }
    }
}

internal fun isBanned(module: String): Boolean {
    val group = module.substringBefore(':')
    val name = module.substringAfter(':').substringBefore(':')
    return BANNED_GROUP_PREFIXES.any { group == it || group.startsWith("$it.") } ||
        "$group:$name" in BANNED_MODULES
}

private val BANNED_GROUP_PREFIXES = listOf(
    // Google services that report usage: Firebase, ML Kit, Play Services, their transport.
    "com.google.firebase",
    "com.google.mlkit",
    "com.google.android.gms",
    "com.google.android.datatransport",
    // Crash reporting and analytics SDKs.
    "com.crashlytics",
    "io.fabric",
    "io.sentry",
    "com.bugsnag",
    "com.instabug",
    "com.newrelic",
    "com.datadoghq",
    "com.amplitude",
    "com.mixpanel",
    "com.segment",
    "com.segment.analytics",
    "io.branch",
    "com.appsflyer",
    "com.adjust.sdk",
    "com.facebook.android",
    // The app has no API client: no Retrofit.
    "com.squareup.retrofit2",
)

private val BANNED_MODULES = setOf(
    "com.squareup.okhttp3:logging-interceptor",
)

internal fun Project.registerNoTelemetryCheck() {
    val checked = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
    tasks.register<NoTelemetryCheckTask>("checkNoTelemetry") {
        group = "verification"
        description = "Fails if a telemetry, analytics or crash SDK is on the app's classpath."
        checked.forEach { configurationName ->
            modules.addAll(
                configurations.named(configurationName).flatMap { configuration ->
                    configuration.incoming.resolutionResult.rootComponent.map(::externalModules)
                },
            )
        }
        report.set(layout.buildDirectory.file("reports/no-telemetry.txt"))
    }
}

private fun externalModules(root: ResolvedComponentResult): List<String> {
    val seen = mutableSetOf<ResolvedComponentResult>()
    val queue = ArrayDeque(listOf(root))
    val modules = mutableListOf<String>()
    while (queue.isNotEmpty()) {
        val component = queue.removeFirst()
        if (!seen.add(component)) continue
        (component.id as? ModuleComponentIdentifier)?.let {
            modules += "${it.group}:${it.module}:${it.version}"
        }
        component.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .forEach { queue += it.selected }
    }
    return modules
}
