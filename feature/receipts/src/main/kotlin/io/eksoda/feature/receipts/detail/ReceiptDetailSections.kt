package io.eksoda.feature.receipts.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.text.monoNumbers
import io.eksoda.core.designsystem.theme.CardShape
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.designsystem.theme.HeroNumberStyle
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.IssueTime
import io.eksoda.core.model.ReceiptDetail
import io.eksoda.core.model.ReceiptWarning
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.motion.SharedKeys
import io.eksoda.core.ui.motion.sharedBoundsIfAvailable
import io.eksoda.core.ui.text.appLocale
import io.eksoda.core.ui.text.formatMoney
import io.eksoda.feature.receipts.R
import io.eksoda.feature.receipts.list.paymentLabel
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Merchant, store, date, total, payment and item count. */
@Composable
internal fun DetailHeader(detail: ReceiptDetail) {
    val receipt = detail.receipt
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        receipt.store?.label?.takeIf { it.isNotEmpty() }?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Text(issuedText(detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The hero amount: terracotta for spending, periwinkle for a refund (ui-redesign.md §1).
        val refund = receipt.documentKind == DocumentKind.CREDIT_NOTE
        MoneyText(
            money = receipt.totalAmount,
            negative = refund,
            style = HeroNumberStyle,
            color = if (refund) EksodaTheme.colors.compare else EksodaTheme.colors.spend,
            textDecoration = if (receipt.cancelled) TextDecoration.LineThrough else null,
            modifier = Modifier
                .padding(top = 8.dp)
                .sharedBoundsIfAvailable(SharedKeys.receiptTotal(receipt.id)),
        )
        val parts = listOfNotNull(
            paymentLabel(receipt.paymentMethod)?.let { stringResource(it) },
            pluralStringResource(R.plurals.item_count, receipt.itemCount, receipt.itemCount),
        )
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
    }
}

/** "Mon 11 Nov 2026, 18:42"; "~18:40" when approximate; the date only when that is all. */
@Composable
private fun issuedText(detail: ReceiptDetail): String {
    val locale = appLocale()
    val issued = detail.receipt.issuedAt
    val date = issued.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val time =
        issued.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    return when (detail.issueTime) {
        IssueTime.EXACT -> stringResource(R.string.detail_date_time, date, time)
        IssueTime.APPROXIMATE -> stringResource(R.string.detail_date_approximate_time, date, time)
        IssueTime.DATE_ONLY -> date
    }
}

/**
 * A banner: cancelled by the shop ([attention]: terracotta, an item that needs attention), or a
 * totals-only receipt (neutral).
 */
@Composable
internal fun Notice(text: String, attention: Boolean = false) {
    val colors = EksodaTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (attention) colors.spendTint else colors.inset,
            contentColor = if (attention) colors.spendText else colors.ink,
        ),
        border = BorderStroke(1.dp, if (attention) colors.spendBorder else colors.line),
    ) {
        Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The validation warnings, collapsed to the first one. */
@Composable
internal fun WarningsCard(warnings: List<ReceiptWarning>) {
    if (warnings.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = warnings.size > 1) { expanded = !expanded },
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = EksodaTheme.colors.spendTint,
            contentColor = EksodaTheme.colors.spendText,
        ),
        border = BorderStroke(1.dp, EksodaTheme.colors.spendBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val shown = if (expanded) warnings else warnings.take(1)
            shown.forEach { WarningLine(it) }
            if (warnings.size > 1) {
                Icon(
                    imageVector = expandIcon(expanded),
                    contentDescription = stringResource(
                        if (expanded) R.string.detail_show_less else R.string.detail_show_more,
                    ),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun WarningLine(warning: ReceiptWarning) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.Warning, contentDescription = null)
        val text = stringResource(warningRes(warning.code))
        val line = warning.lineNumber
        Text(if (line != null) stringResource(R.string.detail_warning_line, line, text) else text)
    }
}

/** Validation issue codes (the engine's `validation`), as local strings. */
private fun warningRes(code: String): Int = when (code) {
    "line_amount_mismatch" -> R.string.warning_line_amount
    "unit_price_mismatch" -> R.string.warning_unit_price
    "discount_mismatch" -> R.string.warning_discount
    "vat_rate_mismatch" -> R.string.warning_vat_rate
    "total_mismatch", "receipt_amount_mismatch" -> R.string.warning_total
    "total_discount_mismatch" -> R.string.warning_total_discount
    "vat_breakdown_mismatch", "vat_breakdown_rate_mismatch" -> R.string.warning_vat_breakdown
    "payment_mismatch" -> R.string.warning_payment
    else -> R.string.warning_other
}

private fun expandIcon(expanded: Boolean) =
    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore

@Composable
internal fun SectionHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

/** Payment methods ("derived" ones marked estimated) and the VAT breakdown. */
@Composable
internal fun PaymentAndVat(detail: ReceiptDetail) {
    val locale = appLocale()
    val payments = detail.payments.map { payment ->
        val method = payment.label?.takeIf { it.isNotBlank() }
            ?: paymentLabel(payment.method)?.let { stringResource(it) }
            ?: stringResource(R.string.payment_unknown)
        val amount = formatMoney(payment.amount, locale)
        if (payment.estimated) {
            stringResource(R.string.detail_payment_estimated, method, amount)
        } else {
            stringResource(R.string.detail_payment, method, amount)
        }
    }
    val vat = detail.vatBreakdown.mapNotNull { entry ->
        entry.rate?.let { rate ->
            val percent = rate.stripTrailingZeros().toPlainString()
            stringResource(R.string.detail_vat_entry, percent, formatMoney(entry.vat, locale))
        }
    }
    if (payments.isNotEmpty()) {
        LabeledValue(stringResource(R.string.detail_payment_title), payments.joinToString("\n"))
    }
    if (vat.isNotEmpty()) {
        LabeledValue(stringResource(R.string.detail_vat_title), vat.joinToString(" · "))
    }
}

@Composable
internal fun LabeledValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = monoNumbers(value),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
        )
    }
}

/** Document number, type code, MARK, UID, provider and parser version, collapsed. */
@Composable
internal fun DetailsSection(detail: ReceiptDetail) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.detail_details),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = expandIcon(expanded),
            contentDescription = null,
        )
    }
    if (!expanded) return
    SelectionContainer {
        Column {
            listOf(
                R.string.detail_document_number to detail.documentNumber,
                R.string.detail_document_type to detail.documentTypeCode,
                R.string.detail_mark to detail.mark,
                R.string.detail_uid to detail.uid,
                R.string.detail_cancellation_mark to detail.cancellationMark,
                R.string.detail_provider to detail.providerCode,
                R.string.detail_parser_version to detail.parserVersion,
            ).forEach { (label, value) ->
                if (!value.isNullOrEmpty()) LabeledValue(stringResource(label), value)
            }
        }
    }
}
