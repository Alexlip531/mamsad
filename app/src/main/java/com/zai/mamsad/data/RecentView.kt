package com.zai.mamsad.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Recently viewed kindergarten record.
 *
 * One row per orgId. updatedAt is the timestamp of the last view —
 * bumped on every open of the detail card. The home screen shows
 * the top 5 by updatedAt DESC as "Недавно просмотренные".
 */
@Entity(tableName = "recent_views")
data class RecentView(
    @PrimaryKey val orgId: Int,
    val viewedAt: Long = System.currentTimeMillis()
)

@Dao
interface RecentDao {

    @Query("SELECT * FROM recent_views ORDER BY viewedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<RecentView>>

    @Query("SELECT * FROM recent_views ORDER BY viewedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<RecentView>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(view: RecentView)

    @Query("DELETE FROM recent_views WHERE orgId = :orgId")
    suspend fun delete(orgId: Int)

    @Query("DELETE FROM recent_views")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM recent_views")
    suspend fun count(): Int
}
