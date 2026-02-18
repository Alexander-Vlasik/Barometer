package com.barometer.data

import java.time.LocalDate

enum class SampleMode {
    FGS,
    NO_FGS,
}

enum class SampleResult {
    OK,
    TIMEOUT,
    ERROR,
    NO_SENSOR,
    CANCELLED,
}

enum class AppEventType {
    APP_START,
    WORKER_START,
    PROCESS_COLD_START_BY_WORKER,
}

data class AppEvent(
    val id: Long,
    val timestampUtcMillis: Long,
    val type: AppEventType,
    val detail: String?,
)

data class SampleDiagnostics(
    val durationMs: Long,
    val isDozeMode: Boolean,
    val isPowerSaveMode: Boolean,
    val batteryPercent: Int?,
    val appStandbyBucket: Int?,
    val stopReason: Int?,
    val failureClass: String?,
    val failureMessage: String?,
    val workerRunId: String,
    val runAttemptCount: Int,
)

data class PressureSample(
    val id: Long,
    val timestampUtcMillis: Long,
    val pressureHpa: Float?,
    val mode: SampleMode,
    val result: SampleResult,
    val diagnostics: SampleDiagnostics?,
)

data class DayKpi(
    val expectedSamples: Int,
    val samplesCount: Int,
    val coverage: Double,
    val medianGapMinutes: Double,
    val p90GapMinutes: Double,
    val maxGapMinutes: Double,
    val timeoutsCount: Int,
    val errorsCount: Int,
    val cancelledCount: Int,
    val fgsCount: Int,
    val noFgsCount: Int,
    val dozeCount: Int,
    val appStartsCount: Int,
    val workerColdStartsCount: Int,
)

data class DayData(
    val date: LocalDate,
    val samples: List<PressureSample>,
    val events: List<AppEvent>,
    val kpi: DayKpi,
)
