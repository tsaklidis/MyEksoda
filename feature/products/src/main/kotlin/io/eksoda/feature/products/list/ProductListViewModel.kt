package io.eksoda.feature.products.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.eksoda.core.data.repository.ProductRepository
import io.eksoda.core.model.Product
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class ProductListViewModel @Inject constructor(productRepository: ProductRepository) :
    ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    private val searchQueryFlow = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: Flow<PagingData<Product>> = searchQueryFlow
        .flatMapLatest { query ->
            productRepository.getProducts(query.takeIf { it.isNotBlank() })
        }
        .cachedIn(viewModelScope)

    fun onSearchQueryChange(query: String) {
        searchQuery = query
        searchQueryFlow.value = query
    }
}
