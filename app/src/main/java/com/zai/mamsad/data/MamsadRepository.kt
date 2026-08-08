package com.zai.mamsad.data

import com.squareup.moshi.Moshi
import com.zai.mamsad.api.GeoHtmlParser
import com.zai.mamsad.api.MamsadApi
import com.zai.mamsad.api.NetworkClient
import com.zai.mamsad.api.OrgGeoInfo
import com.zai.mamsad.api.OverridesFile
import com.zai.mamsad.api.WpOrg
import com.zai.mamsad.api.WpReview
import com.zai.mamsad.api.WpTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Repository: bridge between mamsad.ru REST API and local Room cache.
 *
 * Strategy:
 *   - fetchOrgs(): pull fresh list from API, scrape each org's HTML page for geo/address/price/rating
 *     (in parallel), merge admin overrides, persist to Room (favorites preserved).
 *   - applyFilters(): purely local — filter cached entities by the given CatalogFilter.
 *   - fetchReviews(): pull reviews from /wp/v2/kg_review.
 *   - fetchOverrides(): pull admin overrides JSON from GitHub raw URL.
 *   - Favorites live entirely in DB (isFavorite flag on OrgEntity).
 */
class MamsadRepository(
    private val api: MamsadApi = NetworkClient.api,
    private val dao: OrgDao,
    private val overridesUrl: String = DEFAULT_OVERRIDES_URL
) {

    fun observeAll(): Flow<List<OrgEntity>> = dao.observeAll()
    fun observeFavorites(): Flow<List<OrgEntity>> = dao.observeFavorites()
    fun observeById(id: Int): Flow<OrgEntity?> = dao.observeById(id)

    suspend fun fetchOrgs(): Result<List<OrgEntity>> = runCatching {
        // 1. Fetch all orgs with embedded terms
        val wpOrgs = api.getOrganizations(perPage = 100, embed = true)

        // 2. Fetch geo info for each org in parallel (8 at a time to be polite)
        val geoMap: Map<Int, OrgGeoInfo?> = withContext(Dispatchers.IO) {
            coroutineScope {
                wpOrgs.map { org ->
                    async {
                        try {
                            val html = api.getOrgHtml(org.link)
                            org.id to GeoHtmlParser.parse(html)
                        } catch (_: Throwable) {
                            org.id to null
                        }
                    }
                }.awaitAll().toMap()
            }
        }

        // 3. Fetch admin overrides
        val overrides = try { fetchOverrides() } catch (_: Throwable) { OverridesFile() }
        val overrideMap = overrides.orgs.associateBy { it.id }

        // 4. Merge into entities
        val entities = wpOrgs.map { wp ->
            val geo = geoMap[wp.id]
            val ov = overrideMap[wp.id]
            wp.toEntity(geo, ov)
        }
        // Filter out hidden orgs
        val visible = entities.filter { !it.hidden }

        // 5. Add extra orgs from overrides
        val extras = overrides.extraOrgs.map { it.toEntity() }

        // 6. Preserve favorites across refresh
        val existingFavs = dao.observeFavorites().firstSafe()
        val favIds = existingFavs.map { it.id }.toSet()
        val withFavState = (visible + extras).map { it.copy(isFavorite = it.id in favIds) }

        // 7. Persist
        dao.clearAll()
        dao.upsertAll(withFavState)
        withFavState
    }

    suspend fun fetchCities(): Result<List<WpTerm>> = runCatching { api.getCities() }
    suspend fun fetchTypes(): Result<List<WpTerm>> = runCatching { api.getOrgTypes() }
    suspend fun fetchCategories(): Result<List<WpTerm>> = runCatching { api.getOrgCategories() }

    suspend fun fetchReviews(): Result<List<WpReview>> = runCatching {
        api.getReviews(perPage = 100, embed = false)
    }

    suspend fun setFavorite(id: Int, fav: Boolean) {
        dao.setFavorite(id, fav)
    }

    suspend fun getById(id: Int): OrgEntity? = dao.getById(id)

    suspend fun count(): Int = dao.count()

    /**
     * Apply in-memory filters + sorting on cached entities.
     */
    fun applyFilters(items: List<OrgEntity>, filter: CatalogFilter): List<OrgEntity> {
        var result = items.filterNot { it.hidden }

        if (filter.searchQuery.isNotBlank()) {
            val q = filter.searchQuery.trim().lowercase()
            result = result.filter {
                it.title.lowercase().contains(q) ||
                    it.excerpt.lowercase().contains(q) ||
                    it.cityName.lowercase().contains(q) ||
                    it.typeName.lowercase().contains(q) ||
                    it.categoryNames.lowercase().contains(q) ||
                    it.address.lowercase().contains(q)
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

        // Always pin featured to top, then apply sort
        val (featured, rest) = result.partition { it.featured }
        val sortedRest = when (filter.sort) {
            SortMode.NEW -> rest.sortedByDescending { it.date }
            SortMode.ALPHA -> rest.sortedBy { it.title.lowercase() }
            SortMode.CITY -> rest.sortedWith(compareBy({ it.cityName }, { it.title.lowercase() }))
        }
        return featured + sortedRest
    }

    private suspend fun fetchOverrides(): OverridesFile = withContext(Dispatchers.IO) {
        try {
            val raw = api.getOverridesJson(overridesUrl)
            Moshi.Builder().build()
                .adapter(OverridesFile::class.java)
                .fromJson(raw) ?: OverridesFile()
        } catch (_: Throwable) {
            OverridesFile()
        }
    }

    private fun WpOrg.toEntity(geo: OrgGeoInfo?, override: com.zai.mamsad.api.OrgOverride?): OrgEntity {
        val terms = embedded?.terms?.flatten() ?: emptyList()
        val city = terms.firstOrNull { it.taxonomy == "kg_city" }
        val type = terms.firstOrNull { it.taxonomy == "kg_org_type" }
        val categories = terms.filter { it.taxonomy == "kg_org_category" }
        val mediaUrl = embedded?.featuredMedia?.firstOrNull()?.sourceUrl
        return OrgEntity(
            id = id,
            slug = slug,
            link = link,
            title = override?.titleOverride ?: title.rendered.stripHtml(),
            excerpt = override?.excerptOverride ?: excerpt.rendered.stripHtml(),
            content = override?.contentOverride ?: content.rendered,
            cityId = city?.id ?: cityIds.firstOrNull() ?: 0,
            cityName = city?.name ?: "",
            typeId = type?.id ?: typeIds.firstOrNull() ?: 0,
            typeName = type?.name ?: "",
            categoryIds = categories.joinToString(",") { it.id.toString() },
            categoryNames = categories.joinToString(", ") { it.name },
            imageUrl = mediaUrl,
            date = date,
            lat = override?.latOverride ?: geo?.lat,
            lng = override?.lngOverride ?: geo?.lng,
            address = override?.addressOverride ?: geo?.address ?: "",
            priceFrom = override?.priceOverride ?: geo?.priceFrom ?: "",
            rating = override?.ratingOverride ?: geo?.rating,
            reviewCount = geo?.reviewCount ?: 0,
            featured = override?.featured ?: false,
            hidden = override?.hidden ?: false
        )
    }

    private fun com.zai.mamsad.api.ExtraOrg.toEntity(): OrgEntity {
        val customCatIds = customTags.joinToString(",") { (-it.hashCode()).toString() }
        return OrgEntity(
            id = id,
            slug = "custom-$id",
            link = site.ifBlank { "https://mamsad.ru/" },
            title = title,
            excerpt = excerpt,
            content = content,
            cityId = 0,
            cityName = cityName,
            typeId = 0,
            typeName = typeName,
            categoryIds = customCatIds,
            categoryNames = customTags.joinToString(", "),
            imageUrl = null,
            date = "",
            lat = lat,
            lng = lng,
            address = address,
            priceFrom = priceFrom,
            rating = rating,
            reviewCount = 0,
            featured = true,    // Pin custom orgs as featured
            hidden = false
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
            .replace("&#038;", "&")
            .trim()
    }

    private suspend fun <T> Flow<T>.firstSafe(): T = first()

    companion object {
        /**
         * Admin overrides JSON hosted in the GitHub repo.
         * The admin web panel writes to this file via GitHub API.
         */
        const val DEFAULT_OVERRIDES_URL =
            "https://raw.githubusercontent.com/Alexlip531/mamsad/main/admin/overrides.json"
    }
}
