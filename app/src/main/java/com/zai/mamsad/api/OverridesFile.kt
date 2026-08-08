package com.zai.mamsad.api

import com.squareup.moshi.JsonClass

/**
 * Admin overrides fetched from a JSON file in the GitHub repo.
 * This lets the admin edit kindergarten data without touching the mamsad.ru server.
 *
 * Schema:
 *   - hidden: hide an org from the app entirely
 *   - featured: pin to top of catalog
 *   - titleOverride: replace title
 *   - excerptOverride: replace excerpt
 *   - contentOverride: replace description
 *   - lat/lngOverride: correct coordinates
 *   - addressOverride: correct address
 *   - priceOverride: correct price
 *   - customTags: extra tags to show on detail screen
 *   - extraOrgs: brand new orgs to add (not on mamsad.ru)
 */
@JsonClass(generateAdapter = true)
data class OverridesFile(
    val version: Int = 1,
    val updatedAt: Long = 0,
    val editedBy: String = "",
    val orgs: List<OrgOverride> = emptyList(),
    val extraOrgs: List<ExtraOrg> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OrgOverride(
    val id: Int,
    val hidden: Boolean = false,
    val featured: Boolean = false,
    val titleOverride: String? = null,
    val excerptOverride: String? = null,
    val contentOverride: String? = null,
    val latOverride: Double? = null,
    val lngOverride: Double? = null,
    val addressOverride: String? = null,
    val priceOverride: String? = null,
    val ratingOverride: Float? = null,
    val customTags: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ExtraOrg(
    val id: Int,                    // negative number to avoid clash with WP ids
    val title: String,
    val excerpt: String,
    val content: String,
    val cityName: String,
    val typeName: String,
    val lat: Double,
    val lng: Double,
    val address: String,
    val priceFrom: String,
    val rating: Float,
    val phone: String = "",
    val site: String = "",
    val customTags: List<String> = emptyList()
)
