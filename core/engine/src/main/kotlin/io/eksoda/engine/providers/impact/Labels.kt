package io.eksoda.engine.providers.impact

// Field names of the Impact receipt page.
//
// The commercial view marks fields with `field-<Name>` CSS classes. The tax view (myDATA) uses
// label/value table rows whose labels are Greek in every culture (observed 2026-11-14). Labels
// are compared after whitespace collapsing.

internal object Labels {
    // --- Commercial view ---------------------------------------------------------------------
    val ADDRESS_FIELDS = setOf("Street", "Number", "City", "Postal", "Country")

    /** Fields read per section; any other field there is reported in unknown_fields (format drift). */
    val COMMERCIAL_KNOWN: Map<String, Set<String>> = mapOf(
        "header" to setOf(
            "RegisteredName", "Activities", "Vat", "TaxOffice", "GeneralCommercialRegistryNumber",
            "RegistrationNumber", "Phones", "Emails", "Url", "OtherInfo",
        ) + ADDRESS_FIELDS,
        "identity" to setOf("InvoiceType", "IssuerFormatedInvoiceSeriesNumber", "DateIssued"),
        "line" to setOf(
            "Code", "Description1", "Quantity", "MeasurementUnit", "UnitPrice",
            "NetTotal", "VatCategory", "VATTotal", "Total",
        ),
        "payment" to setOf("PaymentMethodType", "Amount"),
        "summary" to setOf("CurrencyCode", "TotalNetAmount", "TotalVATAmount", "TotalGrossValue"),
        "certification" to setOf("Mark", "Uid", "ServiceSupplier"),
    )

    /** Personal or secret values that are never read (the sanitizer replaces them). */
    val COMMERCIAL_DROP = setOf("TransactionId", "Authentication")

    // --- Tax view (myDATA) -------------------------------------------------------------------
    val TAX_HEADER = mapOf(
        "Σειρά Παραστατικού" to "series",
        "ΑΑ Παραστατικού" to "number",
        "Ημερομηνία Έκδοσης" to "issue_date",
        "Είδος Παραστατικού" to "type_code",
        "Νόμισμα" to "currency",
        "Ένδειξη Αυτοτιμολόγησης" to "self_billing",
    )
    val TAX_ISSUER =
        mapOf("ΑΦΜ" to "vat", "Κωδ. Χώρας" to "country", "Αρ. Εγκατάστασης" to "branch")
    const val ISSUER_PARTY_TITLE = "Στοιχεία Εκδότη"
    val TAX_SUMMARY = mapOf(
        "Σύνολο Καθαρής Αξίας" to "net",
        "Σύνολο ΦΠΑ" to "vat",
        "Σύνολο Παρ. Φόρων" to "withheld",
        "Σύνολο Τελών" to "fees",
        "Σύνολο Ψηφιακού Τέλους συναλλαγής" to "digital_fee",
        "Σύνολο Λοιπών Φόρων" to "other_taxes",
        "Σύνολο Χαρτοσήμου" to "stamp_duty",
        "Σύνολο Κρατήσεων" to "deductions",
        "Συνολική Αξία" to "gross",
    )
    val TAX_PAYMENT = mapOf(
        "Τύπος Πληρωμής" to "method_code",
        "Αναλογούν Ποσό" to "amount",
        "Φιλοδώρημα" to "tip",
    )
    val TAX_IDENTIFIERS = mapOf(
        "Μοναδικός Αριθμός Καταχώρησης Παραστατικού" to "mark",
        "Αναγνωριστικό Παραστατικού" to "uid",
        "Url με το οποίο ο λήπτης του παραστατικού θα μπορεί να το λαμβάνει" to "url",
        "Μοναδικός Αριθμός Καταχώρησης Ακυρωτικού" to "cancellation_mark",
    )

    /** Long labels matched by prefix. */
    val TAX_IDENTIFIER_PREFIXES = mapOf("Αδυναμία Επικοινωνίας Παρόχου" to "transmission_failure")
    val TAX_LINE = mapOf(
        "ΑΑ Γραμμής" to "line_number",
        "Είδος Γραμμής" to "line_type_code",
        "Κωδικός Είδους" to "code",
        "Περιγραφή Είδους" to "description",
        "Ποσότητα" to "quantity",
        "Είδος Ποσότητας" to "unit_code",
        "Επισήμανση" to "detail_type",
        "Καθαρή Αξία" to "net",
        "Κατηγορία ΦΠΑ" to "vat_category",
        "Ποσό ΦΠΑ" to "vat",
        "Κατηγορία Αιτίας Εξαίρεσης ΦΠΑ" to "vat_exemption",
        "Δικαίωμα Έκπτωσης" to "deduction_right",
        "Ποσό Παρ. Φόρου" to "withheld",
        "Κατηγορία Συντελεστή Παρ. Φόρου" to "withheld_category",
        "Ποσό Ψηφιακού Τέλους συναλλαγής" to "digital_fee",
        "Κατηγορία Συντελεστή Ψηφιακού Τέλους συναλλαγής" to "digital_fee_category",
        "Ποσό Τελών" to "fees",
        "Κατηγορία Συντελεστή Τελών" to "fees_category",
        "Ποσό Λοιπών Φόρων" to "other_taxes",
        "Κατηγορία Συντελεστή Λοιπών Φόρων" to "other_taxes_category",
        "Ποσό Χαρτοσήμου" to "stamp_duty",
        "Ποσό Φόρου Διαμονής" to "accommodation_tax",
        "Ποσό Κρατήσεων" to "deductions",
        "Σχόλια Γραμμής" to "comments",
        "Ένδειξη μη συμμετοχής στο ΦΠΑ (έσοδα – εκροές)" to "not_vat_relevant",
    )

    /** Personal or secret values: skipped, never stored (the sanitizer replaces them). */
    val TAX_DROP = setOf(
        "Μοναδική Ταυτότητα Πληρωμής",
        "ID Τερματικού POS",
        "Provider’s Id",
        "Provider's Id",
        "Υπογραφή",
        "Συμβολοσειρά Αυθεντικοποίησης Παρόχου",
    )

    const val NOT_FOUND_MARKER = "Document with UniqueId"
}
