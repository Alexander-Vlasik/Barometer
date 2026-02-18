package com.barometer.data

import android.content.Context
import com.barometer.data.db.AppDatabase
import com.barometer.sensor.PressureReader
import com.barometer.settings.SettingsRepository

class AppGraph(context: Context) {
    private val appContext = context.applicationContext
    private val db by lazy { AppDatabase.getInstance(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val pressureRepository: PressureRepository by lazy {
        PressureRepository(
            sampleDao = db.pressureSampleDao(),
            appEventDao = db.appEventDao(),
        )
    }
    val pressureReader: PressureReader by lazy { PressureReader(appContext) }
}

object GraphProvider {
    @Volatile
    private var graph: AppGraph? = null

    fun get(context: Context): AppGraph {
        return graph ?: synchronized(this) {
            graph ?: AppGraph(context).also { graph = it }
        }
    }
}
