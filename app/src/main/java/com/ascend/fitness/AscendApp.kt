package com.ascend.fitness

import android.app.Application
import com.ascend.fitness.data.AppDatabase
import com.ascend.fitness.data.FinishResult
import com.ascend.fitness.data.Repository
import com.ascend.fitness.data.SettingsStore

class AscendApp : Application() {
    lateinit var repo: Repository
        private set

    /** Result of the last finished workout, shown once on the summary screen. */
    var lastFinish: FinishResult? = null

    override fun onCreate() {
        super.onCreate()
        repo = Repository(AppDatabase.build(this), SettingsStore(this)) {
            assets.open("exercises.json").bufferedReader().use { it.readText() }
        }
    }
}
