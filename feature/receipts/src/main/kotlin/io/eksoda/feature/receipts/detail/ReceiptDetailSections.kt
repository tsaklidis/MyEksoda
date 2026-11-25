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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * The validation warnings, in two cards. Notices that need attention (the QR signature or amount
 * differs from AADE's record, no MARK, another currency, an unknown document type) are
 * terracotta, each with its own message. The arithmetic checks are worded as what they are: the
 * receipt's amounts do not agree with each other and are left as printed; they are neutral,
 * since nothing is wrong with the user's data. One entry per kind of issue; the items it
 * concerns are marked in the item list ([LineRow]) and only counted here; [marked] are the line
 * numbers whose row carries a mark (none when the receipt shows no item list), the others are
 * named by number; "Show" scrolls to the marked items ([onShowLines]). Each card is collapsed to
 * its first entry.
 */
@Composable
internal fun WarningsCard(
    warnings: List<ReceiptWarning>,
    marked: Set<Int>,
    onShowLines: (List<Int>) -> Unit,
) {
    if (warnings.isEmpty()) return
    val groups = remember(warnings) { groupWarnings(warnings) }
    Column {
        val notices = groups.filterNot { it.amountCheck }
        WarningGroupsCard(notices, marked, attention = true, onShowLines = onShowLines)
        val checks = groups.filter { it.amountCheck }
        WarningGroupsCard(checks, marked, attention = false, onShowLines = onShowLines)
    }
}

@Composable
private fun WarningGroupsCard(
    groups: List<WarningGroup>,
    marked: Set<Int>,
    attention: Boolean,
    onShowLines: (List<Int>) -> Unit,
) {
    if (groups.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = EksodaTheme.colors
    val content = if (attention) colors.spendText else colors.ink
    // Secondary text: which items, and the expand arrow.
    val detail = if (attention) colors.spendText else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = groups.size > 1) { expanded = !expanded },
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (attention) colors.spendTint else colors.inset,
            contentColor = content,
        ),
        border = BorderStroke(1.dp, if (attention) colors.spendBorder else colors.line),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (attention) Icons.Outlined.WarningAmber else Icons.Outlined.Info,
                contentDescription = null,
                tint = detail,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!attention) {
                    Text(
                        stringResource(R.string.detail_warnings_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                val shown = if (expanded) groups else groups.take(1)
                shown.forEach { WarningEntry(it, marked, detail, onShowLines) }
                if (groups.size > 1) {
                    Icon(
                        imageVector = expandIcon(expanded),
                        contentDescription = stringResource(
                            if (expanded) R.string.detail_show_less else R.string.detail_show_more,
                        ),
                        tint = detail,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }
        }
    }
}

/**
 * The message, and which items it concerns: how many are marked in the list (with "Show", which
 * scrolls to the first), and the line numbers of any that cannot be marked (a number two items
 * share, or no item list).
 */
@Composable
private fun WarningEntry(
    group: WarningGroup,
    marked: Set<Int>,
    detail: Color,
    onShowLines: (List<Int>) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(group.message),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        val (inList, unmarked) = lineMarks(group.lines, marked)
        if (inList > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pluralStringResource(R.plurals.detail_warning_marked, inList, inList),
                    style = MaterialTheme.typography.bodySmall,
                    color = detail,
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(onClick = { onShowLines(group.lines) }) {
                    Text(stringResource(R.string.detail_warning_show))
                }
            }
        }
        if (unmarked.isNotEmpty()) {
            Text(
                pluralStringResource(
                    R.plurals.detail_warning_lines,
                    unmarked.size,
                    unmarked.joinToString(", "),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = detail,
            )
        }
    }
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
