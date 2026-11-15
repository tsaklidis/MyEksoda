plugins {
    alias(libs.plugins.eksoda.jvm.library)
    alias(libs.plugins.kover)
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
