package io.eksoda.engine.products

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.eksoda.engine.mydata.Unit
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MatchingTest {
    private fun size(description: String): Pair<String, String>? =
        suggest(description)?.let { it.quantity.stripTrailingZeros().toPlainString() to it.unit }

    @Test
    fun `one unambiguous size token is a package size`() {
        assertThat(size("ΓΑΛΑ ΦΡΕΣΚΟ 1L")).isEqualTo("1" to "l")
        assertThat(size("ΜΑΚΑΡΟΝΙΑ 500GR")).isEqualTo("500" to "g")
        assertThat(size("ΤΥΡΙ 1,5ΚΙΛΟ")).isEqualTo("1.5" to "kg")
        assertThat(size("ΚΡΑΣΙ 75CL")).isEqualTo("75" to "cl")
        assertThat(size("ΣΑΜΠΟΥΑΝ 400ML")).isEqualTo("400" to "ml")
        // Greek look-alike letters count as the unit.
        assertThat(size("ΑΛΕΥΡΙ 1ΚG")).isEqualTo("1" to "kg")
        // mg becomes g.
        assertThat(size("ΒΙΤΑΜΙΝΗ C 500MG")).isEqualTo("0.5" to "g")
        // A percentage is not a size.
        assertThat(size("ΓΙΑΟΥΡΤΙ 2% 1KG")).isEqualTo("1" to "kg")
    }

    @Test
    fun `packs, several sizes, a zero size or none give no suggestion`() {
        assertThat(size("ΝΕΡΟ 6X500ML")).isNull()
        assertThat(size("ΜΠΥΡΑ 27/250ML")).isNull()
        assertThat(size("ΣΟΚΟΛΑΤΑ 100G 200G")).isNull()
        assertThat(size("ΤΣΑΝΤΑ 0G")).isNull()
        assertThat(size("ΨΩΜΙ")).isNull()
    }

    @Test
    fun `the mapping key is the item code, else the description`() {
        assertThat(mappingKey("12345", "ΓΑΛΑ")).isEqualTo("code:12345")
        assertThat(mappingKey(null, "ΓΑΛΑ")).isEqualTo("desc:ΓΑΛΑ")
        assertThat(mappingKey("", "ΓΑΛΑ")).isEqualTo("desc:ΓΑΛΑ")
    }

    @Test
    fun `a code reused for a different item is caught`() {
        assertThat(isReusedCode("ΓΑΛΑ ΦΡΕΣΚΟ 1L", "ΓΑΛΑ ΦΡΕΣΚΟ 1L")).isFalse()
        assertThat(isReusedCode("ΓΑΛΑ ΦΡ 1L", "ΓΑΛΑ ΦΡΕΣΚΟ 1L")).isFalse()
        assertThat(isReusedCode("ΓΑΛΑ", "ΑΠΟΡΡΥΠΑΝΤΙΚΟ")).isTrue()
    }

    @Test
    fun `a new product is named after the line, with a size for piece lines only`() {
        val piece = newProduct("  ΜΑΚΑΡΟΝΙΑ 500GR ", "1", Unit.PIECE)
        assertThat(piece.name).isEqualTo("ΜΑΚΑΡΟΝΙΑ 500GR")
        assertThat(piece.packageQuantity?.compareTo(BigDecimal("500"))).isEqualTo(0)
        assertThat(piece.packageUnit).isEqualTo("g")
        val weighed = newProduct("ΤΥΡΙ 500GR", null, Unit.KG)
        assertThat(weighed.packageQuantity).isNull()
        assertThat(weighed.packageUnit).isNull()
        assertThat(newProduct("   ", "42", null).name).isEqualTo("Item 42")
        assertThat(newProduct("", null, null).name).isEqualTo("Item ?")
        assertThat(newProduct("Χ".repeat(300), null, null).name.length).isEqualTo(255)
    }

    @Test
    fun `category slugs are lowercase words joined by hyphens, Greek kept`() {
        assertThat(categorySlug("Φρούτα & Λαχανικά")).isEqualTo("φρούτα-λαχανικά")
        assertThat(categorySlug("  Hello   World ")).isEqualTo("hello-world")
        assertThat(categorySlug("!!!")).isEqualTo("category")
        assertThat(categorySlug("a".repeat(100)).length).isEqualTo(80)
    }
}
