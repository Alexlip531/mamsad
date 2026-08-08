package com.zai.mamsad.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrgDao {

    @Query("SELECT * FROM organizations ORDER BY date DESC")
    fun observeAll(): Flow<List<OrgEntity>>

    @Query("SELECT * FROM organizations WHERE isFavorite = 1 ORDER BY date DESC")
    fun observeFavorites(): Flow<List<OrgEntity>>

    @Query("SELECT * FROM organizations WHERE id = :id")
    suspend fun getById(id: Int): OrgEntity?

    @Query("SELECT * FROM organizations WHERE id = :id")
    fun observeById(id: Int): Flow<OrgEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OrgEntity>)

    @Query("SELECT COUNT(*) FROM organizations")
    suspend fun count(): Int

    @Query("UPDATE organizations SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Int, fav: Boolean)

    @Query("DELETE FROM organizations")
    suspend fun clearAll()
}
