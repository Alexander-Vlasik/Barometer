package com.barometer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_events")
data class AppEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampUtcMillis: Long,
    val type: String,
    val detail: String?,
)
