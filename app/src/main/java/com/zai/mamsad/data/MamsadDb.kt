package com.zai.mamsad.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [OrgEntity::class, AdminOverride::class],
    version = 2,
    exportSchema = false
)
abstract class MamsadDb : RoomDatabase() {
    abstract fun orgDao(): OrgDao
    abstract fun adminDao(): AdminDao

    companion object {
        @Volatile private var INSTANCE: MamsadDb? = null

        fun get(context: Context): MamsadDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MamsadDb::class.java,
                    "mamsad.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
