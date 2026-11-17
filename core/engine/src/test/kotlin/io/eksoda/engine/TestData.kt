package io.eksoda.engine

import java.io.File

/** This repository's test data: receipt fixtures in testdata/fixtures/<provider>/ (§10). */
internal object TestData {
    val root: File by lazy {
        val path = checkNotNull(System.getProperty("eksoda.testdata")) {
            "System property eksoda.testdata is not set. Run the tests through Gradle."
        }
        File(path)
    }

    val fixtures: File get() = root.resolve("fixtures")
}
