package com.zai.mamsad.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached kindergarten (organization) entity.
 * Mirrors the WpOrg DTO flattened with resolved city/type names for fast UI rendering.
 * Geo, address, price and rating are scraped from the org's HTML page (JSON-LD),
 * because the WP REST API does not expose them directly.
 */
@Entity(tableName = "organizations")
data class OrgEntity(
    @PrimaryKey val id: Int,
    val slug: String,
    val link: String,
    val title: String,
    val excerpt: String,
    val content: String,
    val cityId: Int,
    val cityName: String,
    val typeId: Int,
    val typeName: String,
    val categoryIds: String,    // comma-separated
    val categoryNames: String,  // comma-separated
    val imageUrl: String?,
    val date: String,
    // Geo / address / price / rating (scraped from JSON-LD on the org's HTML page)
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String = "",
    val priceFrom: String = "",      // e.g. "35000" RUB/month
    val rating: Float? = null,        // 0..5
    val reviewCount: Int = 0,
    // Admin overrides
    val featured: Boolean = false,
    val hidden: Boolean = false,
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
