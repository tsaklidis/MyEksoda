package io.eksoda.feature.products.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.CatalogRepository
import io.eksoda.core.model.CatalogError
import io.eksoda.core.model.CategorySummary
import io.eksoda.feature.products.common.catalogError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CategoriesEvent {
    data class Create(val name: String) : CategoriesEvent

    data class Rename(val id: String, val name: String) : CategoriesEvent

    data class Delete(val id: String) : CategoriesEvent
}

sealed interface CategoriesEffect {
    data object Deleted : CategoriesEffect

    data class Failed(val error: CatalogError?) : CategoriesEffect
}

/** Categories (`android-app.md` §6.9): system ones are read-only, the household's are not. */
@HiltViewModel
class CategoriesViewModel @Inject constructor(private val catalog: CatalogRepository) :
    ViewModel() {
    /** Null while loading. */
    val categories: StateFlow<List<CategorySummary>?> = catalog.getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val _effects = Channel<CategoriesEffect>(Channel.BUFFERED)
    val effects: Flow<CategoriesEffect> = _effects.receiveAsFlow()

    fun onEvent(event: CategoriesEvent) {
        viewModelScope.launch {
            val result = when (event) {
                is CategoriesEvent.Create -> catalog.createCategory(event.name).map { }
                is CategoriesEvent.Rename -> catalog.renameCategory(event.id, event.name)
                is CategoriesEvent.Delete -> catalog.deleteCategory(event.id)
            }
            val effect = when {
                result.isFailure -> CategoriesEffect.Failed(result.exceptionOrNull().catalogError())
                event is CategoriesEvent.Delete -> CategoriesEffect.Deleted
                else -> null
            }
            effect?.let { _effects.send(it) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
