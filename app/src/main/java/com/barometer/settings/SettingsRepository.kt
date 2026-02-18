package com.barometer.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(
    private val context: Context,
) {
    private val useFgsKey: Preferences.Key<Boolean> = booleanPreferencesKey("use_fgs")

    val useFgsFlow: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[useFgsKey] ?: false
    }

    suspend fun setUseFgs(value: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[useFgsKey] = value
        }
    }
}
