package com.zai.mamsad.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zai.mamsad.MamsadApp
import com.zai.mamsad.data.CatalogFilter
import com.zai.mamsad.data.MamsadRepository
import com.zai.mamsad.data.OrgEntity
import com.zai.mamsad.data.SortMode
import com.zai.mamsad.api.WpPost
import com.zai.mamsad.api.WpReview
import com.zai.mamsad.api.WpTerm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared view-model across catalog / filters / detail screens.
 * Holds the single source of truth for filters and the cached catalog.
 */
class CatalogViewModel(
    private val repo: MamsadRepository
) : ViewModel() {

    // Catalog filter state — shared between Filters screen and Catalog screen
    private val _filter = MutableStateFlow(CatalogFilter())
    val filter: StateFlow<CatalogFilter> = _filter.asStateFlow()

    // All orgs in cache (observable from Room)
    private val allOrgs: StateFlow<List<OrgEntity>> =
        repo.observeAll().stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )

    // Filtered catalog
    val filteredOrgs: StateFlow<List<OrgEntity>> =
        combine(allOrgs, _filter) { items, f -> repo.applyFilters(items, f) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Favorites
    val favorites: StateFlow<List<OrgEntity>> =
        repo.observeFavorites().stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )

    // Cities / types / categories (for filter chips)
    private val _cities = MutableStateFlow<List<WpTerm>>(emptyList())
    val cities: StateFlow<List<WpTerm>> = _cities.asStateFlow()

    private val _types = MutableStateFlow<List<WpTerm>>(emptyList())
    val types: StateFlow<List<WpTerm>> = _types.asStateFlow()

    private val _categories = MutableStateFlow<List<WpTerm>>(emptyList())
    val categories: StateFlow<List<WpTerm>> = _categories.asStateFlow()

    // Loading / error states for refresh
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshError = MutableStateFlow<String?>(null)
    val refreshError: StateFlow<String?> = _refreshError.asStateFlow()

    private val _hasInitialData = MutableStateFlow(false)
    val hasInitialData: StateFlow<Boolean> = _hasInitialData.asStateFlow()

    // Reviews state — shared by DetailFragment
    private val _reviews = MutableStateFlow<List<WpReview>>(emptyList())
    val reviews: StateFlow<List<WpReview>> = _reviews.asStateFlow()

    private val _reviewsLoading = MutableStateFlow(false)
    val reviewsLoading: StateFlow<Boolean> = _reviewsLoading.asStateFlow()

    private val _reviewsError = MutableStateFlow(false)
    val reviewsError: StateFlow<Boolean> = _reviewsError.asStateFlow()

    var reviewsOrgId: Int = 0
        private set

    // ============================================================
    // Articles state — shared by ArticlesFragment
    // ============================================================
    private val _articles = MutableStateFlow<List<WpPost>>(emptyList())
    val articles: StateFlow<List<WpPost>> = _articles.asStateFlow()

    private val _articlesLoading = MutableStateFlow(false)
    val articlesLoading: StateFlow<Boolean> = _articlesLoading.asStateFlow()

    private val _articlesError = MutableStateFlow<String?>(null)
    val articlesError: StateFlow<String?> = _articlesError.asStateFlow()

    // ============================================================
    // Compare state — shared by DetailFragment + CompareFragment
    // ============================================================
    private val _compareIds = MutableStateFlow<Set<Int>>(emptySet())
    val compareIds: StateFlow<Set<Int>> = _compareIds.asStateFlow()

    val compareOrgs: StateFlow<List<OrgEntity>> =
        combine(allOrgs, _compareIds) { list, ids ->
            list.filter { it.id in ids }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        refresh()
        loadTaxonomies()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _refreshError.value = null
            val result = repo.fetchOrgs()
            result.onSuccess {
                _hasInitialData.value = true
            }.onFailure { e ->
                _refreshError.value = e.message ?: "Ошибка загрузки"
                // If we still have cached items, mark them as initial data
                if (allOrgs.value.isNotEmpty()) {
                    _hasInitialData.value = true
                }
            }
            _isRefreshing.value = false
        }
    }

    private fun loadTaxonomies() {
        viewModelScope.launch {
            repo.fetchCities().onSuccess { _cities.value = it }
            repo.fetchTypes().onSuccess { _types.value = it }
            repo.fetchCategories().onSuccess { _categories.value = it }
        }
    }

    fun loadReviews(forOrgId: Int) {
        if (reviewsOrgId == forOrgId && _reviews.value.isNotEmpty()) return
        reviewsOrgId = forOrgId
        viewModelScope.launch {
            _reviewsLoading.value = true
            _reviewsError.value = false
            val result = repo.fetchReviews()
            result.onSuccess { allReviews ->
                _reviews.value = allReviews
            }.onFailure {
                _reviewsError.value = true
            }
            _reviewsLoading.value = false
        }
    }

    fun setFilter(filter: CatalogFilter) {
        _filter.value = filter
    }

    fun setSearch(query: String) {
        _filter.value = _filter.value.copy(searchQuery = query)
    }

    fun setCity(cityId: Int?) {
        _filter.value = _filter.value.copy(cityId = cityId)
    }

    fun setType(typeId: Int?) {
        _filter.value = _filter.value.copy(typeId = typeId)
    }

    fun toggleCategory(catId: Int) {
        val current = _filter.value.categoryIds.toMutableSet()
        if (catId in current) current.remove(catId) else current.add(catId)
        _filter.value = _filter.value.copy(categoryIds = current)
    }

    fun setSort(mode: SortMode) {
        _filter.value = _filter.value.copy(sort = mode)
    }

    fun resetFilters() {
        _filter.value = CatalogFilter()
    }

    fun toggleFavorite(orgId: Int) {
        viewModelScope.launch {
            val current = repo.getById(orgId)
            current?.let { repo.setFavorite(orgId, !it.isFavorite) }
        }
    }

    fun setFavorite(orgId: Int, fav: Boolean) {
        viewModelScope.launch { repo.setFavorite(orgId, fav) }
    }

    fun getById(id: Int) = repo.observeById(id)

    // ============================================================
    // Articles
    // ============================================================
    fun loadArticles() {
        if (_articles.value.isNotEmpty()) return
        viewModelScope.launch {
            _articlesLoading.value = true
            _articlesError.value = null
            repo.fetchPosts()
                .onSuccess { _articles.value = it }
                .onFailure { _articlesError.value = it.message ?: "Ошибка загрузки статей" }
            _articlesLoading.value = false
        }
    }

    // ============================================================
    // Compare
    // ============================================================
    private val maxCompare = 4

    fun toggleCompare(orgId: Int) {
        val current = _compareIds.value.toMutableSet()
        if (orgId in current) {
            current.remove(orgId)
        } else if (current.size < maxCompare) {
            current.add(orgId)
        }
        _compareIds.value = current
    }

    fun isInCompare(orgId: Int): Boolean = orgId in _compareIds.value

    fun removeFromCompare(orgId: Int) {
        _compareIds.value = _compareIds.value - orgId
    }

    fun clearCompare() {
        _compareIds.value = emptySet()
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CatalogViewModel(MamsadApp.instance.repository) as T
            }
        }
    }
}
