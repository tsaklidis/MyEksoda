// Android-only (not in the server): a system category for a new product from its name, so
// spending by category is useful without assigning every product by hand. Owner decision
// 2026-11-16; recorded in docs/android-standalone.md §16. The user can always change it.
package io.eksoda.engine.products

import io.eksoda.engine.text.normalizeText
import io.eksoda.engine.text.pySplit

/**
 * Keyword stems per system category slug (`CategorySeed`), as they appear on Greek receipts
 * (capitals, often shortened). A stem matches the start of a word of the product name; stems of
 * three letters or fewer must match the whole word.
 */
private val KEYWORDS: Map<String, List<String>> = mapOf(
    "dairy-and-eggs" to listOf(
        "ΓΑΛΑ", "ΓΑΛ", "ΓΙΑΟΥΡ", "ΓΙΑΟΥΡΤ", "ΤΥΡΙ", "ΤΥΡΑΚΙ", "ΦΕΤΑ", "ΚΑΣΕΡ", "ΓΚΟΥΝΤΑ",
        "GOUDA", "EDAM", "ΕΝΤΑΜ", "ΓΡΑΒΙΕΡ", "ΚΕΦΑΛΟΤΥΡ", "ΚΕΦΑΛΟΓΡΑΒ", "ΜΥΖΗΘΡ", "ΑΝΘΟΤΥΡ",
        "ΜΑΝΟΥΡ", "ΧΑΛΟΥΜ", "ΜΟΤΣΑΡΕΛ", "MOZZAREL", "ΠΑΡΜΕΖΑΝ", "PARMESAN", "ΤΣΕΝΤΑΡ", "CHEDDAR",
        "ΡΙΚΟΤΑ", "ΚΡΕΜΑ ΤΥΡΙ", "ΒΟΥΤΥΡ", "ΑΥΓΑ", "ΑΥΓΟ", "ΚΕΦΙΡ", "ΞΙΝΟΓΑΛ", "ΡΥΖΟΓΑΛ",
        "ΚΡΕΜΑ ΓΑΛ", "ΓΑΛΑΚΤ", "ΣΚΥΡ", "SKYR", "ΑΡΙΑΝ",
    ),
    "meat-and-fish" to listOf(
        "ΚΟΤΟΠ", "ΚΟΤΑ", "ΣΤΗΘΟΣ", "ΜΠΟΥΤΙ", "ΦΤΕΡΟΥΓ", "ΜΟΣΧ", "ΧΟΙΡ", "ΑΡΝΙ", "ΑΡΝΑΚ",
        "ΚΑΤΣΙΚ", "ΚΙΜΑ", "ΚΙΜΑΣ", "ΜΠΡΙΖΟΛ", "ΜΠΙΦΤΕΚ", "ΣΟΥΒΛΑΚ", "ΓΥΡΟΣ", "ΛΟΥΚΑΝ", "ΖΑΜΠΟΝ",
        "ΜΠΕΙΚΟΝ", "BACON", "ΣΑΛΑΜ", "ΠΑΡΙΖΑ", "ΓΑΛΟΠΟΥΛ", "ΠΡΟΣΟΥΤ", "ΣΟΥΤΖΟΥΚ", "ΠΑΣΤΟΥΡΜ",
        "ΨΑΡ", "ΨΑΡΙ", "ΤΣΙΠΟΥΡΑ", "ΛΑΥΡΑΚ", "ΣΟΛΟΜ", "ΤΟΝΟΣ", "ΤΟΝΟ", "ΓΑΡΙΔ", "ΚΑΛΑΜΑΡ",
        "ΧΤΑΠΟΔ", "ΣΟΥΠΙ", "ΜΠΑΚΑΛΙΑΡ", "ΣΑΡΔΕΛ", "ΓΑΥΡ", "ΜΥΔΙ", "ΓΛΩΣΣΑ", "ΠΕΡΚΑ", "ΚΡΕΑΣ",
        "ΚΡΕΑΤ", "ΜΠΙΦΤ", "ΠΑΝΣΕΤ", "ΚΑΠΝΙΣΤ", "ΚΟΝΤΟΣΟΥΒΛ", "ΜΠΙΦΤΕΚΙ",
    ),
    "fruits-and-vegetables" to listOf(
        "ΜΠΑΝΑΝ", "ΜΗΛΑ", "ΜΗΛΟ", "ΠΟΡΤΟΚΑΛ", "ΛΕΜΟΝ", "ΜΑΝΤΑΡΙΝ", "ΑΧΛΑΔ", "ΣΤΑΦΥΛ",
        "ΚΑΡΠΟΥΖ", "ΠΕΠΟΝ", "ΦΡΑΟΥΛ", "ΚΕΡΑΣ", "ΡΟΔΑΚΙΝ", "ΒΕΡΙΚΟΚ", "ΑΚΤΙΝΙΔ", "ΑΒΟΚΑΝΤ",
        "ΑΝΑΝΑ", "ΣΥΚΑ", "ΡΟΔΙ", "ΤΟΜΑΤ", "ΑΓΓΟΥΡ", "ΠΙΠΕΡΙΑ", "ΠΙΠΕΡΙΕΣ", "ΚΡΕΜΜΥΔ", "ΣΚΟΡΔ",
        "ΠΑΤΑΤ", "ΚΑΡΟΤ", "ΜΑΡΟΥΛ", "ΛΑΧΑΝ", "ΚΟΛΟΚΥΘ", "ΜΕΛΙΤΖΑΝ", "ΜΠΡΟΚΟΛ", "ΚΟΥΝΟΥΠΙΔ",
        "ΣΠΑΝΑΚ", "ΜΑΝΙΤΑΡ", "ΜΑΙΝΤΑΝ", "ΑΝΗΘ", "ΡΟΚΑ", "ΣΕΛΙΝ", "ΠΡΑΣ", "ΦΑΣΟΛΑΚ", "ΦΡΟΥΤ",
        "ΛΑΧΑΝΙΚ", "ΒΛΗΤ", "ΧΟΡΤ", "ΜΠΑΜΙ", "ΠΑΝΤΖΑΡ", "ΔΥΟΣΜ", "ΒΑΣΙΛΙΚ", "ΓΚΡΕΙΠΦΡΟΥΤ",
    ),
    "bakery" to listOf(
        "ΨΩΜΙ", "ΨΩΜΑΚ", "ΑΡΤΟΣ", "ΑΡΤΟΠ", "ΚΡΟΥΑΣΑΝ", "CROISSANT", "ΤΥΡΟΠΙΤ", "ΣΠΑΝΑΚΟΠΙΤ",
        "ΚΟΥΛΟΥΡ", "ΜΠΑΓΚΕΤ", "ΦΡΥΓΑΝ", "ΠΑΞΙΜΑΔ", "ΤΣΟΥΡΕΚ", "ΚΕΙΚ", "ΤΟΣΤ", "ΠΙΤΑ", "ΠΙΤΕΣ",
        "ΑΡΑΒΙΚ", "ΚΡΙΤΣΙΝ", "ΜΠΟΥΓΑΤΣ", "ΛΑΓΑΝ", "ΚΑΣΕΡΟΠΙΤ", "ΖΑΜΠΟΝΟΠΙΤ", "ΠΙΤΣΑ", "ΧΩΡΙΑΤΟΨ",
        "ΝΤΟΝΑΤ", "ΤΑΡΤ", "ΒΕΚΑΤΣ", "ΑΡΤ", "ΦΡΑΤΖ", "ΝΤΑΚ", "ΝΤΑΚΟΥΣ",
    ),
    "beverages" to listOf(
        "ΑΝΑΨΥΚΤ", "COLA", "ΚΟΚΑ", "PEPSI", "FANTA", "SPRITE", "SCHWEPPES", "ΝΕΡΟ", "ΝΕΡΑ",
        "ΜΠΥΡ", "ΜΠΥΡΑ", "BEER", "ΚΡΑΣ", "ΟΙΝΟΣ", "ΟΙΝΟ", "WINE", "ΡΕΤΣΙΝ", "ΤΣΙΠΟΥΡΟ", "ΟΥΖΟ",
        "ΒΟΤΚ", "VODKA", "ΟΥΙΣΚ", "WHISK", "ΡΟΥΜΙ", "ΤΖΙΝ", "GIN", "ΤΣΑΙ", "ΚΑΦΕ", "ΚΑΦΕΣ",
        "ESPRESSO", "ΕΣΠΡΕΣΟ", "FREDDO", "ΦΡΕΝΤΟ", "CAPPUCC", "ΚΑΠΟΥΤΣΙΝ", "NESCAFE", "ΦΡΑΠΕ",
        "ΣΟΔΑ", "ΤΟΝΩΤ", "ENERGY", "REDBULL", "MONSTER", "ΧΥΜ", "ΧΥΜΟΣ", "ΧΥΜΟΙ", "ΛΕΜΟΝΑΔ",
        "ΠΟΡΤΟΚΑΛΑΔ", "ΒΥΣΣΙΝΑΔ", "ΓΚΑΖΟΖ", "ΣΑΝΓΚΡΙ", "ΛΙΚΕΡ", "ΣΑΜΠΑΝ", "ΑΦΕΨ", "ΡΟΦΗΜ",
        "ΣΟΚΟΦΡΕΤ", "ΤΣΑΓΙ", "ΜΕΤΑΛΛΙΚ", "ΑΝΘΡΑΚΟΥΧ", "ΚΑΦ",
    ),
    "frozen-food" to listOf(
        "ΚΑΤΕΨ",
        "ΚΑΤΕΨΥΓΜ",
        "ΚΤΨ",
        "ΠΑΓΩΤ",
        "FROZEN",
        "ΠΑΓΟΣ",
        "ΠΑΓΑΚ",
    ),
    "snacks-and-sweets" to listOf(
        "ΣΟΚΟΛ", "ΣΟΚΟΛΑΤ", "ΜΠΙΣΚΟΤ", "7DAYS", "ΓΚΟΦΡΕΤ", "ΚΑΡΑΜΕΛ", "ΤΣΙΧΛ",
        "ΠΑΤΑΤΑΚ", "CHIPS", "ΓΑΡΙΔΑΚ",
        "POPCORN", "ΠΟΠΚΟΡΝ", "ΚΡΑΚΕΡ", "CRACKER", "ΦΥΣΤΙΚ", "ΑΜΥΓΔΑΛ", "ΚΑΣΙΟΥ", "ΚΑΡΥΔ",
        "ΛΟΥΚΟΥΜ", "ΧΑΛΒΑ", "ΜΠΑΡΑ", "ΜΠΑΡΕΣ", "ΖΕΛΕ", "ΠΡΑΛΙΝ", "NUTELLA", "OREO", "ΚΟΥΛΟΥΡΑΚ",
        "ΓΛΥΚΟ", "ΓΛΥΚΑ", "ΓΛΥΚΙΣΜ", "ΚΡΟΥΤΟΝ", "ΣΝΑΚ", "SNACK", "DORITOS", "LAYS", "PRINGLES",
        "ΤΣΙΠΣ", "ΠΑΣΤΑ ΦΛΩΡ", "ΠΑΣΤΕΛ", "ΜΑΡΕΓΚ", "ΤΟΥΡΤ", "ΠΑΣΤΑΚ", "ΚΑΡΠΟΙ", "ΞΗΡΟΙ",
    ),
    "groceries" to listOf(
        "ΖΑΧΑΡ", "ΑΛΕΥΡ", "ΑΛΑΤΙ", "ΡΥΖΙ", "ΜΑΚΑΡΟΝ", "ΣΠΑΓΓΕΤ", "ΠΕΝΕΣ", "ΚΡΙΘΑΡΑΚ", "ΧΥΛΟΠΙΤ",
        "ΦΑΚΕΣ", "ΦΑΣΟΛΙΑ", "ΦΑΣΟΛ", "ΡΕΒΥΘ", "ΛΑΔΙ", "ΕΛΑΙΟΛ", "ΗΛΙΕΛΑΙ", "ΞΥΔΙ", "ΕΛΙΕΣ",
        "ΜΕΛΙ", "ΜΑΡΜΕΛΑΔ", "ΔΗΜΗΤΡΙΑΚ", "CORNFLAKES", "ΒΡΩΜ", "ΜΟΥΣΤΑΡΔ", "ΚΕΤΣΑΠ", "ΜΑΓΙΟΝΕΖ",
        "ΣΑΛΤΣ", "ΚΟΝΣΕΡΒ", "ΜΠΑΧΑΡ", "ΡΙΓΑΝ", "ΚΑΝΕΛ", "ΠΙΠΕΡΙ", "ΜΑΓΙΑ", "ΚΑΚΑΟ", "ΖΩΜ",
        "ΚΥΒΟΙ", "ΤΑΧΙΝ", "ΦΥΤΙΝ", "ΜΑΡΓΑΡΙΝ", "ΝΟΥΝΤΛ", "NOODLE", "ΣΟΥΠΑ", "ΤΟΜΑΤΟΠΟΛΤ",
        "ΠΕΛΤΕ", "ΠΟΥΡΕΣ", "ΚΟΡΝ ΦΛΕΙΚΣ", "ΒΑΝΙΛ", "ΜΠΕΙΚΙΝ", "ΣΙΜΙΓΔΑΛ", "ΤΑΡΑΜΑ", "ΤΡΑΧΑΝ",
        "ΠΑΣΤΑ", "ΛΑΖΑΝ", "ΖΥΜΑΡ", "ΚΑΛΑΜΠΟΚ", "QUAKER", "ΝΙΦΑΔ", "ΜΑΓΙΟΝ", "ΤΟΡΤΕΛΙΝ",
        "ΡΑΒΙΟΛ", "ΜΟΥΣΛΙ", "ΓΡΑΝΟΛ", "ΣΙΡΟΠΙ ΣΦΕΝΔ",
    ),
    "cleaning-products" to listOf(
        "ΑΠΟΡΡΥΠ", "ΑΠΟΡΡ", "ΜΑΛΑΚΤ", "ΧΛΩΡ", "ΣΠΟΓΓ", "ΚΑΘΑΡΙΣΤ", "ΠΙΑΤΩΝ", "ΠΛΥΝΤΗΡ", "ΣΦΟΥΓΓ",
        "ΑΠΟΛΥΜΑΝΤ", "ΤΖΑΜΙ", "ΤΖΑΜΙΩΝ", "ΓΥΑΛΙΣΤ", "FAIRY", "SKIP", "ARIEL", "DIXAN", "AJAX",
        "VIAKAL", "ΑΛΑΤΑ ΠΛ", "ΣΚΟΝΗ ΠΛ", "ΥΓΡΟ ΠΙΑΤ", "ΑΠΟΛΙΠΑΝΤ", "ΑΦΑΛΑΤ", "ΣΚΟΥΠ", "ΣΦΟΥΓΓΑΡ",
        "ΓΑΝΤΙΑ", "ΣΚΟΝΗ ΠΛΥΝ", "ΚΑΨΟΥΛΕΣ ΠΛ", "ΥΓΡΟ ΠΛ", "ΕΝΤΟΜΟΚΤ", "ΚΑΤΣΑΡΙΔ",
    ),
    "personal-care" to listOf(
        "ΣΑΜΠΟΥΑΝ", "SHAMPOO", "ΑΦΡΟΛΟΥΤΡ", "ΣΑΠΟΥΝ", "ΟΔΟΝΤΟΚΡ", "ΟΔΟΝΤΟΒΟΥΡ", "ΟΔΟΝΤ",
        "ΑΠΟΣΜΗΤ", "ΞΥΡΙΣΤ", "ΞΥΡΑΦ", "ΜΑΛΛΙΩΝ", "ΧΑΡΤΟΜΑΝΤ", "ΜΑΝΤΗΛΑΚ", "ΣΕΡΒΙΕΤ", "ΠΑΝΑ",
        "ΠΑΝΕΣ", "ΠΑΝΑΚ", "ΒΑΜΒΑΚ", "COLGATE", "DOVE", "NIVEA", "GILLETTE", "ΑΝΤΗΛΙΑΚ", "ΜΑΚΙΓΙΑΖ",
        "ΚΟΛΩΝΙ", "ΑΡΩΜΑ", "ΜΑΛΑΚΤΙΚΗ ΜΑΛ", "ΚΡΕΜΑ ΧΕΡ", "ΚΡΕΜΑ ΠΡΟΣ", "ΚΡΕΜΑ ΣΩΜ", "ΛΟΣΙΟΝ",
        "ΜΩΡΟ", "ΜΩΡΟΜΑΝΤ", "ΤΑΜΠΟΝ", "ΣΤΟΜΑΤ", "ΒΑΦΗ ΜΑΛ", "ΖΕΛ ΜΑΛ", "ΑΦΡΟΣ ΞΥΡ",
    ),
    "household-items" to listOf(
        "ΧΑΡ", "ΧΑΡΤΙ", "ΧΑΡΤΟΠΕΤΣ", "ΧΑΡΤΟΒΑΜΒ", "ΡΟΛΟ", "ΑΛΟΥΜΙΝΟΧ", "ΜΕΜΒΡΑΝ",
        "ΣΑΚΟΥΛ", "ΜΠΑΤΑΡ",
        "ΛΑΜΠΑ", "ΛΑΜΠΤΗΡ", "ΚΕΡΙ", "ΚΕΡΙΑ", "ΑΝΑΠΤΗΡ", "ΣΠΙΡΤ", "ΠΟΤΗΡ", "ΚΑΛΑΜΑΚ", "ΠΙΑΤΑ",
        "ΜΑΧΑΙΡΟΠ", "ΠΙΡΟΥΝ", "ΚΟΥΤΑΛ", "ΤΑΨΙ", "ΦΙΛΜ", "ΑΝΤΑΛΛΑΚΤ", "ΚΡΕΜΑΣΤΡ", "ΜΑΝΤΑΛ",
    ),
    "pet-supplies" to listOf(
        "ΣΚΥΛ", "ΓΑΤΑ", "ΓΑΤΑΣ", "ΓΑΤΕΣ", "ΓΑΤΩΝ", "ΚΑΤΟΙΚΙΔ", "PEDIGREE", "WHISKAS", "FRISKIES",
        "PURINA", "FELIX", "ΑΜΜΟΣ ΓΑΤ", "ΤΡΟΦΗ ΣΚ", "ΤΡΟΦΗ ΓΑΤ",
    ),
    "pharmacy-and-health" to listOf(
        "ΒΙΤΑΜΙΝ", "ΠΑΡΑΚΕΤΑΜ", "DEPON", "PANADOL", "ΑΣΠΙΡΙΝ", "ΣΙΡΟΠ", "ΧΑΠΙ", "ΓΑΖΑ", "ΓΑΖΕΣ",
        "ΤΣΙΡΟΤ", "ΘΕΡΜΟΜ", "ΜΑΣΚΑ", "ΜΑΣΚΕΣ", "ΑΝΤΙΣΗΠΤ", "ΟΙΝΟΠΝ", "ΣΥΜΠΛΗΡ", "ΠΡΟΒΙΟΤ",
        "ΑΝΤΙΒΙΟΤ", "ΦΑΡΜΑΚ",
    ),
    "fuel-and-transport" to listOf(
        "ΑΜΟΛΥΒΔ", "UNLEADED", "DIESEL", "ΝΤΙΖΕΛ", "ΠΕΤΡΕΛ", "ΒΕΝΖΙΝ", "LPG", "ΥΓΡΑΕΡ", "ADBLUE",
        "ΔΙΟΔΙ", "ΕΙΣΙΤΗΡ", "ΣΤΑΘΜΕΥΣ", "PARKING", "ΚΑΥΣΙΜ", "ΛΙΠΑΝΤ", "ΛΑΔΙ ΜΗΧ",
    ),
    "clothing-and-footwear" to listOf(
        "ΚΑΛΤΣ", "ΜΠΛΟΥΖ", "ΠΑΝΤΕΛΟΝ", "ΦΟΥΣΤ", "ΦΟΡΕΜ", "ΠΑΠΟΥΤΣ", "ΠΑΝΤΟΦΛ", "ΕΣΩΡΟΥΧ",
        "ΣΛΙΠ", "ΣΟΥΤΙΕΝ", "ΜΠΟΞΕΡ", "ΜΑΓΙΟ", "ΚΑΣΚΟΛ", "ΓΑΝΤΙ ΠΛΕΚ", "ΣΚΟΥΦ", "ΤΖΑΚΕΤ",
        "T-SHIRT", "ΜΠΟΥΦΑΝ",
    ),
    "electronics-and-appliances" to listOf(
        "ΚΑΛΩΔ", "ΦΟΡΤΙΣΤ", "ΑΚΟΥΣΤ", "USB", "ΗΧΕΙ", "ΠΟΝΤΙΚ", "ΠΛΗΚΤΡΟΛ", "ΜΠΡΙΖ", "ΠΟΛΥΜΠΡ",
        "ΣΚΟΥΠΑ ΗΛ", "ΣΕΣΟΥΑΡ", "ΤΟΣΤΙΕΡ", "ΒΡΑΣΤΗΡ", "ΜΙΞΕΡ", "ΜΠΛΕΝΤΕΡ",
    ),
)

