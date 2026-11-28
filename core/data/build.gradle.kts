import io.eksoda.buildlogic.useTestData
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.eksoda.android.library)
    alias(libs.plugins.eksoda.hilt)
    alias(libs.plugins.kover)
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.paging.common)
    api(libs.kotlinx.coroutines.core)
    implementation(project(":core:database"))
    implementation(project(":core:engine"))
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.room.ktx)
    // Backup manifest and rows (backup/), built as JSON trees without the compiler plugin.
    implementation(libs.kotlinx.serialization.json)
    // Runs ImportWorker in the import pipeline tests.
    testImplementation(libs.androidx.work.testing)
    // Reads the paged lists (receipts, review) in the repository tests.
    testImplementation(libs.androidx.paging.testing)
    // Follows the repositories' flows as a job moves (ImportLifecycleTest).
    testImplementation(libs.turbine)
}

tasks.withType<Test>().configureEach {
    // The import pipeline tests serve this repository's receipt fixtures, testdata/fixtures/.
    useTestData(rootDir.resolve("testdata"))
}

kover {
    reports {
        filters {
            excludes {
                // Hilt modules and the code Hilt and Dagger generate.
                packages("io.eksoda.core.data.di", "dagger.hilt.internal", "hilt_aggregated_deps")
                classes("*_Factory", "*_HiltModules*", "*_MembersInjector", "Hilt_*")
            }
        }
        verify {
            // Floors, not targets: the tests are at 97.8% of lines and 79.5% of branches
            // (2026-11-27). The room below is for code only a device runs (Keystore, NSD,
            // sockets). Raise them as tests are added, never lower them to let a change
            // through; a drop on a pull request is also Codecov's check (codecov.yml).
            // `make check` verifies the debug variant.
            rule("core:data line coverage") {
                minBound(95)
            }
            rule("core:data branch coverage") {
                minBound(79, CoverageUnit.BRANCH)
            }
        }
    }
}
