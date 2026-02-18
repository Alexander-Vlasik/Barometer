package com.barometer.data

import com.barometer.data.db.AppEventDao
import com.barometer.data.db.AppEventEntity
import com.barometer.data.db.PressureSampleDao
import com.barometer.data.db.PressureSampleEntity
import com.barometer.data.db.PressureSampleWithDiagnostics
import com.barometer.data.db.SampleDiagnosticsEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val EXPECTED_SAMPLES_PER_DAY = 96

class PressureRepository(
    private val sampleDao: PressureSampleDao,
    private val appEventDao: AppEventDao,
) {
    fun getDistinctDays(limit: Int): Flow<List<LocalDate>> {
        return sampleDao.getDistinctDays(limit).map { dayStrings ->
            dayStrings.mapNotNull { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
        }
    }

    fun getSamplesForDay(date: LocalDate): Flow<List<PressureSample>> {
        val (startUtc, endUtc) = dayBoundsUtc(date)
        return sampleDao.getSamplesForDay(startUtc, endUtc).map { rows -> rows.map { it.toDomain() } }
    }

    fun getEventsForDay(date: LocalDate): Flow<List<AppEvent>> {
        val (startUtc, endUtc) = dayBoundsUtc(date)
        return appEventDao.getEventsForDay(startUtc, endUtc).map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun insertSampleWithDiagnostics(
        sample: PressureSampleEntity,
        diagnostics: SampleDiagnosticsEntity,
    ) {
        sampleDao.insertSampleWithDiagnostics(sample, diagnostics)
    }

    suspend fun insertEvent(event: AppEventEntity) {
        appEventDao.insert(event)
    }

    fun buildKpi(samples: List<PressureSample>, events: List<AppEvent>): DayKpi {
        val sorted = samples.sortedBy { it.timestampUtcMillis }
        val gapsMinutes = sorted.zipWithNext { a, b ->
            (b.timestampUtcMillis - a.timestampUtcMillis).coerceAtLeast(0) / 60_000.0
        }

        return DayKpi(
            expectedSamples = EXPECTED_SAMPLES_PER_DAY,
            samplesCount = samples.size,
            coverage = samples.size.toDouble() / EXPECTED_SAMPLES_PER_DAY,
            medianGapMinutes = percentile(gapsMinutes, 0.5),
            p90GapMinutes = percentile(gapsMinutes, 0.9),
            maxGapMinutes = gapsMinutes.maxOrNull() ?: 0.0,
            timeoutsCount = samples.count { it.result == SampleResult.TIMEOUT },
            errorsCount = samples.count { it.result == SampleResult.ERROR },
            cancelledCount = samples.count { it.result == SampleResult.CANCELLED },
            fgsCount = samples.count { it.mode == SampleMode.FGS },
            noFgsCount = samples.count { it.mode == SampleMode.NO_FGS },
            dozeCount = samples.count { it.diagnostics?.isDozeMode == true },
            appStartsCount = events.count { it.type == AppEventType.APP_START },
            workerColdStartsCount = events.count { it.type == AppEventType.PROCESS_COLD_START_BY_WORKER },
        )
    }

    companion object {
        fun dayBoundsUtc(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
            val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            return start to end
        }

        fun localDateFromUtcMillis(timestampUtcMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
            return Instant.ofEpochMilli(timestampUtcMillis).atZone(zoneId).toLocalDate()
        }

        private fun percentile(values: List<Double>, p: Double): Double {
            if (values.isEmpty()) return 0.0
            val sorted = values.sorted()
            val index = ((sorted.lastIndex) * p).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }
    }
}

private fun PressureSampleWithDiagnostics.toDomain(): PressureSample {
    return PressureSample(
        id = sample.id,
        timestampUtcMillis = sample.timestampUtcMillis,
        pressureHpa = sample.pressureHpa,
        mode = SampleMode.valueOf(sample.mode),
        result = SampleResult.valueOf(sample.result),
        diagnostics = diagnostics?.toDomain(),
    )
}

private fun SampleDiagnosticsEntity.toDomain(): SampleDiagnostics {
    return SampleDiagnostics(
        durationMs = durationMs,
        isDozeMode = isDozeMode,
        isPowerSaveMode = isPowerSaveMode,
        batteryPercent = batteryPercent,
        appStandbyBucket = appStandbyBucket,
        stopReason = stopReason,
        failureClass = failureClass,
        failureMessage = failureMessage,
        workerRunId = workerRunId,
        runAttemptCount = runAttemptCount,
    )
}

private fun AppEventEntity.toDomain(): AppEvent {
    return AppEvent(
        id = id,
        timestampUtcMillis = timestampUtcMillis,
        type = AppEventType.valueOf(type),
        detail = detail,
    )
}
