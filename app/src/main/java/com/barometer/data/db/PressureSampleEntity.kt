package com.barometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pressure_samples")
data class PressureSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtcMillis: Long,
    val pressureHpa: Float?,
    val mode: String,
    val result: String,
)
