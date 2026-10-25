package com.example.djmeter.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Integrating-averaging sound level meter working on normalised samples
 * (full scale = ±1), for the A, C and Z weightings and the Fast, Slow and
 * Impulse time weightings simultaneously.
 *
 * Every sample goes through: DC blocker → frequency weighting → squaring →
 * exponential time weighting, as in a physical meter. Leq integrates the
 * squared weighted signal itself, never already-averaged dB values.
 *
 * Statistics survive [start] so a paused measurement can be resumed; only
 * [reset] clears them. Not thread-safe: call it from the capture thread only.
 */
class SoundLevelMeter(private val reportIntervalSeconds: Double = 0.1) {

    private var sampleRate = 0
    private var reportSamples = 0
    private var warmupSamples = 0
    private var seedSamples = 0

    private var dcAlpha = 0.0
    private var dcX1 = 0.0
    private var dcY1 = 0.0

    private var channels: List<Channel> = emptyList()
    private var warmupIndex = 0
    private var samplesSinceReport = 0
    private var intervalOverload = false

    private var measuredSeconds = 0.0
    private var overloadLatched = false
    private var signalLost = false

    /** Prepares a new capture run at [sampleRate], keeping the statistics. */
    fun start(sampleRate: Int) {
        if (sampleRate != this.sampleRate) {
            this.sampleRate = sampleRate
            reportSamples = max(1, (sampleRate * reportIntervalSeconds).roundToInt())
            warmupSamples = (sampleRate * WARMUP_SECONDS).roundToInt()
            seedSamples = (sampleRate * SEED_SECONDS).roundToInt()
            dcAlpha = exp(-2.0 * PI * DC_CUTOFF_HZ / sampleRate)
            val previous = channels
            channels = FrequencyWeighting.entries.mapIndexed { i, w ->
                Channel(WeightingFilter(w, sampleRate), sampleRate).also { c -> previous.getOrNull(i)?.let(c::takeStatsFrom) }
            }
        }
        dcX1 = 0.0
        dcY1 = 0.0
        channels.forEach { it.restartSignal() }
        warmupIndex = 0
        samplesSinceReport = 0
        intervalOverload = false
        signalLost = false
    }

    /** Clears every statistic of the measurement. */
    fun reset() {
        channels.forEach { it.resetStats() }
        measuredSeconds = 0.0
        overloadLatched = false
    }

    /**
     * Processes [count] samples of [samples]. [onReading] is called every
     * report interval once the start-up transient has settled.
     *
     * A block of pure digital silence cannot come from a live microphone: it
     * means Android silenced the capture (another app took the mic). Such
     * blocks are kept out of the statistics and reported as [MeterReading.signalLost].
     */
    fun process(samples: FloatArray, count: Int, onReading: (MeterReading) -> Unit) {
        check(sampleRate > 0) { "start() must be called first" }
        if (isDigitalSilence(samples, count)) {
            if (!signalLost) {
                start(sampleRate)
                signalLost = true
            }
            samplesSinceReport += count
            if (samplesSinceReport >= reportSamples && channels.first().hasMeasured) {
                samplesSinceReport = 0
                onReading(snapshot())
            }
            return
        }
        if (signalLost) start(sampleRate)

        for (i in 0 until count) {
            val x = samples[i].toDouble()
            val y = x - dcX1 + dcAlpha * dcY1
            dcX1 = x
            dcY1 = y

            if (warmupIndex < warmupSamples) {
                val seeding = warmupIndex >= warmupSamples - seedSamples
                for (c in channels) c.warmUp(y, seeding)
                warmupIndex++
                if (warmupIndex == warmupSamples) channels.forEach { it.seed(seedSamples) }
                continue
            }

            if (abs(x) >= OVERLOAD_THRESHOLD) intervalOverload = true
            for (c in channels) c.measure(y)

            if (++samplesSinceReport >= reportSamples) {
                measuredSeconds += samplesSinceReport.toDouble() / sampleRate
                overloadLatched = overloadLatched || intervalOverload
                onReading(snapshot())
                samplesSinceReport = 0
                intervalOverload = false
                channels.forEach { it.peakInterval = 0.0 }
            }
        }
    }

