package com.barometer.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class PressureSampleWithDiagnostics(
    @Embedded val sample: PressureSampleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sampleId",
    )
    val diagnostics: SampleDiagnosticsEntity?,
)
