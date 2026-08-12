package com.zai.mamsad.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Local user vote for a kindergarten.
 *
 * Stored locally on the device (one vote per org per device).
 * The vote is purely local — mamsad.ru WP REST API has no public write
 * endpoint for unauthenticated users, so we keep votes on-device and
 * surface them in the UI as "Ваш голос" alongside the remote rating
 * scraped from the org's HTML page.
 *
 * Rating range: 1..5 (integer stars). 0 is reserved as "no vote yet".
 */
@Entity(tableName = "votes")
data class Vote(
    @PrimaryKey val orgId: Int,
    val rating: Int,                  // 1..5
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface VoteDao {

    @Query("SELECT * FROM votes WHERE orgId = :orgId")
    fun observeByOrg(orgId: Int): Flow<Vote?>

    @Query("SELECT * FROM votes")
    fun observeAll(): Flow<List<Vote>>

    @Query("SELECT * FROM votes WHERE orgId = :orgId")
    suspend fun getByOrg(orgId: Int): Vote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(vote: Vote)

    @Query("DELETE FROM votes WHERE orgId = :orgId")
    suspend fun delete(orgId: Int)

    @Query("SELECT COUNT(*) FROM votes")
    suspend fun count(): Int

    @Query("SELECT AVG(rating) FROM votes")
    suspend fun averageRating(): Float?
}
