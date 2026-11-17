package io.eksoda.engine

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

/**
 * Checks that the receipt fixtures are where the parser tests read them. Each receipt source has
 * pages with meta files and golden output.
 */
class TestDataTest {
    @Test
    fun `every receipt source has fixtures and golden files`() {
        val providers = TestData.fixtures.listFiles { file -> file.isDirectory }.orEmpty()
        assertThat(
            providers.map {
                it.name
            },
        ).containsExactlyInAnyOrder("impact", "entersoft", "aade_fhm", "epsilon")
        providers.forEach { provider ->
            val metaFiles = provider.listFiles { file ->
                file.name.endsWith(".meta.json")
            }.orEmpty()
            assertThat(metaFiles.toList(), name = "${provider.name} meta files").isNotEmpty()
            assertThat(
                provider.resolve("expected").isDirectory,
                name = "${provider.name}/expected",
            ).isTrue()
        }
    }
}
