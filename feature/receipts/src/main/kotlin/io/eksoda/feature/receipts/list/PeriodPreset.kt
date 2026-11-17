package io.eksoda.feature.receipts.list

import io.eksoda.core.model.PeriodPreset
import io.eksoda.feature.receipts.R

/** The filter sheet's name for a period preset (the days are in core:model). */
internal val PeriodPreset.label: Int
    get() = when (this) {
        PeriodPreset.TODAY -> R.string.period_today
        PeriodPreset.THIS_WEEK -> R.string.period_this_week
        PeriodPreset.THIS_MONTH -> R.string.period_this_month
        PeriodPreset.LAST_MONTH -> R.string.period_last_month
        PeriodPreset.LAST_3_MONTHS -> R.string.period_last_3_months
        PeriodPreset.THIS_YEAR -> R.string.period_this_year
    }
