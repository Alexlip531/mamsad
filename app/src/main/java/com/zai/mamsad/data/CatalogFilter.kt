package com.zai.mamsad.data

/**
 * In-memory filter state for the catalog.
 */
data class CatalogFilter(
    val searchQuery: String = "",
    val cityId: Int? = null,        // null = all cities
    val typeId: Int? = null,        // null = all types
    val categoryIds: Set<Int> = emptySet(),
    val sort: SortMode = SortMode.NEW
) {
    val isActive: Boolean
        get() = searchQuery.isNotBlank() || cityId != null || typeId != null || categoryIds.isNotEmpty()

    val activeCount: Int
        get() {
            var n = 0
            if (searchQuery.isNotBlank()) n++
            if (cityId != null) n++
            if (typeId != null) n++
            if (categoryIds.isNotEmpty()) n++
            return n
        }
}

enum class SortMode { NEW, ALPHA, CITY }
