package io.eksoda.feature.receipts.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.component.MerchantAvatar
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.Money
import io.eksoda.core.model.PaymentMethod
import io.eksoda.core.model.Receipt
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.motion.SharedKeys
import io.eksoda.core.ui.motion.sharedBoundsIfAvailable
import io.eksoda.core.ui.text.dayLabel
import io.eksoda.feature.receipts.R
import java.time.LocalDate

/** A receipt in the list (android-app.md §6.4.1). Merchant names are shown as printed. */
@Composable
internal fun ReceiptRow(receipt: Receipt, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { MerchantAvatar(receipt.merchant.id, receipt.merchant.name) },
        headlineContent = {
            Text(
                text = receipt.merchant.name,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.sharedBoundsIfAvailable(SharedKeys.receiptMerchant(receipt.id)),
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = summaryLine(receipt),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ReceiptBadges(receipt)
            }
        },
        trailingContent = {
            MoneyText(
                money = receipt.totalAmount,
                negative = receipt.documentKind == DocumentKind.CREDIT_NOTE,
                textDecoration = if (receipt.cancelled) TextDecoration.LineThrough else null,
                modifier = Modifier.sharedBoundsIfAvailable(SharedKeys.receiptTotal(receipt.id)),
            )
        },
    )
}

/** "Kifisias 12 · 14 items · Card". */
@Composable
private fun summaryLine(receipt: Receipt): String {
    val parts = buildList {
        receipt.store?.label?.takeIf { it.isNotEmpty() }?.let { add(it) }
        add(pluralStringResource(R.plurals.item_count, receipt.itemCount, receipt.itemCount))
        paymentLabel(receipt.paymentMethod)?.let { add(stringResource(it)) }
    }
    return parts.joinToString(" · ")
}

/** The label of a payment method; none when unknown. */
internal fun paymentLabel(method: PaymentMethod): Int? = when (method) {
    PaymentMethod.CARD -> R.string.payment_card
    PaymentMethod.CASH -> R.string.payment_cash
    PaymentMethod.BANK -> R.string.payment_bank
    PaymentMethod.MIXED -> R.string.payment_mixed
    PaymentMethod.OTHER -> R.string.payment_other
    PaymentMethod.UNKNOWN -> null
}

@Composable
private fun ReceiptBadges(receipt: Receipt) {
    val badges = buildList {
        if (receipt.needsReview) add(R.string.badge_review to BadgeTone.ATTENTION)
        if (receipt.hasWarnings) add(R.string.badge_warnings to BadgeTone.ATTENTION)
        if (receipt.cancelled) add(R.string.badge_cancelled to BadgeTone.ERROR)
        if (receipt.documentKind == DocumentKind.CREDIT_NOTE) {
            add(R.string.badge_refund to BadgeTone.NEUTRAL)
        }
        if (receipt.excludedFromAnalytics) add(R.string.badge_not_in_stats to BadgeTone.MUTED)
    }
    if (badges.isEmpty()) return
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        badges.forEach { (label, tone) -> StatusTag(stringResource(label), tone) }
    }
}

internal enum class BadgeTone { ATTENTION, ERROR, NEUTRAL, MUTED }

@Composable
internal fun StatusTag(text: String, tone: BadgeTone) {
    val colors = MaterialTheme.colorScheme
    val (background, content) = when (tone) {
        BadgeTone.ATTENTION -> colors.tertiaryContainer to colors.onTertiaryContainer
        BadgeTone.ERROR -> colors.errorContainer to colors.onErrorContainer
        BadgeTone.NEUTRAL -> colors.secondaryContainer to colors.onSecondaryContainer
        BadgeTone.MUTED -> colors.surfaceVariant to colors.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(6.dp), color = background, contentColor = content) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** A day header with that day's total of the loaded rows (a convenience, §6.4.1). */
@Composable
internal fun DayHeader(date: LocalDate, today: LocalDate, total: Money?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = dayLabel(date, today).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (total != null) {
            MoneyText(
                money = total,
                negative = total.amount.signum() < 0,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
