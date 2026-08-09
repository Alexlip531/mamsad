package com.zai.mamsad.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit service for mamsad.ru WordPress REST API.
 * Acts as the "bridge" between the website and the app.
 */
interface MamsadApi {

    @GET("wp/v2/kg_org")
    suspend fun getOrganizations(
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("_embed") embed: Boolean = true,
        @Query("search") search: String? = null,
        @Query("kg_city") cityId: Int? = null,
        @Query("kg_org_type") typeId: Int? = null,
        @Query("kg_org_category") categoryId: Int? = null,
        @Query("orderby") orderby: String = "date",
        @Query("order") order: String = "desc"
    ): List<WpOrg>

    @GET("wp/v2/kg_city")
    suspend fun getCities(@Query("per_page") perPage: Int = 50): List<WpTerm>

    @GET("wp/v2/kg_org_type")
    suspend fun getOrgTypes(@Query("per_page") perPage: Int = 50): List<WpTerm>

    @GET("wp/v2/kg_org_category")
    suspend fun getOrgCategories(@Query("per_page") perPage: Int = 100): List<WpTerm>

    /** Reviews. Optionally filter by org via parent post (kg_org). */
    @GET("wp/v2/kg_review")
    suspend fun getReviews(
        @Query("per_page") perPage: Int = 100,
        @Query("_embed") embed: Boolean = true,
        @Query("search") search: String? = null
    ): List<WpReview>

    /** Articles (posts) — used for the «Статьи для мам» screen. */
    @GET("wp/v2/posts")
    suspend fun getPosts(
        @Query("per_page") perPage: Int = 20,
        @Query("page") page: Int = 1,
        @Query("_embed") embed: Boolean = true,
        @Query("search") search: String? = null
    ): List<WpPost>

    /**
     * Raw HTML of an org page (used to scrape JSON-LD: geo, address, price, rating).
     * Retrofit returns the response body as a String.
     */
    @GET
    suspend fun getOrgHtml(@Url url: String): String

    /**
     * Admin overrides JSON stored in the GitHub repo.
     * Returns the raw JSON string from raw.githubusercontent.com.
     */
    @GET
    suspend fun getOverridesJson(@Url url: String): String
}

@JsonClass(generateAdapter = true)
data class WpReview(
    val id: Int,
    val date: String,
    val slug: String,
    val link: String,
    val title: RenderedText,
    val content: RenderedText,
    val excerpt: RenderedText,
    @Json(name = "_embedded") val embedded: WpEmbedded? = null
)

/**
 * Article (post) DTO from mamsad.ru WP REST API.
 * Endpoint: /wp-json/wp/v2/posts
 *
 * Used for the «Статьи для мам» screen.
 */
@JsonClass(generateAdapter = true)
data class WpPost(
    val id: Int,
    val date: String,
    val slug: String,
    val link: String,
    val title: RenderedText,
    val content: RenderedText,
    val excerpt: RenderedText,
    @Json(name = "_embedded") val embedded: WpEmbedded? = null
)
