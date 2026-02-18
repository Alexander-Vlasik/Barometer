package com.barometer.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import com.barometer.ProcessSessionTracker
import com.barometer.R
import com.barometer.data.AppEventType
import com.barometer.data.GraphProvider
import com.barometer.data.SampleMode
import com.barometer.data.SampleResult
import com.barometer.data.db.AppEventEntity
import com.barometer.data.db.PressureSampleEntity
import com.barometer.data.db.SampleDiagnosticsEntity
import com.barometer.sensor.PressureReadOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class PressureSampleWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val graph = GraphProvider.get(applicationContext)
        val useFgs = inputData.getBoolean(WorkScheduler.KEY_USE_FGS, false)
        val startedAtElapsed = SystemClock.elapsedRealtime()
        val runtimeState = captureRuntimeState(applicationContext)
        val workerRunId = id.toString()

        graph.pressureRepository.insertEvent(
            AppEventEntity(
                timestampUtcMillis = System.currentTimeMillis(),
                type = AppEventType.WORKER_START.name,
                detail = "worker_started",
            ),
        )

        if (ProcessSessionTracker.markWorkerStartAndIsColdStartByWorker()) {
            graph.pressureRepository.insertEvent(
                AppEventEntity(
                    timestampUtcMillis = System.currentTimeMillis(),
                    type = AppEventType.PROCESS_COLD_START_BY_WORKER.name,
                    detail = "process_started_for_worker",
                ),
            )
        }

        if (useFgs) {
            setForeground(createForegroundInfo())
        }

        try {
            val result = graph.pressureReader.readPressureSnapshot(timeoutMs = 2_500L)
            val now = System.currentTimeMillis()

            val sample = when (result) {
                is PressureReadOutcome.Success -> PressureSampleEntity(
                    timestampUtcMillis = now,
                    pressureHpa = result.pressureHpa,
                    mode = mode(useFgs),
                    result = SampleResult.OK.name,
                )

                is PressureReadOutcome.Timeout -> PressureSampleEntity(
                    timestampUtcMillis = now,
                    pressureHpa = null,
                    mode = mode(useFgs),
                    result = SampleResult.TIMEOUT.name,
                )

                is PressureReadOutcome.NoSensor -> PressureSampleEntity(
                    timestampUtcMillis = now,
                    pressureHpa = null,
                    mode = mode(useFgs),
                    result = SampleResult.NO_SENSOR.name,
                )

                is PressureReadOutcome.Error -> PressureSampleEntity(
                    timestampUtcMillis = now,
                    pressureHpa = null,
                    mode = mode(useFgs),
                    result = SampleResult.ERROR.name,
                )
            }

            val diagnostics = when (result) {
                is PressureReadOutcome.Success -> diagnostics(
                    sampleId = 0L,
                    now = now,
                    durationMs = result.sensorReadyMs,
                    runtimeState = runtimeState,
                    stopReason = null,
                    failureClass = null,
                    failureMessage = null,
                    workerRunId = workerRunId,
                )

                is PressureReadOutcome.Timeout -> diagnostics(
                    sampleId = 0L,
                    now = now,
                    durationMs = result.sensorReadyMs,
                    runtimeState = runtimeState,
                    stopReason = null,
                    failureClass = "Timeout",
                    failureMessage = result.reason,
                    workerRunId = workerRunId,
                )

                is PressureReadOutcome.NoSensor -> diagnostics(
                    sampleId = 0L,
                    now = now,
                    durationMs = result.sensorReadyMs,
                    runtimeState = runtimeState,
                    stopReason = null,
                    failureClass = "NoSensor",
                    failureMessage = result.reason,
                    workerRunId = workerRunId,
                )

                is PressureReadOutcome.Error -> diagnostics(
                    sampleId = 0L,
                    now = now,
                    durationMs = result.sensorReadyMs,
                    runtimeState = runtimeState,
                    stopReason = null,
                    failureClass = result.failureClass,
                    failureMessage = result.failureMessage,
                    workerRunId = workerRunId,
                )
            }

            graph.pressureRepository.insertSampleWithDiagnostics(sample, diagnostics)
            return Result.success()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                val now = System.currentTimeMillis()
                val stopReason = getStopReason()
                graph.pressureRepository.insertSampleWithDiagnostics(
                    sample = PressureSampleEntity(
                        timestampUtcMillis = now,
                        pressureHpa = null,
                        mode = mode(useFgs),
                        result = SampleResult.CANCELLED.name,
                    ),
                    diagnostics = diagnostics(
                        sampleId = 0L,
                        now = now,
                        durationMs = (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(0L),
                        runtimeState = runtimeState,
                        stopReason = stopReason,
                        failureClass = cancelled::class.simpleName ?: "CancellationException",
                        failureMessage = "${cancelled.message.orEmpty()} [${stopReasonLabel(stopReason)}]",
                        workerRunId = workerRunId,
                    ),
                )
            }
            throw cancelled
        }
    }

    private fun diagnostics(
        sampleId: Long,
        now: Long,
        durationMs: Long,
        runtimeState: RuntimeState,
        stopReason: Int?,
        failureClass: String?,
        failureMessage: String?,
        workerRunId: String,
    ): SampleDiagnosticsEntity {
        return SampleDiagnosticsEntity(
            sampleId = sampleId,
            recordedAtUtcMillis = now,
            durationMs = durationMs,
            isDozeMode = runtimeState.isDozeMode,
            isPowerSaveMode = runtimeState.isPowerSaveMode,
            batteryPercent = runtimeState.batteryPercent,
            appStandbyBucket = runtimeState.appStandbyBucket,
            stopReason = stopReason,
            failureClass = failureClass,
            failureMessage = failureMessage,
            workerRunId = workerRunId,
            runAttemptCount = runAttemptCount,
        )
    }

    private fun captureRuntimeState(context: Context): RuntimeState {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val usageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.getSystemService(UsageStatsManager::class.java)
        } else {
            null
        }

        return RuntimeState(
            isDozeMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isDeviceIdleMode ?: false
            } else {
                false
            },
            isPowerSaveMode = powerManager?.isPowerSaveMode ?: false,
            batteryPercent = readBatteryPercent(context),
            appStandbyBucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                usageStatsManager?.appStandbyBucket
            } else {
                null
            },
        )
    }

    private fun readBatteryPercent(context: Context): Int? {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        return ((level * 100f) / scale).toInt().coerceIn(0, 100)
    }

    private fun mode(useFgs: Boolean): String {
        return if (useFgs) SampleMode.FGS.name else SampleMode.NO_FGS.name
    }

    private fun createForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(applicationContext.getString(R.string.fgs_title))
            .setContentText(applicationContext.getString(R.string.fgs_text))
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.fgs_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setSound(null, null)
            setShowBadge(false)
        }
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)
    }

    private fun stopReasonLabel(reason: Int): String {
        return when (reason) {
            WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "cancelled_by_app"
            WorkInfo.STOP_REASON_PREEMPT -> "preempt"
            WorkInfo.STOP_REASON_TIMEOUT -> "timeout"
            WorkInfo.STOP_REASON_DEVICE_STATE -> "device_state"
            WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "constraint_battery_not_low"
            WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "constraint_charging"
            WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "constraint_connectivity"
            WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "constraint_storage_not_low"
            WorkInfo.STOP_REASON_QUOTA -> "quota"
            WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "background_restriction"
            WorkInfo.STOP_REASON_APP_STANDBY -> "app_standby"
            WorkInfo.STOP_REASON_USER -> "user"
            WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "system_processing"
            WorkInfo.STOP_REASON_UNKNOWN -> "unknown"
            else -> "reason_$reason"
        }
    }

    companion object {
        private const val CHANNEL_ID = "pressure_sampling"
        private const val NOTIFICATION_ID = 7001
    }
}

private data class RuntimeState(
    val isDozeMode: Boolean,
    val isPowerSaveMode: Boolean,
    val batteryPercent: Int?,
    val appStandbyBucket: Int?,
)
