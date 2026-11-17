package io.eksoda.engine.products

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Line descriptions from the synced receipt fixtures and common receipt names. */
class AutoCategoryTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "ΚΟΖΑΝΗΣ ΦΡΕΣΚΟ ΓΑΛΑ ΠΛΗΡΕΣ 1L, dairy-and-eggs",
        "ΣΥΜΠΥΚ. ΓΑΛΑ 4% 410G, dairy-and-eggs",
        "ΦΕΤΑ ΠΟΠ 400G, dairy-and-eggs",
        "ΓΑΛΑ ΣΟΚΟΛΑΤΑ 500ML, dairy-and-eggs",
        "ΣΟΚΟΛΑΤΑ ΓΑΛΑΚΤΟΣ 100G, snacks-and-sweets",
        "BIO ΣΟΚΟΛ.ΥΓΕΙΑΣ100G, snacks-and-sweets",
        "ΤΣΙΠΣ ΠΑΠΡΙΚΑ 150G, snacks-and-sweets",
        "ΝΙΚΑΣ ΣΑΛΑΜΙ ΑΕΡΟΣ ΠΙΚ.72 ΕΓΧΩΡΙΟ (Τ.Κ.), meat-and-fish",
        "ΦΙΛΕΤΟ ΚΟΤΟΠΟΥΛΟ, meat-and-fish",
        "GALIA ΠΕΠΟΝΙΑ, fruits-and-vegetables",
        "ΚΑΡΠΟΥΖΙ, fruits-and-vegetables",
        "ΤΟΜΑΤΕΣ ΕΓΧΩΡΙΕΣ, fruits-and-vegetables",
        "ΠΑΤΑΤΑΚΙΑ ΑΛΑΤΙ, snacks-and-sweets",
        "ΤΣΟΥΡΕΚΙ 490G, bakery",
        "ΜΠΑΓΚΕΤΑ Μ.ΜΥΡΩΔ175G, bakery",
        "CO.COLA ZE.5+1X330ML, beverages",
        "MY GUSTO ΧΥΜΟΣ MULTIV. 9 ΦΡΟΥΤΑ 27/250ML, beverages",
        "ΕΛΛ.ΚΑΦ.ΚΛΑΣ.340G, beverages",
        "ΠΟΡΤΟΚΑΛΑΔΑ 1.5L, beverages",
        "ΡΥΖΙ BASMATI 2KG, groceries",
        "ΖΥΜΑΡ.ΛΙΓΚΟΥΙΝΙ 500G, groceries",
        "ΜΑΓION.LIG.27% 500ML, groceries",
        "FAIRY REGULAR 900ml, cleaning-products",
        "ΠΑΧ.ΧΛΩΡ.FRES.1250ML, cleaning-products",
        "ΣΠΟΓΓΟΠΕΤΣΕΤΕΣ 5ΤΕΜ, cleaning-products",
        "B.ΠΑΝΕΣ BΡΑΚ.N7 28T, personal-care",
        "ΟΔΟΝΤΟΓΛΥΦΙΔΕΣ400ΤΕΜ, personal-care",
        "XAΡ.ΚΟΥΖ.2ΠΛΑ ΦΥΛ1ΚG, household-items",
        "ΣΑΚΟΥΛ.ΒΙΟΔ.25Μ1ΤΕΜ, household-items",
        "ΣΛΙΠ ΣΤΡΙΝΓ.-0521942, clothing-and-footwear",
        "ΑΜΟΛΥΒΔΗ 95, fuel-and-transport",
        "ΠΑΓΩΤΟ ΒΑΝΙΛΙΑ, frozen-food",
        "ΜΕΒΓΑΛ ΚΕΦΙΡ 500ML, dairy-and-eggs",
        "ZAGORI 1.5L, beverages",
        "ΙΟΝ ΑΜΥΓΔΑΛΟΥ 100G, snacks-and-sweets",
        "KLINEX ULTRA 750ML, cleaning-products",
        "ΝΙΚΑΣ ΠΑΡΙΖΑ ΓΑΛΟΠΟΥΛΑ, meat-and-fish",
        "ΜΙΣΚΟ ΚΟΥΦΟΤΑ 500G, groceries",
    )
    fun `suggests a category from the name`(name: String, slug: String) {
        assertThat(suggestCategorySlug(name)).isEqualTo(slug)
    }

    @ParameterizedTest
    @CsvSource("MSC ΦΙ.ΤΟ.ΦΥ.Ζ.195G", "ΔΩΡΟΚΑΡΤΑ")
    fun `returns null when nothing matches`(name: String) {
        assertThat(suggestCategorySlug(name)).isNull()
    }

    @ParameterizedTest(name = "{0} / {1} -> {2}")
    @CsvSource(
        "'ΛΙΑΝΙΚΟ ΕΜΠΟΡΙΟ ΚΑΥΣΙΜΩΝ ΚΙΝΗΣΗΣ ΣΕ ΕΞΕΙΔΙΚΕΥΜΕΝΑ ΚΑΤΑΣΤΗΜΑΤΑ', 'ΠΑΠΑΔΟΠΟΥΛΟΣ ΑΕ', fuel-and-transport",
        "'ΔΡΑΣΤΗΡΙΟΤΗΤΕΣ ΠΑΡΟΧΗΣ ΠΟΤΩΝ', 'ΚΑΦΕ ΑΛΦΑ', restaurants-and-cafes",
        "'ΥΠΗΡΕΣΙΕΣ ΕΣΤΙΑΤΟΡΙΟΥ ΜΕ ΠΛΗΡΗ ΕΞΥΠΗΡΕΤΗΣΗ', 'ΤΑΒΕΡΝΑ Ο ΜΠΑΜΠΑΣ', restaurants-and-cafes",
        "'ΛΙΑΝΙΚΟ ΕΜΠΟΡΙΟ ΦΑΡΜΑΚΕΥΤΙΚΩΝ ΠΡΟΪΟΝΤΩΝ', 'ΓΕΩΡΓΙΟΥ ΜΑΡΙΑ', pharmacy-and-health",
        "'ΛΙΑΝΙΚΟ ΕΜΠΟΡΙΟ ΨΩΜΙΟΥ, ΚΕΙΚ, ΓΛΥΚΙΣΜΑΤΩΝ ΚΑΙ ΖΑΧΑΡΩΤΩΝ', 'ΦΟΥΡΝΟΣ ΝΙΚΟΥ', bakery",
        "'ΛΙΑΝΙΚΟ ΕΜΠΟΡΙΟ ΗΛΕΚΤΡΙΚΩΝ ΟΙΚΙΑΚΩΝ ΣΥΣΚΕΥΩΝ', 'ΚΩΤΣΟΒΟΛΟΣ ΑΕ', electronics-and-appliances",
        "'ΠΑΡΑΓΩΓΗ ΚΑΙ ΕΜΠΟΡΙΑ ΗΛΕΚΤΡΙΚΗΣ ΕΝΕΡΓΕΙΑΣ', 'ΔΕΗ ΑΕ', services",
        "'', 'SHELL HELLAS', fuel-and-transport",
    )
    fun `a merchant's activity or name gives its kind`(
        activity: String?,
        name: String,
        slug: String,
    ) {
        assertThat(merchantCategorySlug(activity.orEmpty(), name)).isEqualTo(slug)
    }

    @ParameterizedTest
    @CsvSource(
        "'ΕΜΠΟΡΙΟ ΣΕ ΜΗ ΕΞΕΙΔΙΚΕΥΜΕΝΑ ΚΑΤΑΣΤΗΜΑΤΑ, ΤΡΟΦΙΜΑ, ΠΟΤΑ', 'ΑΒ ΒΑΣΙΛΟΠΟΥΛΟΣ ΑΕ'",
        "'ΛΙΑΝΙΚΟ ΕΜΠΟΡΙΟ ΣΕ ΜΗ ΕΞΕΙΔΙΚΕΥΜΕΝΑ ΚΑΤΑΣΤΗΜΑΤΑ', 'SHELL CAFE'",
        "'', 'ΚΑΡΑΜΑΝΛΗΣ ΟΕ'",
    )
    fun `general stores and unknown merchants have no kind`(activity: String?, name: String) {
        assertThat(merchantCategorySlug(activity.orEmpty(), name)).isNull()
    }

    @ParameterizedTest
    @CsvSource(
        "FREDDO ESPRESSO, restaurants-and-cafes, restaurants-and-cafes",
        "ΜΠΙΡΑ ΑΛΦΑ 500ML, restaurants-and-cafes, restaurants-and-cafes",
        "COCA COLA 330ML, fuel-and-transport, beverages",
        "ΔΩΡΟΚΑΡΤΑ, pharmacy-and-health,",
    )
    fun `eating out wins, elsewhere the name decides`(
        name: String,
        merchant: String,
        slug: String?,
    ) {
        assertThat(productCategorySlug(name, merchant)).isEqualTo(slug)
    }
}
