package com.zai.mamsad.api

import retrofit2.http.GET
import retrofit2.http.Query

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
}
