package com.barometer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PressureSampleEntity::class, SampleDiagnosticsEntity::class, AppEventEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pressureSampleDao(): PressureSampleDao
    abstract fun appEventDao(): AppEventDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barometer.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .addMigrations(MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pressure_samples ADD COLUMN errorDetail TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestampUtcMillis INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        detail TEXT
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sample_diagnostics_legacy (
                        sampleId INTEGER NOT NULL PRIMARY KEY,
                        recordedAtUtcMillis INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        failureMessage TEXT
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    INSERT INTO sample_diagnostics_legacy (sampleId, recordedAtUtcMillis, durationMs, failureMessage)
                    SELECT id, timestampUtcMillis, durationMs, errorDetail
                    FROM pressure_samples
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pressure_samples_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        timestampUtcMillis INTEGER NOT NULL,
                        pressureHpa REAL,
                        mode TEXT NOT NULL,
                        result TEXT NOT NULL
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    INSERT INTO pressure_samples_new (id, timestampUtcMillis, pressureHpa, mode, result)
                    SELECT id, timestampUtcMillis, pressureHpa, mode, result
                    FROM pressure_samples
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    DROP TABLE pressure_samples
                    """.trimIndent(),
                )
                db.execSQL("ALTER TABLE pressure_samples_new RENAME TO pressure_samples")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sample_diagnostics (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sampleId INTEGER NOT NULL,
                        recordedAtUtcMillis INTEGER NOT NULL,
                        durationMs INTEGER NOT NULL,
                        isDozeMode INTEGER NOT NULL,
                        isPowerSaveMode INTEGER NOT NULL,
                        batteryPercent INTEGER,
                        appStandbyBucket INTEGER,
                        stopReason INTEGER,
                        failureClass TEXT,
                        failureMessage TEXT,
                        workerRunId TEXT NOT NULL,
                        runAttemptCount INTEGER NOT NULL,
                        FOREIGN KEY(sampleId) REFERENCES pressure_samples(id) ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_sample_diagnostics_sampleId
                    ON sample_diagnostics(sampleId)
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    INSERT INTO sample_diagnostics (
                        sampleId,
                        recordedAtUtcMillis,
                        durationMs,
                        isDozeMode,
                        isPowerSaveMode,
                        batteryPercent,
                        appStandbyBucket,
                        stopReason,
                        failureClass,
                        failureMessage,
                        workerRunId,
                        runAttemptCount
                    )
                    SELECT
                        sampleId,
                        recordedAtUtcMillis,
                        durationMs,
                        0,
                        0,
                        NULL,
                        NULL,
                        NULL,
                        CASE WHEN failureMessage IS NULL THEN NULL ELSE 'legacy' END,
                        failureMessage,
                        'legacy_migration',
                        0
                    FROM sample_diagnostics_legacy
                    """.trimIndent(),
                )

                db.execSQL("DROP TABLE sample_diagnostics_legacy")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sample_diagnostics ADD COLUMN batteryPercent INTEGER")
            }
        }
    }
}
