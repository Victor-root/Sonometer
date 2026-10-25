package com.example.djmeter.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.djmeter.BuildConfig

/**
 * Microphone sources usable for measurement, best first.
 *
 * [fullScaleSpl] is the sound pressure level (dB SPL) giving a full-scale
 * RMS value, derived from the input sensitivity the Android CDD asks for:
 * - UNPROCESSED (CDD 5.11): 94 dB SPL at 1 kHz → −36 dBFS, no processing at all.
 * - VOICE_RECOGNITION (CDD 5.4.2): 90 dB SPL at 1 kHz → −22.35 dBFS ±3 dB,
 *   noise suppression and AGC disabled by default.
 * - MIC: no sensitivity requirement, may be processed; last resort.
 */
enum class MicSource(val androidSource: Int, val fullScaleSpl: Float) {
    UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED, 130f),
    VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION, 112.35f),
    MIC(MediaRecorder.AudioSource.MIC, 112.35f),
}

/**
 * Raw microphone capture delivering normalised float samples (full scale = ±1)
 * on a dedicated audio-priority thread.
 */
class AudioRecorder(private val context: Context) {

    data class Config(val source: MicSource, val sampleRate: Int, val floatSamples: Boolean)

    private var audioRecord: AudioRecord? = null
    private var effects: List<AudioEffect> = emptyList()
    @Volatile private var isRecording = false
    private var thread: Thread? = null

    /**
     * Starts capturing. [onStart] runs on the capture thread before the first
     * block, then [onSamples] receives every block. Returns null when no
     * source could be opened.
     */
    fun start(
        onStart: (Config) -> Unit,
        onSamples: (samples: FloatArray, count: Int) -> Unit,
    ): Config? {
        if (isRecording) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        val (record, config) = openBestRecord() ?: return null
        effects = disableProcessing(record.audioSessionId)
        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            debugLog("startRecording failed", e)
            release(record)
            return null
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            debugLog("microphone busy, recordingState=${record.recordingState}")
            release(record)
            return null
        }

        audioRecord = record
        isRecording = true
        thread = Thread({ captureLoop(record, config, onStart, onSamples) }, "sonometer-capture")
            .also { it.start() }
        debugLog("capture started: $config")
        return config
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        try {
            thread?.join(STOP_TIMEOUT_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
        audioRecord?.let { release(it) }
        audioRecord = null
    }

    private fun captureLoop(
        record: AudioRecord,
        config: Config,
        onStart: (Config) -> Unit,
        onSamples: (FloatArray, Int) -> Unit,
    ) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val blockSize = config.sampleRate / BLOCKS_PER_SECOND
        val samples = FloatArray(blockSize)
        val pcm16 = if (config.floatSamples) null else ShortArray(blockSize)
        onStart(config)

        while (isRecording) {
            val read = if (pcm16 == null) {
                record.read(samples, 0, blockSize, AudioRecord.READ_BLOCKING)
            } else {
                record.read(pcm16, 0, blockSize).also { n ->
                    for (i in 0 until n) samples[i] = pcm16[i] / PCM16_FULL_SCALE
                }
            }
            when {
                read > 0 -> onSamples(samples, read)
                read == 0 -> continue
                else -> {
                    debugLog("read error $read, stopping capture")
                    break
                }
            }
        }
    }

    /** Tries every source, sample rate and encoding from best to worst. */
    @SuppressLint("MissingPermission")
    private fun openBestRecord(): Pair<AudioRecord, Config>? {
        for (source in availableSources()) {
            for (rate in SAMPLE_RATES) {
                for (floatSamples in booleanArrayOf(true, false)) {
                    val encoding = if (floatSamples) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
                    val minBytes = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, encoding)
                    if (minBytes <= 0) continue
                    val bytesPerSample = if (floatSamples) 4 else 2
                    val bufferBytes = maxOf(minBytes * 2, (rate * BUFFER_SECONDS).toInt() * bytesPerSample)
                    val record = try {
                        AudioRecord(source.androidSource, rate, AudioFormat.CHANNEL_IN_MONO, encoding, bufferBytes)
                    } catch (e: IllegalArgumentException) {
                        debugLog("rejected $source $rate Hz float=$floatSamples", e)
                        continue
                    }
                    if (record.state == AudioRecord.STATE_INITIALIZED && record.sampleRate == rate) {
                        return record to Config(source, rate, floatSamples)
                    }
                    record.release()
                }
            }
        }
        return null
    }

    private fun availableSources(): List<MicSource> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.equals("true", ignoreCase = true) == true
        return if (unprocessed) MicSource.entries else MicSource.entries - MicSource.UNPROCESSED
    }

    /**
     * Some devices attach voice processing to the session anyway; switch every
     * such effect off so the level is not altered.
     */
    private fun disableProcessing(sessionId: Int): List<AudioEffect> {
        val created = mutableListOf<AudioEffect>()
        fun disable(effect: AudioEffect?) {
            effect ?: return
            try {
                if (effect.enabled) effect.setEnabled(false)
                created += effect
                debugLog("${effect.descriptor.name} disabled")
            } catch (e: IllegalStateException) {
                effect.release()
            }
        }
        if (AutomaticGainControl.isAvailable()) disable(AutomaticGainControl.create(sessionId))
        if (NoiseSuppressor.isAvailable()) disable(NoiseSuppressor.create(sessionId))
        if (AcousticEchoCanceler.isAvailable()) disable(AcousticEchoCanceler.create(sessionId))
        return created
    }

    private fun release(record: AudioRecord) {
        try {
            record.stop()
        } catch (_: IllegalStateException) {
            // Never started.
        }
        record.release()
        effects.forEach { it.release() }
        effects = emptyList()
    }

    private fun debugLog(message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.d(TAG, message, error)
    }

    companion object {
        private const val TAG = "AudioRecorder"
        private val SAMPLE_RATES = intArrayOf(48000, 44100)
        private const val BLOCKS_PER_SECOND = 50
        private const val BUFFER_SECONDS = 0.5
        private const val PCM16_FULL_SCALE = 32768f
        private const val STOP_TIMEOUT_MS = 500L
    }
}
