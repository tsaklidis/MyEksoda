import io.eksoda.buildlogic.useTestData
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    alias(libs.plugins.eksoda.jvm.library)
    alias(libs.plugins.kover)
}

dependencies {
    // The canonical receipt JSON (CanonicalJson).
    api(libs.kotlinx.serialization.json)
    // Parsers take Jsoup documents (the Layout contract); the fetcher is the app's only HTTP client.
    api(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    // Parser tests read this repository's receipt fixtures, testdata/fixtures/<provider>/.
    useTestData(rootDir.resolve("testdata"))
}

kover {
    reports {
        verify {
            // Floors, not targets: the tests are at 99.2% of lines and 87.4% of branches
            // (2026-11-27). Raise them as tests are added, never lower them to let a change
            // through; a drop on a pull request is also Codecov's check (codecov.yml).
            rule("core:engine line coverage") {
                minBound(97)
            }
            // Branches are the cases a parser or rule handles: a line can run while one of its
            // `?:`, `if` or `when` arms never does.
            rule("core:engine branch coverage") {
                minBound(87, CoverageUnit.BRANCH)
            }
        }
    }
}
