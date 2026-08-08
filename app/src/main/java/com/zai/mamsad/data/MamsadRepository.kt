package com.zai.mamsad.data

import com.zai.mamsad.api.MamsadApi
import com.zai.mamsad.api.NetworkClient
import com.zai.mamsad.api.WpOrg
import com.zai.mamsad.api.WpTerm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repository: bridge between mamsad.ru REST API and local Room cache.
 *
 * Strategy:
 *   - fetchOrgs(): pull fresh list from API, replace local cache (favorites preserved),
 *     return cached list.
 *   - applyFilters(): purely local — filter cached entities by the given CatalogFilter.
 *   - Favorites live entirely in DB (isFavorite flag on OrgEntity).
 */
class MamsadRepository(
    private val api: MamsadApi = NetworkClient.api,
    private val dao: OrgDao
) {

    fun observeAll(): Flow<List<OrgEntity>> = dao.observeAll()
    fun observeFavorites(): Flow<List<OrgEntity>> = dao.observeFavorites()
    fun observeById(id: Int): Flow<OrgEntity?> = dao.observeById(id)

    suspend fun fetchOrgs(): Result<List<OrgEntity>> = runCatching {
        // Fetch all orgs with embedded terms in one request
        val wpOrgs = api.getOrganizations(perPage = 100, embed = true)
        val entities = wpOrgs.map { it.toEntity() }
        // Preserve favorites across refresh
        val existingFavs = dao.observeFavorites().firstSafe()
        val favIds = existingFavs.map { it.id }.toSet()
        val withFavState = entities.map { it.copy(isFavorite = it.id in favIds) }
        dao.clearAll()
        dao.upsertAll(withFavState)
        withFavState
    }

    suspend fun fetchCities(): Result<List<WpTerm>> = runCatching { api.getCities() }
    suspend fun fetchTypes(): Result<List<WpTerm>> = runCatching { api.getOrgTypes() }
    suspend fun fetchCategories(): Result<List<WpTerm>> = runCatching { api.getOrgCategories() }

    suspend fun setFavorite(id: Int, fav: Boolean) {
        dao.setFavorite(id, fav)
    }

    suspend fun getById(id: Int): OrgEntity? = dao.getById(id)

    /**
     * Apply in-memory filters + sorting on cached entities.
     */
    fun applyFilters(items: List<OrgEntity>, filter: CatalogFilter): List<OrgEntity> {
        var result = items

        if (filter.searchQuery.isNotBlank()) {
            val q = filter.searchQuery.trim().lowercase()
            result = result.filter {
                it.title.lowercase().contains(q) ||
                    it.excerpt.lowercase().contains(q) ||
                    it.cityName.lowercase().contains(q) ||
                    it.typeName.lowercase().contains(q) ||
                    it.categoryNames.lowercase().contains(q)
            }
        }

        if (filter.cityId != null) {
            result = result.filter { it.cityId == filter.cityId }
        }

        if (filter.typeId != null) {
            result = result.filter { it.typeId == filter.typeId }
        }

        if (filter.categoryIds.isNotEmpty()) {
            result = result.filter { entity ->
                entity.categoryIds.split(",").mapNotNull { it.trim().toIntOrNull() }
                    .any { it in filter.categoryIds }
            }
        }

        return when (filter.sort) {
            SortMode.NEW -> result.sortedByDescending { it.date }
            SortMode.ALPHA -> result.sortedBy { it.title.lowercase() }
            SortMode.CITY -> result.sortedWith(
                compareBy({ it.cityName }, { it.title.lowercase() })
            )
        }
    }

    private fun WpOrg.toEntity(): OrgEntity {
        val terms = embedded?.terms?.flatten() ?: emptyList()
        val city = terms.firstOrNull { it.taxonomy == "kg_city" }
        val type = terms.firstOrNull { it.taxonomy == "kg_org_type" }
        val categories = terms.filter { it.taxonomy == "kg_org_category" }
        val mediaUrl = embedded?.featuredMedia?.firstOrNull()?.sourceUrl
        return OrgEntity(
            id = id,
            slug = slug,
            link = link,
            title = title.rendered.stripHtml(),
            excerpt = excerpt.rendered.stripHtml(),
            content = content.rendered,
            cityId = city?.id ?: cityIds.firstOrNull() ?: 0,
            cityName = city?.name ?: "",
            typeId = type?.id ?: typeIds.firstOrNull() ?: 0,
            typeName = type?.name ?: "",
            categoryIds = categories.joinToString(",") { it.id.toString() },
            categoryNames = categories.joinToString(", ") { it.name },
            imageUrl = mediaUrl,
            date = date
        )
    }

    private fun String.stripHtml(): String {
        return replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    private suspend fun <T> Flow<T>.firstSafe(): T = first()
}
