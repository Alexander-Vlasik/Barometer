package com.barometer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sample_diagnostics",
    foreignKeys = [
        ForeignKey(
            entity = PressureSampleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sampleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sampleId"], unique = true)],
)
data class SampleDiagnosticsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sampleId: Long,
    val recordedAtUtcMillis: Long,
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
