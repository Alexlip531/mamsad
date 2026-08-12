package com.zai.mamsad.data

import com.squareup.moshi.Moshi
import com.zai.mamsad.api.GeoHtmlParser
import com.zai.mamsad.api.MamsadApi
import com.zai.mamsad.api.NetworkClient
import com.zai.mamsad.api.OrgGeoInfo
import com.zai.mamsad.api.OverridesFile
import com.zai.mamsad.api.WpOrg
import com.zai.mamsad.api.WpPost
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
    private val adminDao: AdminDao,
    private val voteDao: VoteDao,
    private val recentDao: RecentDao,
    private val overridesUrl: String = DEFAULT_OVERRIDES_URL
) {

    fun observeAll(): Flow<List<OrgEntity>> = dao.observeAll()
    fun observeFavorites(): Flow<List<OrgEntity>> = dao.observeFavorites()
    fun observeById(id: Int): Flow<OrgEntity?> = dao.observeById(id)

    fun observeAdminOverrides(): Flow<List<AdminOverride>> = adminDao.observeAll()

    // ============================================================
    // Votes (local, per-device)
    // ============================================================
    fun observeVote(orgId: Int): Flow<Vote?> = voteDao.observeByOrg(orgId)
    fun observeAllVotes(): Flow<List<Vote>> = voteDao.observeAll()

    suspend fun saveVote(orgId: Int, rating: Int) {
        require(rating in 1..5) { "rating must be 1..5, got $rating" }
        voteDao.upsert(Vote(orgId = orgId, rating = rating))
    }

    suspend fun deleteVote(orgId: Int) {
        voteDao.delete(orgId)
    }

    suspend fun getVote(orgId: Int): Vote? = voteDao.getByOrg(orgId)

    /**
     * Aggregated local vote stats — used by the detail screen to show
     * "На основе N голосов наших пользователей".
     */
    suspend fun voteStats(): VoteStats {
        val all = voteDao.observeAll().firstSafe()
        val n = all.size
        val avg = if (n == 0) 0f else all.map { it.rating }.average().toFloat()
        return VoteStats(count = n, average = avg)
    }

    // ============================================================
    // Recently viewed (local, per-device)
    // ============================================================
    fun observeRecentIds(limit: Int = 10): Flow<List<RecentView>> =
        recentDao.observeRecent(limit)

    /**
     * Mark an org as recently viewed. Bumps its viewedAt to now.
     * Safe to call on every detail open.
     */
    suspend fun markViewed(orgId: Int) {
        recentDao.upsert(RecentView(orgId = orgId, viewedAt = System.currentTimeMillis()))
    }

    suspend fun clearRecent() {
        recentDao.clearAll()
    }

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

        // 3. Fetch remote admin overrides (GitHub raw)
        val overrides = try { fetchOverrides() } catch (_: Throwable) { OverridesFile() }
        val overrideMap = overrides.orgs.associateBy { it.id }

        // 3b. Fetch LOCAL admin overrides (this device's admin edits — top priority)
        val localOverrides = adminDao.getAll().associateBy { it.orgId }

        // 4. Merge into entities
        val entities = wpOrgs.map { wp ->
            val geo = geoMap[wp.id]
            val remoteOv = overrideMap[wp.id]
            val localOv = localOverrides[wp.id]
            wp.toEntity(geo, remoteOv).applyLocalOverride(localOv)
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

    suspend fun fetchPosts(): Result<List<WpPost>> = runCatching {
        api.getPosts(perPage = 20, embed = true)
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
            SortMode.RATING -> rest.sortedByDescending { it.rating ?: 0f }
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
            ageGroups = geo?.ageGroups ?: "",
            phone = geo?.phone,
            galleryUrls = geo?.galleryUrls?.joinToString(",") ?: "",
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

    /**
     * Apply a local admin override (from this device) on top of an entity.
     * Local override has the highest priority — it wins over both mamsad.ru
     * source data and remote GitHub overrides.json.
     */
    private fun OrgEntity.applyLocalOverride(local: AdminOverride?): OrgEntity {
        if (local == null) return this
        return copy(
            title = local.title ?: title,
            excerpt = local.excerpt ?: excerpt,
            content = local.content ?: content,
            address = local.address ?: address,
            priceFrom = local.priceFrom ?: priceFrom,
            lat = local.lat ?: lat,
            lng = local.lng ?: lng,
            rating = local.rating ?: rating,
            featured = local.featured ?: featured,
            hidden = local.hidden ?: hidden
        )
    }

    // ============================================================
    // Local admin CRUD — used by AdminFragment / AdminEditFragment
    // ============================================================

    suspend fun saveAdminOverride(override: AdminOverride) {
        adminDao.upsert(override)
        // Re-apply: reload from API would be wasteful — just patch the cached entity
        val cached = dao.getById(override.orgId) ?: return
        dao.upsertAll(listOf(cached.run {
            copy(
                title = override.title ?: title,
                excerpt = override.excerpt ?: excerpt,
                content = override.content ?: content,
                address = override.address ?: address,
                priceFrom = override.priceFrom ?: priceFrom,
                lat = override.lat ?: lat,
                lng = override.lng ?: lng,
                rating = override.rating ?: rating,
                featured = override.featured ?: featured,
                hidden = override.hidden ?: hidden,
                updatedAt = System.currentTimeMillis()
            )
        }))
    }

    suspend fun deleteAdminOverride(orgId: Int) {
        adminDao.delete(orgId)
        // After removing override, the cached entity keeps the patched values
        // until the next refresh. That's acceptable — admin gets a snackbar
        // saying "refresh catalog to restore original values".
    }

    suspend fun getAdminOverride(orgId: Int): AdminOverride? = adminDao.getById(orgId)

    suspend fun clearAllAdminOverrides() {
        adminDao.clearAll()
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

/**
 * Aggregated local vote stats for the whole catalog.
 */
data class VoteStats(
    val count: Int,
    val average: Float
)
