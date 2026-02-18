package com.barometer.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface PressureReadOutcome {
    data class Success(val pressureHpa: Float, val sensorReadyMs: Long) : PressureReadOutcome
    data class Timeout(val sensorReadyMs: Long, val reason: String) : PressureReadOutcome
    data class NoSensor(val sensorReadyMs: Long, val reason: String) : PressureReadOutcome
    data class Error(
        val sensorReadyMs: Long,
        val failureClass: String,
        val failureMessage: String?,
    ) : PressureReadOutcome
}

class PressureReader(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    suspend fun readPressureSnapshot(timeoutMs: Long = 2_500L): PressureReadOutcome {
        val startedAt = SystemClock.elapsedRealtime()
        val manager = sensorManager
            ?: return PressureReadOutcome.Error(
                sensorReadyMs = SystemClock.elapsedRealtime() - startedAt,
                failureClass = "SensorManagerUnavailable",
                failureMessage = "context.getSystemService(SENSOR_SERVICE) returned null",
            )

        val sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE)
            ?: return PressureReadOutcome.NoSensor(
                sensorReadyMs = SystemClock.elapsedRealtime() - startedAt,
                reason = "pressure_sensor_unavailable",
            )

        return try {
            val value = withTimeoutOrNull(timeoutMs) {
                awaitFirstPressureValue(manager, sensor)
            }
            val duration = SystemClock.elapsedRealtime() - startedAt
            if (value == null) {
                PressureReadOutcome.Timeout(
                    sensorReadyMs = duration,
                    reason = "sensor_event_timeout_${timeoutMs}ms",
                )
            } else {
                PressureReadOutcome.Success(value, duration)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val duration = SystemClock.elapsedRealtime() - startedAt
            PressureReadOutcome.Error(
                sensorReadyMs = duration,
                failureClass = error::class.simpleName ?: "UnknownError",
                failureMessage = error.message,
            )
        }
    }

    private suspend fun awaitFirstPressureValue(sensorManager: SensorManager, sensor: Sensor): Float {
        return suspendCancellableCoroutine { continuation ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val value = event.values.firstOrNull()
                    if (value == null) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("sensor_event_empty_values"),
                            )
                        }
                        sensorManager.unregisterListener(this)
                        return
                    }

                    if (continuation.isActive) continuation.resume(value)
                    sensorManager.unregisterListener(this)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            val registered = sensorManager.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL,
            )

            if (!registered) {
                continuation.resumeWithException(
                    IllegalStateException("register_listener_failed"),
                )
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation {
                sensorManager.unregisterListener(listener)
            }
        }
    }
}
