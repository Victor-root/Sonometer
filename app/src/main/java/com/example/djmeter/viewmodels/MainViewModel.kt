package com.example.djmeter.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.djmeter.utils.AudioRecorder
import com.example.djmeter.utils.BASE_OFFSET_DB
import com.example.djmeter.utils.CalibrationStore
import com.example.djmeter.utils.PdfGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val audioRecorder = AudioRecorder(application.applicationContext)
    private val pdfGenerator = PdfGenerator()
    private val calibrationStore = CalibrationStore(application.applicationContext)

    /** Persistent user calibration offset in dB (default 0). */
    val calibrationOffset: StateFlow<Float> = calibrationStore.offsetFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, CalibrationStore.DEFAULT_OFFSET_DB)

    private val _decibelLevel = MutableStateFlow(0f)
    val decibelLevel: StateFlow<Float> = _decibelLevel

    /** Short-term per-window peak (instantaneous max sample, calibrated). */
    private val _peakDb = MutableStateFlow<Float?>(null)
    val peakDb: StateFlow<Float?> = _peakDb

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    // Bounded rolling buffer used by the live chart.
    private val _decibelReadings = MutableStateFlow<List<Float>>(emptyList())
    val decibelReadings: StateFlow<List<Float>> = _decibelReadings

    private val _sessionReadings = MutableStateFlow<List<Float>>(emptyList())
    val sessionReadings: StateFlow<List<Float>> = _sessionReadings

    private val _hasSessionData = MutableStateFlow(false)
    val hasSessionData: StateFlow<Boolean> = _hasSessionData

    private val _recordingTime = MutableStateFlow(0L)
    val recordingTime: StateFlow<Long> = _recordingTime

    // Aggregated stats over the current session — all in calibrated dB.
    private val _minDb = MutableStateFlow<Float?>(null)
    val minDb: StateFlow<Float?> = _minDb

    private val _maxDb = MutableStateFlow<Float?>(null)
    val maxDb: StateFlow<Float?> = _maxDb

    /** Energy-equivalent average (Leq), exposed under the existing avg flow. */
    private val _avgDb = MutableStateFlow<Float?>(null)
    val avgDb: StateFlow<Float?> = _avgDb

    private var sumEnergyLin = 0.0
    private var sampleCount = 0L
    private var timerJob: Job? = null

    fun toggleRecording() {
        if (_isRecording.value) pauseRecording() else startRecording()
    }

    fun startRecording() {
        if (_isRecording.value) return

        audioRecorder.startRecording { reading ->
            val offset = calibrationOffset.value
            val rmsCalibrated = (reading.rmsDbFs + BASE_OFFSET_DB + offset).coerceIn(0f, 120f)
            val peakCalibrated = (reading.peakDbFs + BASE_OFFSET_DB + offset).coerceIn(0f, 120f)

            viewModelScope.launch {
                _decibelLevel.value = rmsCalibrated
                _peakDb.value = peakCalibrated

                val current = _decibelReadings.value.toMutableList()
                if (current.size >= MAX_LIVE_READINGS) current.removeAt(0)
                current.add(rmsCalibrated)
                _decibelReadings.value = current

                sampleCount += 1
                sumEnergyLin += 10.0.pow(rmsCalibrated.toDouble() / 10.0)
                val leq = (10.0 * log10(sumEnergyLin / sampleCount)).toFloat()
                _avgDb.value = leq.coerceIn(0f, 120f)

                _minDb.value = _minDb.value?.let { minOf(it, rmsCalibrated) } ?: rmsCalibrated
                _maxDb.value = _maxDb.value?.let { maxOf(it, rmsCalibrated) } ?: rmsCalibrated

                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(
                        TAG,
                        "raw=${reading.rmsDbFs} peak=${reading.peakDbFs} " +
                            "offset=$offset cal=$rmsCalibrated leq=${_avgDb.value}"
                    )
                }
            }
        }
        _isRecording.value = true
        startTimer()
    }

    /** Stops the mic but keeps stats and chart visible (acts as Pause). */
    fun pauseRecording() {
        if (!_isRecording.value) return
        audioRecorder.stopRecording()
        _isRecording.value = false
        timerJob?.cancel()
        _sessionReadings.value = _decibelReadings.value.toList()
        _hasSessionData.value = _sessionReadings.value.isNotEmpty()
    }

    /** Full reset: stops recording and clears stats / chart. */
    fun resetMeasurement() {
        if (_isRecording.value) {
            audioRecorder.stopRecording()
            _isRecording.value = false
            timerJob?.cancel()
        }
        _decibelLevel.value = 0f
        _peakDb.value = null
        _decibelReadings.value = emptyList()
        _sessionReadings.value = emptyList()
        _hasSessionData.value = false
        _recordingTime.value = 0L
        _minDb.value = null
        _maxDb.value = null
        _avgDb.value = null
        sampleCount = 0
        sumEnergyLin = 0.0
    }

    /** Backward-compat alias used by older code paths. */
    fun stopRecording() = pauseRecording()

    // ---------- Calibration controls ----------

    fun adjustCalibration(deltaDb: Float) {
        val newValue = (calibrationOffset.value + deltaDb)
            .coerceIn(CalibrationStore.MIN_OFFSET_DB, CalibrationStore.MAX_OFFSET_DB)
        viewModelScope.launch { calibrationStore.setOffset(newValue) }
    }

    fun resetCalibration() {
        viewModelScope.launch { calibrationStore.setOffset(CalibrationStore.DEFAULT_OFFSET_DB) }
    }

    /**
     * Aligns the calibration so that the currently displayed level matches
     * [referenceDb] (the value read on a real sound meter held next to the phone).
     */
    fun alignCalibrationToReference(referenceDb: Float) {
        val current = _decibelLevel.value
        if (current <= 0f) return
        val newValue = (calibrationOffset.value + (referenceDb - current))
            .coerceIn(CalibrationStore.MIN_OFFSET_DB, CalibrationStore.MAX_OFFSET_DB)
        viewModelScope.launch { calibrationStore.setOffset(newValue) }
    }

    // ---------- Misc ----------

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _recordingTime.value += 1
            }
        }
    }

    fun exportGraphToPdf(context: Context): String? {
        return if (_sessionReadings.value.isNotEmpty()) {
            pdfGenerator.generateDecibelGraphPdf(context, _sessionReadings.value)
        } else null
    }

    fun clearSessionData() {
        _sessionReadings.value = emptyList()
        _hasSessionData.value = false
    }

    override fun onCleared() {
        super.onCleared()
        if (_isRecording.value) audioRecorder.stopRecording()
        timerJob?.cancel()
    }

    companion object {
        private const val MAX_LIVE_READINGS = 200
        private const val TAG = "MainViewModel"
    }
}
