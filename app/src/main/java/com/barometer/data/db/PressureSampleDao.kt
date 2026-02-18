package com.barometer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PressureSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: PressureSampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDiagnostics(diagnostics: SampleDiagnosticsEntity)

    @Transaction
    suspend fun insertSampleWithDiagnostics(
        sample: PressureSampleEntity,
        diagnostics: SampleDiagnosticsEntity,
    ) {
        val sampleId = insertSample(sample)
        insertDiagnostics(diagnostics.copy(sampleId = sampleId))
    }

    @Query(
        """
        SELECT DISTINCT date(timestampUtcMillis / 1000, 'unixepoch', 'localtime')
        FROM pressure_samples
        ORDER BY 1 DESC
        LIMIT :limit
        """
    )
    fun getDistinctDays(limit: Int): Flow<List<String>>

    @Transaction
    @Query(
        """
        SELECT *
        FROM pressure_samples
        WHERE timestampUtcMillis >= :startUtc AND timestampUtcMillis < :endUtc
        ORDER BY timestampUtcMillis ASC
        """
    )
    fun getSamplesForDay(startUtc: Long, endUtc: Long): Flow<List<PressureSampleWithDiagnostics>>
}
