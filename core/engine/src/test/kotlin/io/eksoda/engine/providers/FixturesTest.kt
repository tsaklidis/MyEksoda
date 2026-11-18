package io.eksoda.engine.providers

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.eksoda.engine.ReceiptException
import io.eksoda.engine.TestData
import io.eksoda.engine.validation.validate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * Every fixture in testdata/fixtures/<provider>/: the page parses to its golden JSON with the
 * warnings its meta file expects, or fails with the error it expects, and it is already sanitized.
 * A changed golden file is written to build/golden/ for review.
 */
class FixturesTest {
    @TestFactory
    fun fixtures(): List<DynamicTest> = TestData.fixtures
        .listFiles { file -> file.isDirectory }.orEmpty()
        .sortedBy { it.name }
        .flatMap { directory ->
            directory.listFiles { file -> file.name.endsWith(META) }.orEmpty()
                .sortedBy { it.name }
                .map { meta -> fixtureTest(directory, meta) }
        }

    private fun fixtureTest(directory: File, metaFile: File): DynamicTest {
        val name = metaFile.name.removeSuffix(META)
        return DynamicTest.dynamicTest("${directory.name}/$name") {
            val page = PAGE_EXTENSIONS.map { directory.resolve("$name.$it") }.first { it.exists() }
            val text = page.readText()
            val meta = Json.parseToJsonElement(metaFile.readText()).jsonObject
            val (provider, identity) =
                REGISTRY.resolveUrl(meta.getValue("url").jsonPrimitive.content)
            assertThat(provider.code).isEqualTo(directory.name)
            val expect = meta.getValue("expect").jsonObject

            val error = expect["error"]?.jsonPrimitive?.content
            if (error != null) {
                assertFailure { provider.parseHtml(text, identity) }
                    .isInstanceOf<ReceiptException>()
                    .prop(ReceiptException::code)
                    .transform { it.code }
                    .isEqualTo(error)
            } else {
                val receipt = provider.parseHtml(text, identity)
                assertGolden(directory, name, receipt.toJson())
                val warnings =
                    expect.getValue("warnings").jsonArray.map { it.jsonPrimitive.content }
                assertThat(validate(receipt).map { it.code }).isEqualTo(warnings)
            }
            assertThat(provider.sanitize(text).replaced, name = "left to sanitize").isEmpty()
        }
    }

    private fun assertGolden(directory: File, name: String, actual: JsonObject) {
        val goldenFile = directory.resolve("expected/$name.json")
        assertThat(goldenFile.exists(), name = "${goldenFile.path} exists").isEqualTo(true)
        val expected = Json.parseToJsonElement(goldenFile.readText())
        if (expected != actual) {
            // Written for review; copy it to testdata/ when the change is intended.
            val written = File("build/golden/${directory.name}/$name.json")
            written.parentFile.mkdirs()
            val text = pretty.encodeToString(JsonElement.serializer(), sorted(actual))
            written.writeText("$text\n")
            assertThat(actual, name = "parsed receipt (actual in ${written.path})")
                .isNotNull()
                .isEqualTo(expected)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private val pretty = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    private fun sorted(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { (_, value) -> sorted(value) })
        is JsonArray -> JsonArray(element.map(::sorted))
        else -> element
    }

    private companion object {
        const val META = ".meta.json"
        val PAGE_EXTENSIONS = listOf("html", "xml")
    }
}
