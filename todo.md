# TODO -- Pressure Sampling App

## Goal

Build an Android application (no runtime permissions) that:

-   Reads barometer (TYPE_PRESSURE) value
-   Collects exactly one sample every 15 minutes
-   Performs a very short sensor read (single snapshot)
-   Stores the value in a local database
-   Supports optional Foreground Service (FGS) mode via toggle
-   Provides a simple UI:
    -   One day per screen (horizontal pager)
    -   List of samples for that day
    -   KPI/analytics at the bottom

------------------------------------------------------------------------

# 1. Core Architecture

## Dependencies

-   WorkManager
-   Room (or SQLite)
-   Kotlin Coroutines
-   DataStore (Preferences)
-   Jetpack Compose (UI)

------------------------------------------------------------------------

# 2. Data Layer

## 2.1 Entity: PressureSampleEntity

Fields:

-   id
-   timestampUtcMillis
-   pressureHpa
-   mode (FGS / NO_FGS)
-   result (OK / TIMEOUT / ERROR / NO_SENSOR)
-   durationMs

------------------------------------------------------------------------

## 2.2 DAO

-   insert(sample)
-   getDistinctDays(limit: Int): Flow\<List`<LocalDate>`{=html}\>
-   getSamplesForDay(startUtc: Long, endUtc: Long):
    Flow\<List`<PressureSample>`{=html}\>

------------------------------------------------------------------------

## 2.3 KPI Per Day

Expected samples per day:

96 (24h \* 60 / 15)

Calculate:

-   samplesCount
-   coverage = samplesCount / 96
-   medianGapMinutes
-   p90GapMinutes
-   maxGapMinutes
-   timeoutsCount
-   errorsCount
-   FGS samples
-   NO_FGS samples

Gap calculation:

-   Sort samples by timestamp
-   Compute delta between consecutive samples

------------------------------------------------------------------------

# 3. Sensor Reading

## 3.1 PressureReader

-   Check TYPE_PRESSURE availability
-   Register listener
-   Wait for first sensor event
-   Timeout: 300--500 ms (short snapshot window)
-   Unregister listener in finally block

Return:

-   Success with value
-   Timeout
-   No sensor

Note: The goal is not continuous sampling --- only the first available
sensor value. Most devices deliver the first pressure event almost
immediately after registration.

------------------------------------------------------------------------

# 4. WorkManager Scheduling

## 4.1 Unique Periodic Work

-   Period: 15 minutes
-   No constraints required
-   Use enqueueUniquePeriodicWork
-   Work name: PRESSURE_PERIODIC_WORK

------------------------------------------------------------------------

## 4.2 WorkScheduler

Functions:

-   schedule(context, useFgs: Boolean)
    -   Cancel existing work
    -   Create new PeriodicWorkRequest
    -   Pass use_fgs in inputData
    -   Enqueue as unique periodic work
-   cancel(context)

------------------------------------------------------------------------

## 4.3 Worker: PressureSampleWorker

Steps:

1.  Read use_fgs from inputData
2.  If use_fgs == true
    -   Call setForegroundAsync(createForegroundInfo())
3.  Read pressure using PressureReader
4.  Store result in DB
5.  Return Result.success()

Important:

-   Total execution should be under a few seconds
-   FGS notification must be low importance and silent

------------------------------------------------------------------------

# 5. Settings -- Toggle for FGS

## 5.1 DataStore

Key:

use_fgs: Boolean (default = false)

------------------------------------------------------------------------

## 5.2 SettingsRepository

-   val useFgsFlow: Flow`<Boolean>`{=html}
-   suspend fun setUseFgs(value: Boolean)

------------------------------------------------------------------------

## 5.3 Toggle Behavior

When user changes toggle:

1.  Save new value in DataStore
2.  Cancel existing periodic work
3.  Reschedule with new mode
4.  Show confirmation (Snackbar/Toast: "Rescheduled")

------------------------------------------------------------------------

# 6. UI

## 6.1 Main Screen Structure

Top:

-   Current mode indicator (FGS / NO_FGS)
-   Toggle switch: "Use Foreground Service"
    -   Subtitle: "May improve reliability but can show a notification."

Middle:

-   HorizontalPager
-   One page = one day

Bottom:

-   KPI Card

------------------------------------------------------------------------

## 6.2 Day Page Layout

Header:

-   Date (local timezone)

Sample List (LazyColumn):

Each row:

-   Time (HH:mm:ss)
-   Pressure value
-   Mode badge (FGS / NO_FGS)
-   Result (if not OK)

KPI Section (Bottom Card):

-   Samples: X / 96
-   Coverage: Y%
-   Median gap
-   P90 gap
-   Max gap
-   Timeouts
-   Errors
-   FGS count
-   NO_FGS count

------------------------------------------------------------------------

# 7. Day Handling

-   Store timestamps in UTC
-   Calculate day using device local timezone
-   Helper functions:
    -   startOfDayUtc(localDate)
    -   endOfDayUtc(localDate)

------------------------------------------------------------------------

# 8. Edge Cases

-   No barometer sensor:
    -   Record NO_SENSOR
    -   Optionally stop scheduling
-   Force-stop:
    -   WorkManager will not restart until manual app launch
-   Reboot:
    -   If no RECEIVE_BOOT_COMPLETED, scheduling resumes only after app
        launch

------------------------------------------------------------------------

End of file.
