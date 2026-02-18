package com.barometer.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object WorkScheduler {
    const val WORK_NAME = "PRESSURE_PERIODIC_WORK"
    const val KEY_USE_FGS = "use_fgs"

    fun schedule(context: Context, useFgs: Boolean) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

        val request = PeriodicWorkRequestBuilder<PressureSampleWorker>(15, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_USE_FGS to useFgs))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
