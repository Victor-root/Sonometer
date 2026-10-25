package com.example.djmeter.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.djmeter.utils.AudioRecorder
import com.example.djmeter.utils.FrequencyWeighting
import com.example.djmeter.utils.MeterReading
import com.example.djmeter.utils.MeterSettings
import com.example.djmeter.utils.MeterSettingsStore
import com.example.djmeter.utils.MicSource
import com.example.djmeter.utils.PdfGenerator
import com.example.djmeter.utils.SoundLevelMeter
import com.example.djmeter.utils.TimeWeighting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the meter screen displays, already in calibrated dB SPL. */
data class MeterUiState(
    val isRecording: Boolean = false,
    val micUnavailable: Boolean = false,
    val frequencyWeighting: FrequencyWeighting = FrequencyWeighting.A,
    val timeWeighting: TimeWeighting = TimeWeighting.FAST,
    val calibrationDb: Float = 0f,
    val levelDb: Float? = null,
    val minDb: Float? = null,
    val maxDb: Float? = null,
    val leqDb: Float? = null,
    val peakDb: Float? = null,
    val overload: Boolean = false,
    val overloadLatched: Boolean = false,
    val signalLost: Boolean = false,
    val elapsedSeconds: Long = 0,
    val history: List<Float> = emptyList(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private data class Capture(
        val isRecording: Boolean = false,
        val micUnavailable: Boolean = false,
        val source: MicSource? = null,
        val reading: MeterReading? = null,
        val history: List<MeterReading> = emptyList(),
    )

    private val audioRecorder = AudioRecorder(application.applicationContext)
    private val settingsStore = MeterSettingsStore(application.applicationContext)
    private val pdfGenerator = PdfGenerator()
    private val meter = SoundLevelMeter()

    /** Source used for calibration and display until a capture reports the real one. */
    private val defaultSource: MicSource by lazy { audioRecorder.preferredSource() }

    private val capture = MutableStateFlow(Capture())

    private val settings: StateFlow<MeterSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MeterSettings())

    val uiState: StateFlow<MeterUiState> = combine(capture, settings, ::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeterUiState())

    fun toggleRecording() {
        if (capture.value.isRecording) pauseRecording() else startRecording()
    }

    fun startRecording() {
        if (capture.value.isRecording) return
        val config = audioRecorder.start(
            onStart = { meter.start(it.sampleRate) },
            onSamples = { samples, count -> meter.process(samples, count, ::onReading) },
            onError = { viewModelScope.launch { onCaptureError() } },
        )
        capture.update {
            it.copy(
                isRecording = config != null,
                micUnavailable = config == null,
                source = config?.source ?: it.source,
            )
        }
    }

    /** Stops the microphone but keeps the measurement, like a meter's pause key. */
    fun pauseRecording() {
        if (!capture.value.isRecording) return
        audioRecorder.stop()
        capture.update { it.copy(isRecording = false) }
    }

    /** Clears the measurement; a running capture keeps going from scratch. */
    fun resetMeasurement() {
        val wasRecording = capture.value.isRecording
        if (wasRecording) audioRecorder.stop()
        meter.reset()
        capture.update { it.copy(isRecording = false, reading = null, history = emptyList()) }
        if (wasRecording) startRecording()
    }

    private fun onReading(reading: MeterReading) {
        capture.update {
            val history = if (reading.signalLost) it.history else (it.history + reading).takeLast(MAX_HISTORY)
            it.copy(reading = reading, history = history)
        }
    }

    private fun onCaptureError() {
        audioRecorder.stop()
        capture.update { it.copy(isRecording = false, micUnavailable = true) }
    }

    // ---------- Meter settings ----------

    fun setFrequencyWeighting(value: FrequencyWeighting) {
        viewModelScope.launch { settingsStore.setFrequencyWeighting(value) }
    }

    fun setTimeWeighting(value: TimeWeighting) {
        viewModelScope.launch { settingsStore.setTimeWeighting(value) }
    }

    // ---------- Calibration ----------

    fun adjustCalibration(deltaDb: Float) {
        val source = calibrationSource()
        viewModelScope.launch { settingsStore.updateCalibration(source) { it + deltaDb } }
    }

    fun resetCalibration() = setCalibration(calibrationSource(), 0f)

    /**
     * Shifts the calibration so the current Slow level matches [referenceDb],
     * the value of a reference sound level meter next to the phone on the
     * same weighting, measuring a steady sound.
     */
    fun alignCalibrationToReference(referenceDb: Float) {
        val reading = capture.value.reading ?: return
        if (reading.signalLost) return
        val source = calibrationSource()
        val current = settings.value
        val measured = reading[current.frequencyWeighting][TimeWeighting.SLOW].levelDb + source.fullScaleSpl
        setCalibration(source, referenceDb - measured)
    }

    private fun setCalibration(source: MicSource, valueDb: Float) {
        viewModelScope.launch { settingsStore.setCalibration(source, valueDb) }
    }

    private fun calibrationSource(): MicSource = capture.value.source ?: defaultSource

    // ---------- Export ----------

    fun exportGraphToPdf(context: Context): String? {
        val readings = toUiState(capture.value, settings.value).history
        return if (readings.isNotEmpty()) pdfGenerator.generateDecibelGraphPdf(context, readings) else null
    }

    private fun toUiState(c: Capture, s: MeterSettings): MeterUiState {
        val source = c.source ?: defaultSource
        val calibration = s.calibrationFor(source)
        val offset = source.fullScaleSpl + calibration
        val weighted = c.reading?.get(s.frequencyWeighting)?.shifted(offset)
        val stats = weighted?.get(s.timeWeighting)
        return MeterUiState(
            isRecording = c.isRecording,
            micUnavailable = c.micUnavailable,
            frequencyWeighting = s.frequencyWeighting,
            timeWeighting = s.timeWeighting,
            calibrationDb = calibration,
            levelDb = stats?.levelDb,
            minDb = stats?.minDb,
            maxDb = stats?.maxDb,
            leqDb = weighted?.leqDb,
            peakDb = weighted?.peakMaxDb,
            overload = c.reading?.overload == true,
            overloadLatched = c.reading?.overloadLatched == true,
            signalLost = c.reading?.signalLost == true,
            elapsedSeconds = c.reading?.durationSeconds?.toLong() ?: 0,
            history = c.history.map {
                it[s.frequencyWeighting][s.timeWeighting].levelDb + offset
            },
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stop()
    }

    companion object {
        /** 20 s of history at 10 readings per second. */
        private const val MAX_HISTORY = 200
    }
}
