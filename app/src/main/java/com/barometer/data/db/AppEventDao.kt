package com.barometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AppEventEntity)

    @Query(
        """
        SELECT *
        FROM app_events
        WHERE timestampUtcMillis >= :startUtc AND timestampUtcMillis < :endUtc
        ORDER BY timestampUtcMillis ASC
        """
    )
    fun getEventsForDay(startUtc: Long, endUtc: Long): Flow<List<AppEventEntity>>
}
