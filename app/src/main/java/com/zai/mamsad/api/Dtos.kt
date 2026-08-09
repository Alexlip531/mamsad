package com.zai.mamsad.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Organization (kindergarten) DTO from mamsad.ru WP REST API.
 * Endpoint: /wp-json/wp/v2/kg_org
 */
@JsonClass(generateAdapter = true)
data class WpOrg(
    val id: Int,
    val date: String,
    val slug: String,
    val link: String,
    val title: RenderedText,
    val content: RenderedText,
    val excerpt: RenderedText,
    @Json(name = "featured_media") val featuredMedia: Int = 0,
    @Json(name = "kg_city") val cityIds: List<Int> = emptyList(),
    @Json(name = "kg_org_category") val categoryIds: List<Int> = emptyList(),
    @Json(name = "kg_org_type") val typeIds: List<Int> = emptyList(),
    @Json(name = "_embedded") val embedded: WpEmbedded? = null
)

@JsonClass(generateAdapter = true)
data class RenderedText(
    val rendered: String
)

@JsonClass(generateAdapter = true)
data class WpEmbedded(
    @Json(name = "wp:featuredmedia") val featuredMedia: List<WpMedia>? = null,
    @Json(name = "wp:term") val terms: List<List<WpTerm>>? = null
)

@JsonClass(generateAdapter = true)
data class WpMedia(
    val id: Int,
    @Json(name = "source_url") val sourceUrl: String?,
    @Json(name = "alt_text") val altText: String? = null
)

@JsonClass(generateAdapter = true)
data class WpTerm(
    val id: Int,
    val name: String,
    val slug: String,
    val taxonomy: String,
    val count: Int = 0,
    val link: String? = null
)
