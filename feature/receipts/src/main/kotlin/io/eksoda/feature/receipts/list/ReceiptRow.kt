package io.eksoda.feature.receipts.list

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.eksoda.core.designsystem.component.MerchantAvatar
import io.eksoda.core.designsystem.component.OverlineText
import io.eksoda.core.designsystem.theme.BadgeShape
import io.eksoda.core.designsystem.theme.EksodaTheme
import io.eksoda.core.model.DocumentKind
import io.eksoda.core.model.Money
import io.eksoda.core.model.PaymentMethod
import io.eksoda.core.model.Receipt
import io.eksoda.core.ui.component.FromOtherPhoneMark
import io.eksoda.core.ui.component.MoneyText
import io.eksoda.core.ui.component.logo
import io.eksoda.core.ui.motion.SharedKeys
import io.eksoda.core.ui.motion.sharedBoundsIfAvailable
import io.eksoda.core.ui.text.dayLabel
import io.eksoda.feature.receipts.R
import java.time.LocalDate

/**
 * A receipt in the list. Merchant names are shown as printed. A plain row with the look of a
 * Material list item: `ListItem` measures its slots in a generic layout, which costs more than
 * this on every row of a fast scroll.
 */
@Composable
internal fun ReceiptRow(receipt: Receipt, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MerchantAvatar(
            receipt.merchant.id,
            receipt.merchant.name,
            logo = receipt.merchant.brand?.logo,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = receipt.merchant.name,
                    style = typography.bodyLarge,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .sharedBoundsIfAvailable(SharedKeys.receiptMerchant(receipt.id)),
                )
                if (receipt.hasWarnings) WarningMark()
                if (receipt.fromOtherPhone) FromOtherPhoneMark()
            }
            Text(
                text = summaryLine(receipt),
                style = typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ReceiptBadges(receipt)
        }
        MoneyText(
            money = receipt.totalAmount,
            negative = receipt.documentKind == DocumentKind.CREDIT_NOTE,
            textDecoration = if (receipt.cancelled) TextDecoration.LineThrough else null,
            style = typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.sharedBoundsIfAvailable(SharedKeys.receiptTotal(receipt.id)),
        )
    }
}

/** "Kifisias 12 · 14 items · Card". */
@Composable
private fun summaryLine(receipt: Receipt): String {
    val storeLabel = receipt.store?.label?.takeIf { it.isNotEmpty() }
    val itemCountStr =
        pluralStringResource(R.plurals.item_count, receipt.itemCount, receipt.itemCount)
    val paymentRes = paymentLabel(receipt.paymentMethod)
    val paymentStr = if (paymentRes != null) stringResource(paymentRes) else null

    return when {
        storeLabel != null && paymentStr != null -> "$storeLabel · $itemCountStr · $paymentStr"
        storeLabel != null -> "$storeLabel · $itemCountStr"
        paymentStr != null -> "$itemCountStr · $paymentStr"
        else -> itemCountStr
    }
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

/**
 * A receipt with warnings: a small terracotta ⓘ after the merchant, no text, so that a receipt
 * whose amounts merely don't agree does not shout. A sign, not only a color (ui-redesign.md §1).
 * The detail screen explains the warnings; screen readers hear [R.string.badge_warnings].
 */
@Composable
private fun WarningMark() {
    Icon(
        imageVector = Icons.Outlined.Info,
        contentDescription = stringResource(R.string.badge_warnings),
        tint = EksodaTheme.colors.spend,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun ReceiptBadges(receipt: Receipt) {
    val hasAttention = receipt.needsReview
    val hasStatus = receipt.cancelled ||
        receipt.documentKind == DocumentKind.CREDIT_NOTE ||
        receipt.excludedFromAnalytics
    if (!hasAttention && !hasStatus) {
        return
    }
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (receipt.needsReview) {
            StatusTag(
                stringResource(R.string.badge_review),
                BadgeTone.ATTENTION,
            )
        }
        if (receipt.cancelled) {
            StatusTag(
                stringResource(R.string.badge_cancelled),
                BadgeTone.NEUTRAL,
            )
        }
        if (receipt.documentKind == DocumentKind.CREDIT_NOTE) {
            StatusTag(stringResource(R.string.badge_refund), BadgeTone.NEUTRAL)
        }
        if (receipt.excludedFromAnalytics) {
            StatusTag(
                stringResource(R.string.badge_not_in_stats),
                BadgeTone.MUTED,
            )
        }
    }
}

/**
 * Status tags (docs/design/ui-redesign.md §1): attention items in terracotta on its tint;
 * neutral facts (cancelled, refund) in ink-2 on the inset color; muted ones (not in stats)
 * outlined only. Red is kept for errors and destructive actions.
 */
internal enum class BadgeTone { ATTENTION, NEUTRAL, MUTED }

/**
 * A status tag in [tone], with an optional leading [icon]. With [onClick] it is a button (with
 * Material's 48dp touch target), e.g. an item's warnings mark, which opens them.
 */
@Composable
internal fun StatusTag(
    text: String,
    tone: BadgeTone,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = EksodaTheme.colors
    val (background, content, border) = when (tone) {
        BadgeTone.ATTENTION -> Triple(colors.spendTint, colors.spendText, colors.spendBorder)
        BadgeTone.NEUTRAL -> Triple(colors.inset, colors.ink2, colors.line)
        BadgeTone.MUTED -> Triple(Color.Transparent, colors.ink2, colors.line)
    }
    val label: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = BadgeShape,
            color = background,
            contentColor = content,
            border = BorderStroke(1.dp, border),
            content = label,
        )
    } else {
        Surface(
            shape = BadgeShape,
            color = background,
            contentColor = content,
            border = BorderStroke(1.dp, border),
            content = label,
        )
    }
}

/** A day header with that day's total of the loaded rows (a convenience). */
@Composable
internal fun DayHeader(date: LocalDate, today: LocalDate, total: Money?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlineText(dayLabel(date, today))
        if (total != null) {
            MoneyText(
                money = total,
                negative = total.amount.signum() < 0,
                style = MaterialTheme.typography.labelMedium,
                color = EksodaTheme.colors.ink2,
            )
        }
    }
}
