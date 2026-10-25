package com.example.djmeter.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.meterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "meter_prefs"
)

data class MeterSettings(
    val frequencyWeighting: FrequencyWeighting = FrequencyWeighting.A,
    val timeWeighting: TimeWeighting = TimeWeighting.FAST,
    /** User correction in dB per microphone source, on top of its nominal sensitivity. */
    val calibrationDb: Map<MicSource, Float> = emptyMap(),
) {
    fun calibrationFor(source: MicSource): Float = calibrationDb[source] ?: 0f
}

/** Persistent meter settings, like the switches of a physical sound level meter. */
class MeterSettingsStore(private val context: Context) {

    private val frequencyKey = stringPreferencesKey("frequency_weighting")
    private val timeKey = stringPreferencesKey("time_weighting")
    private fun calibrationKey(source: MicSource) = floatPreferencesKey("calibration_${source.name}")

    val settings: Flow<MeterSettings> = context.meterDataStore.data.map { prefs ->
        MeterSettings(
            frequencyWeighting = enumOrDefault(prefs[frequencyKey], FrequencyWeighting.A),
            timeWeighting = enumOrDefault(prefs[timeKey], TimeWeighting.FAST),
            calibrationDb = MicSource.entries.mapNotNull { s -> prefs[calibrationKey(s)]?.let { s to it } }.toMap(),
        )
    }

    suspend fun setFrequencyWeighting(value: FrequencyWeighting) {
        context.meterDataStore.edit { it[frequencyKey] = value.name }
    }

    suspend fun setTimeWeighting(value: TimeWeighting) {
        context.meterDataStore.edit { it[timeKey] = value.name }
    }

    suspend fun setCalibration(source: MicSource, valueDb: Float) {
        context.meterDataStore.edit {
            it[calibrationKey(source)] = valueDb.coerceIn(MIN_CALIBRATION_DB, MAX_CALIBRATION_DB)
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        const val MIN_CALIBRATION_DB = -30f
        const val MAX_CALIBRATION_DB = 30f
    }
}
