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
    systemProperty("eksoda.testdata", rootDir.resolve("testdata").absolutePath)
    inputs.dir(rootDir.resolve("testdata")).withPathSensitivity(PathSensitivity.RELATIVE)
}

kover {
    reports {
        verify {
            rule("core:engine line coverage") {
                minBound(90)
            }
        }
    }
}
