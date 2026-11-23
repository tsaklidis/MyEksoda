package io.eksoda.feature.receipts.manual

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.data.repository.CatalogRepository
import io.eksoda.core.data.repository.ManualExpenseRepository
import io.eksoda.core.data.repository.ReceiptRepository
import io.eksoda.core.model.Category
import io.eksoda.core.model.ManualExpense
import io.eksoda.core.model.ManualKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/** What the form starts with: empty for a new expense, the expense's own when editing one. */
data class ManualDraft(
    val amount: BigDecimal? = null,
    val date: LocalDate,
    val kind: ManualKind = ManualKind.SERVICE,
    val description: String = "",
    val categoryId: String? = null,
    val note: String = "",
)

data class ManualExpenseUiState(
    val loading: Boolean = true,
    /** Whether an existing expense is being edited. */
    val editing: Boolean = false,
    /** Null while loading; the form's starting values. */
    val initial: ManualDraft? = null,
    val today: LocalDate? = null,
    /** Every category, the most likely for a typed-in expense first. */
    val categories: List<Category> = emptyList(),
    /** Descriptions typed before, most used first. */
    val suggestions: List<String> = emptyList(),
    val saving: Boolean = false,
)

sealed interface ManualExpenseEvent {
    data class Save(val expense: ManualExpense) : ManualExpenseEvent
}

sealed interface ManualExpenseEffect {
    data object Saved : ManualExpenseEffect

    data object Failed : ManualExpenseEffect
}

/**
 * Adding an expense by hand (from "+"), or editing one ([RECEIPT_ID] in the arguments): the
 * amount, what it was (product, service or other) with a few words, an optional category and
 * the day.
 */
@HiltViewModel
class ManualExpenseViewModel @Inject constructor(
    private val manual: ManualExpenseRepository,
    receipts: ReceiptRepository,
    catalog: CatalogRepository,
    analytics: AnalyticsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val receiptId: String? = savedStateHandle[RECEIPT_ID]
    private val saving = MutableStateFlow(false)

    private val start: Flow<Pair<LocalDate, ManualDraft>> = flow {
        val today = analytics.today()
        val existing = receiptId?.let { receipts.getReceipt(it).first() }
        val draft = existing?.let { detail ->
            ManualDraft(
                amount = detail.receipt.totalAmount.amount,
                date = detail.receipt.issuedDate,
                kind = detail.manualKind ?: ManualKind.OTHER,
                description = detail.receipt.merchant.name,
                categoryId = detail.categoryId,
                note = detail.userNote,
            )
        } ?: ManualDraft(date = today)
        emit(today to draft)
    }

    val state: StateFlow<ManualExpenseUiState> = combine(
        start,
        catalog.getCategoryList(),
        manual.descriptions,
        saving,
    ) { (today, draft), categories, descriptions, isSaving ->
        ManualExpenseUiState(
            loading = false,
            editing = receiptId != null,
            initial = draft,
            today = today,
            categories = categories.sortedBy { likelyRank(it.slug) },
            suggestions = descriptions,
            saving = isSaving,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        ManualExpenseUiState(),
    )

    private val _effects = Channel<ManualExpenseEffect>(Channel.BUFFERED)
    val effects: Flow<ManualExpenseEffect> = _effects.receiveAsFlow()

    fun onEvent(event: ManualExpenseEvent) {
        when (event) {
            is ManualExpenseEvent.Save -> {
                if (saving.value) return
                saving.value = true
                viewModelScope.launch {
                    val id = receiptId
                    val result = if (id == null) {
                        manual.add(event.expense).map { }
                    } else {
                        manual.update(id, event.expense)
                    }
                    saving.value = false
                    _effects.send(
                        if (result.isSuccess) {
                            ManualExpenseEffect.Saved
                        } else {
                            ManualExpenseEffect.Failed
                        },
                    )
                }
            }
        }
    }

    /** Where [slug] stands among the likely categories; the others after them, in order. */
    private fun likelyRank(slug: String): Int =
        LIKELY.indexOf(slug).let { if (it < 0) LIKELY.size else it }

    private companion object {
        // The navigation argument: the property name of the app's ManualExpenseDestination.
        const val RECEIPT_ID = "receiptId"
        const val STOP_TIMEOUT_MS = 5_000L

        /** The categories a typed-in expense is most likely to be, first. */
        val LIKELY = listOf(
            "services",
            "pharmacy-and-health",
            "restaurants-and-cafes",
            "fuel-and-transport",
            "clothing-and-footwear",
            "household-items",
            "personal-care",
            "electronics-and-appliances",
        )
    }
}
