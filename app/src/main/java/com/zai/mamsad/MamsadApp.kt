package com.zai.mamsad

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zai.mamsad.data.MamsadDb
import com.zai.mamsad.data.MamsadRepository
import com.zai.mamsad.work.RefreshWorker
import java.util.concurrent.TimeUnit

class MamsadApp : Application() {

    val database by lazy { MamsadDb.get(this) }
    val repository by lazy {
        MamsadRepository(
            dao = database.orgDao(),
            adminDao = database.adminDao(),
            voteDao = database.voteDao(),
            recentDao = database.recentDao()
        )
    }

    companion object {
        lateinit var instance: MamsadApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        scheduleBackgroundRefresh()
    }

    private fun scheduleBackgroundRefresh() {
        // Every 6 hours, refresh cache + notify about new kindergartens
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(15, TimeUnit.MINUTES)  // First run after 15 min
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            RefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