/** Greek capitals that look like Latin ones, folded to Latin on both sides of a comparison. */
private val LOOK_ALIKES: Map<Char, Char> = mapOf(
    'Α' to 'A', 'Β' to 'B', 'Ε' to 'E', 'Ζ' to 'Z', 'Η' to 'H', 'Ι' to 'I', 'Κ' to 'K',
    'Μ' to 'M', 'Ν' to 'N', 'Ο' to 'O', 'Ρ' to 'P', 'Τ' to 'T', 'Υ' to 'Y', 'Χ' to 'X',
)

private fun fold(word: String): String =
    buildString(word.length) { word.forEach { append(LOOK_ALIKES[it] ?: it) } }

private const val WHOLE_WORD_MAX = 3

/** (slug, stem words) with the stems normalized like product names, longest stems first. */
private val STEMS: List<Pair<String, List<String>>> = KEYWORDS.flatMap { (slug, words) ->
    words.map { word -> slug to normalizeText(word).pySplit().map(::fold) }
}.filter { it.second.isNotEmpty() }
    .sortedByDescending { (_, parts) -> parts.sumOf { it.length } }

private fun matches(parts: List<String>, words: List<String>, start: Int): Boolean {
    if (start + parts.size > words.size) return false
    return parts.withIndex().all { (offset, part) ->
        val word = words[start + offset]
        if (part.length <= WHOLE_WORD_MAX) word == part else word.startsWith(part)
    }
}

/**
 * The system category slug for a product [name], or null when no keyword matches. The first
 * word that matches decides (Greek names start with what the product is: "ΣΟΚΟΛΑΤΑ ΓΑΛΑΚΤΟΣ"
 * is a sweet, "ΓΑΛΑ ΣΟΚΟΛΑΤΑ" milk); among stems matching the same word, the longest wins.
 */
public fun suggestCategorySlug(name: String): String? {
    val words = normalizeText(name).pySplit().map(::fold)
    for (start in words.indices) {
        val hit = STEMS.firstOrNull { (_, parts) -> matches(parts, words, start) }
        if (hit != null) return hit.first
    }
    return null
}
