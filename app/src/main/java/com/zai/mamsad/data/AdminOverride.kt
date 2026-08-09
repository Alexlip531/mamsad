package com.zai.mamsad.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Local admin override for an organization.
 *
 * Stored locally in Room on the device. These overrides take priority over
 * both mamsad.ru source data AND remote GitHub overrides.json — because the
 * admin on this phone is the most recent editor.
 *
 * Only non-null fields are applied. Null means "use the original value".
 */
@Entity(tableName = "admin_overrides")
data class AdminOverride(
    @PrimaryKey val orgId: Int,
    val title: String? = null,
    val excerpt: String? = null,
    val content: String? = null,
    val address: String? = null,
    val priceFrom: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val rating: Float? = null,
    val featured: Boolean? = null,
    val hidden: Boolean? = null,
    val customTags: String? = null,   // comma-separated
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface AdminDao {

    @Query("SELECT * FROM admin_overrides ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AdminOverride>>

    @Query("SELECT * FROM admin_overrides")
    suspend fun getAll(): List<AdminOverride>

    @Query("SELECT * FROM admin_overrides WHERE orgId = :orgId")
    suspend fun getById(orgId: Int): AdminOverride?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: AdminOverride)

    @Query("DELETE FROM admin_overrides WHERE orgId = :orgId")
    suspend fun delete(orgId: Int)

    @Query("DELETE FROM admin_overrides")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM admin_overrides")
    suspend fun count(): Int
}
