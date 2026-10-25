package com.example.djmeter.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calibrationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "calibration_prefs"
)

/**
 * Persistent user-tunable calibration offset (in dB) on top of the baseline
 * mic-sensitivity correction. 0 dB = no extra correction.
 */
class CalibrationStore(private val context: Context) {

    private val offsetKey = floatPreferencesKey("offset_db")

    val offsetFlow: Flow<Float> = context.calibrationDataStore.data.map { prefs ->
        prefs[offsetKey] ?: DEFAULT_OFFSET_DB
    }

    suspend fun setOffset(value: Float) {
        context.calibrationDataStore.edit { it[offsetKey] = value }
    }

    companion object {
        const val DEFAULT_OFFSET_DB = 0f
        const val MIN_OFFSET_DB = -30f
        const val MAX_OFFSET_DB = 30f
    }
}
