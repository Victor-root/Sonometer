package com.example.djmeter.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Microphone capture + RMS / Peak dBFS estimation.
 *
 * - Tries [MediaRecorder.AudioSource.UNPROCESSED] first (raw mic, no AGC/NS),
 *   falls back to [MediaRecorder.AudioSource.MIC] if unsupported.
 * - Removes DC offset before computing RMS.
 * - Applies an exponential time-weighting equivalent to "Fast" (τ = 125 ms)
 *   on the energy signal so the reading is responsive but not jittery.
 * - Per-chunk peak (max |sample|) is reported alongside the smoothed RMS.
 * - Does not enable AGC / NoiseSuppressor / AcousticEchoCanceler; their
 *   availability is logged for debugging.
 *
 * The output is in dBFS (negative values). Conversion to dB SPL is the
 * caller's responsibility (see [BASE_OFFSET_DB] and [CalibrationStore]).
 */
class AudioRecorder(private val context: Context) {

    /** Per-chunk reading. Values are in dBFS (≤ 0). */
    data class Reading(val rmsDbFs: Float, val peakDbFs: Float)

    private var audioRecord: AudioRecord? = null
    @Volatile private var isRecording = false
    private var thread: Thread? = null

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bytesPerSample = 2

    /** Fast time-weighting constant (≈ 125 ms). */
    private val tauSeconds = 0.125

    /** ~50 ms chunks → ≈ 20 readings/s. Enough for a smooth UI. */
    private val chunkSamples = sampleRate / 20

    /** AudioRecord internal ring buffer (in bytes). At least ~1 s. */
    private val internalBufferBytes: Int by lazy {
        val minBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        when {
            minBytes == AudioRecord.ERROR || minBytes == AudioRecord.ERROR_BAD_VALUE -> {
                Log.w(TAG, "getMinBufferSize returned $minBytes, falling back to 1s buffer")
                sampleRate * bytesPerSample
            }
            else -> max(minBytes * 2, sampleRate * bytesPerSample)
        }
    }

    fun startRecording(onReading: (Reading) -> Unit) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO permission missing — cannot start recording")
            return
        }
        if (isRecording) return

        val preferred = pickPreferredSource()
        val ar = createRecord(preferred)
            ?: createRecord(MediaRecorder.AudioSource.MIC)
            ?: run {
                Log.e(TAG, "Could not initialize AudioRecord with any source")
                return
            }

        Log.i(
            TAG,
            "AudioRecord initialized: source=${sourceName(ar.audioSource)} " +
                "sampleRate=${ar.sampleRate}Hz bufferBytes=$internalBufferBytes " +
                "bufferShorts=${internalBufferBytes / bytesPerSample} chunkSamples=$chunkSamples"
        )
        logEffectsAvailability()

        try {
            ar.startRecording()
        } catch (t: Throwable) {
            Log.e(TAG, "AudioRecord.startRecording() failed", t)
            ar.release()
            return
        }

        audioRecord = ar
        isRecording = true
        thread = Thread { audioLoop(ar, onReading) }.also { it.start() }
    }

    private fun audioLoop(ar: AudioRecord, onReading: (Reading) -> Unit) {
        val buffer = ShortArray(chunkSamples)
        var smoothedEnergy = 0.0
        val chunkDuration = chunkSamples.toDouble() / sampleRate
        val alpha = 1.0 - exp(-chunkDuration / tauSeconds)

        while (isRecording) {
            val read = try {
                ar.read(buffer, 0, buffer.size)
            } catch (t: Throwable) {
                Log.e(TAG, "AudioRecord.read threw", t)
                -1
            }
            if (read <= 0) {
                if (read == AudioRecord.ERROR_INVALID_OPERATION ||
                    read == AudioRecord.ERROR_BAD_VALUE
                ) {
                    Log.e(TAG, "AudioRecord.read error: $read — stopping loop")
                    break
                }
                continue
            }

            // 1. DC offset removal
            var dcSum = 0.0
            for (i in 0 until read) dcSum += buffer[i]
            val dc = dcSum / read

            // 2. RMS energy (mean square of centred samples) and per-chunk peak
            var sumSq = 0.0
            var peakAbs = 0.0
            for (i in 0 until read) {
                val centered = buffer[i].toDouble() - dc
                sumSq += centered * centered
                val abs = if (centered < 0) -centered else centered
                if (abs > peakAbs) peakAbs = abs
            }
            val chunkEnergy = sumSq / read

            // 3. Fast time weighting (exp. moving average on linear energy)
            smoothedEnergy = alpha * chunkEnergy + (1.0 - alpha) * smoothedEnergy

            // 4. Convert to dBFS
            val rms = sqrt(smoothedEnergy)
            val rmsNorm = (rms / 32768.0).coerceAtLeast(1e-12)
            val rmsDbFs = (20.0 * log10(rmsNorm)).toFloat()

            val peakNorm = (peakAbs / 32768.0).coerceAtLeast(1e-12)
            val peakDbFs = (20.0 * log10(peakNorm)).toFloat()

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "samples=$read rms=$rmsDbFs peak=$peakDbFs dBFS")
            }

            onReading(Reading(rmsDbFs, peakDbFs))
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        try {
            thread?.join(500)
        } catch (_: InterruptedException) { /* ignore */ }
        thread = null
        audioRecord?.let {
            try { it.stop() } catch (_: Throwable) { /* already stopped */ }
            it.release()
        }
        audioRecord = null
    }

    private fun createRecord(source: Int): AudioRecord? {
        return try {
            val ar = AudioRecord(source, sampleRate, channelConfig, audioFormat, internalBufferBytes)
            if (ar.state == AudioRecord.STATE_INITIALIZED) {
                ar
            } else {
                Log.w(TAG, "AudioRecord state=${ar.state} for source=${sourceName(source)} — releasing")
                ar.release()
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "AudioRecord ctor failed for source=${sourceName(source)}", t)
            null
        }
    }

    private fun pickPreferredSource(): Int {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val supported = am
            ?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.equals("true", ignoreCase = true) == true
        Log.i(TAG, "PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED=$supported")
        return if (supported) MediaRecorder.AudioSource.UNPROCESSED
        else MediaRecorder.AudioSource.MIC
    }

    /**
     * Logs whether AGC / NoiseSuppressor / AcousticEchoCanceler are available
     * on this device. We do NOT enable them — the goal is to measure raw
     * sound level, not a voice-optimized stream. If the OEM applies them at
     * a lower level on MIC source, only switching to UNPROCESSED can avoid it.
     */
    private fun logEffectsAvailability() {
        val agc = AutomaticGainControl.isAvailable()
        val ns = NoiseSuppressor.isAvailable()
        val aec = AcousticEchoCanceler.isAvailable()
        Log.i(TAG, "Effects available — AGC=$agc NS=$ns AEC=$aec (none enabled by us)")
    }

    private fun sourceName(s: Int): String = when (s) {
        MediaRecorder.AudioSource.MIC -> "MIC"
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION"
        MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER"
        MediaRecorder.AudioSource.DEFAULT -> "DEFAULT"
        else -> "OTHER($s)"
    }

    companion object {
        private const val TAG = "AudioRecorder"
    }
}
