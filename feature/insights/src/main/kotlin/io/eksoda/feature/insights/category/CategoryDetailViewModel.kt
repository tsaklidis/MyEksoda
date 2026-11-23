package io.eksoda.feature.insights.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.AnalyticsRepository
import io.eksoda.core.data.repository.CatalogRepository
import io.eksoda.core.data.repository.GoalsRepository
import io.eksoda.core.model.BudgetProgress
import io.eksoda.core.model.CategoryDetail
import io.eksoda.core.model.Period
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

sealed interface CategoryDetailUiState {
    data object Loading : CategoryDetailUiState

    /** It could not be computed, or the category is gone. */
    data object Failed : CategoryDetailUiState

    data class Loaded(
        val detail: CategoryDetail,
        /** This month's limit against this month's spending; null without a limit. */
        val limit: BudgetProgress? = null,
        /** Limits to pick from, around what the category usually costs a month. */
        val suggestions: List<BigDecimal> = emptyList(),
    ) : CategoryDetailUiState
}

sealed interface CategoryDetailEvent {
    /** The household's name for it; blank brings a system category's own name back. */
    data class Rename(val name: String) : CategoryDetailEvent

    /** This month's limit on, from now on; null removes it. */
    data class SetLimit(val amount: BigDecimal?) : CategoryDetailEvent

    data object Retry : CategoryDetailEvent
}

sealed interface CategoryDetailEffect {
    data object SaveFailed : CategoryDetailEffect
}

/**
 * One category over the period it was opened with: its total, products and shops, and (for a
 * real category) its name and monthly limit, which can be changed here. It follows the data, so
 * a recategorized product, a new receipt or a new limit shows by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    private val goals: GoalsRepository,
    private val catalog: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    /** Null for the uncategorized products. */
    private val categoryId: String? = savedStateHandle[CATEGORY_ID]
    private val period = Period.Range(
        LocalDate.parse(checkNotNull(savedStateHandle[FROM])),
        LocalDate.parse(checkNotNull(savedStateHandle[TO])),
    )
    private val attempts = MutableStateFlow(0)

    val state: StateFlow<CategoryDetailUiState> = combine(
        attempts.flatMapLatest { analytics.changes() },
        goals.getEveryday(),
    ) { _, _ -> }
        .mapLatest { load() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            CategoryDetailUiState.Loading,
        )

    private val _effects = Channel<CategoryDetailEffect>(Channel.BUFFERED)
    val effects: Flow<CategoryDetailEffect> = _effects.receiveAsFlow()

    fun onEvent(event: CategoryDetailEvent) {
        val id = categoryId
        when (event) {
            CategoryDetailEvent.Retry -> attempts.value++

            is CategoryDetailEvent.Rename -> if (id != null) {
                save { catalog.renameCategory(id, event.name) }
            }

            is CategoryDetailEvent.SetLimit -> if (id != null) {
                save { goals.setCategoryLimit(id, event.amount) }
            }
        }
    }

    private fun save(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            if (block().isFailure) _effects.send(CategoryDetailEffect.SaveFailed)
        }
    }

    private suspend fun load(): CategoryDetailUiState = coroutineScope {
        val detail = async { analytics.getCategoryDetail(categoryId, period) }
        val id = categoryId
        if (id == null) {
            return@coroutineScope detail.await().fold(
                onSuccess = { CategoryDetailUiState.Loaded(it) },
                onFailure = { CategoryDetailUiState.Failed },
            )
        }
        val month = YearMonth.from(analytics.today())
        val limits = async { goals.getMonthBudgets(month) }
        // What it usually costs: the three full months before this one.
        val usual = async {
            val from = month.minusMonths(USUAL_MONTHS.toLong())
            analytics.getCategoryDetail(
                id,
                Period.Range(from.atDay(1), month.minusMonths(1).atEndOfMonth()),
            )
                .getOrNull()?.total?.divide(BigDecimal(USUAL_MONTHS), 2, RoundingMode.HALF_UP)
        }
        detail.await().fold(
            onSuccess = { loaded ->
                CategoryDetailUiState.Loaded(
                    detail = loaded,
                    limit = limits.await().getOrNull()?.categories?.firstOrNull {
                        it.category?.id ==
                            id
                    },
                    suggestions = limitSuggestions(usual.await()),
                )
            },
            onFailure = { CategoryDetailUiState.Failed },
        )
    }

    private companion object {
        // The navigation arguments: the property names of the app's CategoryDetailDestination.
        const val CATEGORY_ID = "categoryId"
        const val FROM = "from"
        const val TO = "to"
        const val STOP_TIMEOUT_MS = 5_000L
        const val USUAL_MONTHS = 3
    }
}

private val STEP = BigDecimal("50")
private val DEFAULT_SUGGESTIONS = listOf(BigDecimal("50"), BigDecimal("100"), BigDecimal("200"))

/**
 * Three limits to pick from: what the category [usual]ly costs a month rounded up to 50 €, and
 * 50 € below and above it; 50, 100 and 200 € when it costs nothing yet.
 */
internal fun limitSuggestions(usual: BigDecimal?): List<BigDecimal> {
    if (usual == null || usual.signum() <= 0) return DEFAULT_SUGGESTIONS
    val middle = usual.divide(STEP, 0, RoundingMode.CEILING).multiply(STEP).max(STEP + STEP)
    return listOf(middle - STEP, middle, middle + STEP)
}
