package com.example.djmeter.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.djmeter.utils.AudioRecorder
import com.example.djmeter.utils.PdfGenerator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val audioRecorder = AudioRecorder(application.applicationContext)
    private val pdfGenerator = PdfGenerator()

    private val _decibelLevel = MutableStateFlow(0f)
    val decibelLevel: StateFlow<Float> = _decibelLevel

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

    // Aggregated stats over the current session.
    private val _minDb = MutableStateFlow<Float?>(null)
    val minDb: StateFlow<Float?> = _minDb

    private val _maxDb = MutableStateFlow<Float?>(null)
    val maxDb: StateFlow<Float?> = _maxDb

    private val _avgDb = MutableStateFlow<Float?>(null)
    val avgDb: StateFlow<Float?> = _avgDb

    private var sampleCount = 0L
    private var sumDb = 0.0
    private var timerJob: Job? = null

    fun toggleRecording() {
        if (_isRecording.value) pauseRecording() else startRecording()
    }

    fun startRecording() {
        if (_isRecording.value) return

        audioRecorder.startRecording { decibel ->
            viewModelScope.launch {
                _decibelLevel.value = decibel

                val current = _decibelReadings.value.toMutableList()
                if (current.size >= MAX_LIVE_READINGS) current.removeAt(0)
                current.add(decibel)
                _decibelReadings.value = current

                sampleCount += 1
                sumDb += decibel
                _avgDb.value = (sumDb / sampleCount).toFloat()
                _minDb.value = _minDb.value?.let { minOf(it, decibel) } ?: decibel
                _maxDb.value = _maxDb.value?.let { maxOf(it, decibel) } ?: decibel
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
        _decibelReadings.value = emptyList()
        _sessionReadings.value = emptyList()
        _hasSessionData.value = false
        _recordingTime.value = 0L
        _minDb.value = null
        _maxDb.value = null
        _avgDb.value = null
        sampleCount = 0
        sumDb = 0.0
    }

    /** Backward-compat alias used by older code paths. */
    fun stopRecording() = pauseRecording()

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
    }
}
