plugins {
    alias(libs.plugins.eksoda.jvm.library)
    alias(libs.plugins.kover)
}

dependencies {
    // The canonical receipt JSON (CanonicalJson) and, later, test vectors and backups (§3).
    api(libs.kotlinx.serialization.json)
    // Parsers take Jsoup documents (the Layout contract); the fetcher is the app's only HTTP client (§8.1).
    api(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    // Parser tests read this repository's receipt fixtures, testdata/fixtures/<provider>/ (§10).
    systemProperty("eksoda.testdata", rootDir.resolve("testdata").absolutePath)
    inputs.dir(rootDir.resolve("testdata")).withPathSensitivity(PathSensitivity.RELATIVE)
}

kover {
    reports {
        verify {
            rule("core:engine line coverage (docs/android-standalone.md §10.3)") {
                minBound(90)
            }
        }
    }
}