    private fun snapshot(): MeterReading = MeterReading(
        byWeighting = FrequencyWeighting.entries.zip(channels) { w, c -> w to c.levels() }.toMap(),
        overload = intervalOverload,
        overloadLatched = overloadLatched,
        durationSeconds = measuredSeconds,
        signalLost = signalLost,
    )

    private fun isDigitalSilence(samples: FloatArray, count: Int): Boolean {
        for (i in 0 until count) if (samples[i] != 0f) return false
        return count > 0
    }

    private class Channel(private val filter: WeightingFilter, sampleRate: Int) {
        private val times = TimeWeighting.entries
        private val meanSquare = DoubleArray(times.size)
        private val minMs = DoubleArray(times.size)
        private val maxMs = DoubleArray(times.size)
        private var impulseHeld = 0.0
        private var seedSum = 0.0
        private val coefficients = DoubleArray(times.size) { 1.0 - exp(-1.0 / (sampleRate * times[it].tauSeconds)) }
        private val impulseDecay = exp(-1.0 / (sampleRate * TimeWeighting.IMPULSE_DECAY_SECONDS))

        private var sumSquares = 0.0
        private var count = 0L
        var peakInterval = 0.0
        private var peakSession = 0.0

        val hasMeasured: Boolean get() = count > 0

        init {
            resetStats()
        }

        fun restartSignal() {
            filter.reset()
            meanSquare.fill(0.0)
            impulseHeld = 0.0
            seedSum = 0.0
            peakInterval = 0.0
        }

        fun resetStats() {
            minMs.fill(Double.MAX_VALUE)
            maxMs.fill(0.0)
            sumSquares = 0.0
            count = 0
            peakSession = 0.0
        }

        fun takeStatsFrom(other: Channel) {
            other.minMs.copyInto(minMs)
            other.maxMs.copyInto(maxMs)
            sumSquares = other.sumSquares
            count = other.count
            peakSession = other.peakSession
        }

        fun warmUp(x: Double, seeding: Boolean) {
            val y = filter.process(x)
            if (seeding) seedSum += y * y
        }

        /** Starts the integrators from the settled level instead of silence. */
        fun seed(seedSamples: Int) {
            val ms = seedSum / max(1, seedSamples)
            meanSquare.fill(ms)
            impulseHeld = ms
        }

        fun measure(x: Double) {
            val y = filter.process(x)
            val sq = y * y
            sumSquares += sq
            count++
            val a = abs(y)
            if (a > peakInterval) peakInterval = a
            if (a > peakSession) peakSession = a

            for (t in times.indices) {
                meanSquare[t] += (sq - meanSquare[t]) * coefficients[t]
            }
            val impulseIndex = TimeWeighting.IMPULSE.ordinal
            impulseHeld = max(meanSquare[impulseIndex], impulseHeld * impulseDecay)

            for (t in times.indices) {
                val ms = if (t == impulseIndex) impulseHeld else meanSquare[t]
                if (ms < minMs[t]) minMs[t] = ms
                if (ms > maxMs[t]) maxMs[t] = ms
            }
        }

        fun levels(): WeightedLevels {
            val impulseIndex = TimeWeighting.IMPULSE.ordinal
            return WeightedLevels(
                leqDb = powerDb(if (count > 0) sumSquares / count else 0.0),
                peakDb = amplitudeDb(peakInterval),
                peakMaxDb = amplitudeDb(peakSession),
                byTime = times.associateWith { t ->
                    val i = t.ordinal
                    LevelStats(
                        levelDb = powerDb(if (i == impulseIndex) impulseHeld else meanSquare[i]),
                        minDb = powerDb(minMs[i]),
                        maxDb = powerDb(maxMs[i]),
                    )
                },
            )
        }
    }

    companion object {
        /** Lowest level ever reported, far below any microphone noise floor. */
        const val FLOOR_DB = -200f

        /** First samples of each run are only used to settle the filters. */
        const val WARMUP_SECONDS = 0.5
        private const val SEED_SECONDS = 0.125
        private const val DC_CUTOFF_HZ = 2.0
        private const val OVERLOAD_THRESHOLD = 0.989 // -0.1 dBFS

        fun powerDb(meanSquare: Double): Float =
            if (meanSquare <= 0.0) FLOOR_DB
            else max(FLOOR_DB.toDouble(), 10.0 * log10(meanSquare)).toFloat()

        fun amplitudeDb(amplitude: Double): Float = powerDb(amplitude * amplitude)
    }
}
