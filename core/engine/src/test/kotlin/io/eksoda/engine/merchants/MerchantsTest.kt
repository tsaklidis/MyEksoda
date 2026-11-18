package io.eksoda.engine.merchants

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.eksoda.engine.canonical.Address
import io.eksoda.engine.text.normalizeText
import org.junit.jupiter.api.Test
import java.time.Instant

class MerchantsTest {
    @Test
    fun `values are cut to the field size and stripped`() {
        assertThat(fit("  abc  ", 10)).isEqualTo("abc")
        assertThat(fit("abcdef", 3)).isEqualTo("abc")
    }

    @Test
    fun `the display name is the brand part of the legal name`() {
        assertThat(deriveDisplayName("METRO ΑΕΒΕ ΧΟΝΔΡΙΚΟ ΕΜΠΟΡΙΟ")).isEqualTo("Metro")
        assertThat(deriveDisplayName("ΣΚΛΑΒΕΝΙΤΗΣ - Α.Ε.")).isEqualTo("Σκλαβενιτης")
        assertThat(deriveDisplayName("ΛΙΝΤΛ ΕΛΛΑΣ ΚΑΙ ΣΙΑ Ο.Ε.")).isEqualTo("Λιντλ Ελλας")
        assertThat(deriveDisplayName("ΑΦΟΙ ΠΑΠΑΔΟΠΟΥΛΟΥ & ΣΙΑ")).isEqualTo("Αφοι Παπαδοπουλου")
        assertThat(deriveDisplayName("ΚΑΦΕ ΚΑΙ ΤΣΑΙ")).isEqualTo("Καφε Και Τσαι")
        // The first word is always kept.
        assertThat(deriveDisplayName("ΑΕ")).isEqualTo("Αε")
        assertThat(deriveDisplayName("  ")).isEqualTo("")
        assertThat(deriveDisplayName("Χ".repeat(200)).length).isEqualTo(FieldSize.DISPLAY_NAME)
    }

    @Test
    fun `the store key joins the address parts it has`() {
        assertThat(normalizeAddress(null)).isEqualTo("")
        val full = Address(street = "Ερμού", number = "5", postalCode = "10563", city = "Αθήνα")
        assertThat(normalizeAddress(full)).isEqualTo(normalizeText("Ερμού 5 10563 Αθήνα"))
        assertThat(normalizeAddress(Address(street = "", city = "Κοζάνη")))
            .isEqualTo(normalizeText("Κοζάνη"))
    }

    private val t1: Instant = Instant.parse("2024-05-01T10:00:00Z")
    private val current = mapOf(
        "city" to "",
        "street" to "ΕΡΜΟΥ",
        "legal_name" to "METRO ΑΝΩΝΥΜΗ ΕΜΠΟΡΙΚΗ ΕΤΑΙΡΕΙΑ",
        "tax_office" to "ΚΟΖΑΝΗΣ",
    )
    private val details = mapOf(
        "city" to "ΚΟΖΑΝΗ",
        "street" to "ΣΤΑΔΙΟΥ",
        "legal_name" to "METRO ΑΝΩΝΥΜΗ",
        "tax_office" to "",
    )

    @Test
    fun `a newer receipt fills and replaces details, never with a truncated copy`() {
        val (changed, move) = refreshedDetails(current, t1, details, t1.plusSeconds(60))
        assertThat(changed).isEqualTo(mapOf("city" to "ΚΟΖΑΝΗ", "street" to "ΣΤΑΔΙΟΥ"))
        assertThat(move).isTrue()
    }

    @Test
    fun `an older receipt only fills empty details`() {
        val (changed, move) = refreshedDetails(current, t1, details, t1.minusSeconds(60))
        assertThat(changed).containsOnly("city" to "ΚΟΖΑΝΗ")
        assertThat(move).isFalse()
    }

    @Test
    fun `the same time replaces but does not move, and no time yet always moves`() {
        assertThat(refreshedDetails(current, t1, details, t1).second).isFalse()
        assertThat(refreshedDetails(current, null, details, t1).second).isTrue()
        assertThat(refreshedDetails(details, t1, details, t1.plusSeconds(1)).first).isEmpty()
    }
}
