package com.zai.mamsad

import android.app.Application
import com.zai.mamsad.data.MamsadDb
import com.zai.mamsad.data.MamsadRepository

class MamsadApp : Application() {

    val database by lazy { MamsadDb.get(this) }
    val repository by lazy { MamsadRepository(dao = database.orgDao()) }

    companion object {
        lateinit var instance: MamsadApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
