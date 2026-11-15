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
    // Parity tests read the server's fixtures and vectors from the synced copy (§10.1).
    systemProperty("eksoda.testdata", rootDir.resolve("testdata/server").absolutePath)
    inputs.dir(rootDir.resolve("testdata/server")).withPathSensitivity(PathSensitivity.RELATIVE)
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
