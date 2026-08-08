package com.zai.mamsad.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached kindergarten (organization) entity.
 * Mirrors the WpOrg DTO flattened with resolved city/type names for fast UI rendering.
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
    val date: String,           // ISO date string for sorting
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
