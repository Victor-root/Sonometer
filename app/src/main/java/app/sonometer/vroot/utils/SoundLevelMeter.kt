package app.sonometer.vroot.utils

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
 * Each capture run starts with a warm-up that only settles the filters. The
 * time weightings then start from rest, like a meter being switched on, and
 * each one enters the min/max statistics once it has settled
 * ([TimeWeighting.settleSeconds]), so its rise from zero is never taken as a minimum.
 *
 * Statistics survive [start] so a paused measurement can be resumed; only
 * [reset] clears them. Not thread-safe: call it from the capture thread only.
 */
class SoundLevelMeter(private val reportIntervalSeconds: Double = 0.1) {

    private var sampleRate = 0
    private var reportSamples = 0
    private var warmupSamples = 0

    private var dcAlpha = 0.0
    private var dcX1 = 0.0
    private var dcY1 = 0.0

    private val stats = FrequencyWeighting.entries.map { Stats() }
    private var channels: List<Channel> = emptyList()
    private var warmupIndex = 0
    private var samplesSinceReport = 0
    private var silentSamples = 0
    private var intervalOverload = false

    private var measuredSeconds = 0.0
    private var overloadLatched = false
    private var signalLost = false

    /** Prepares a new capture run at [sampleRate], keeping the statistics. */
    fun start(sampleRate: Int) {
        closeInterval()
        if (sampleRate != this.sampleRate) {
            if (this.sampleRate > 0) stats.forEach { it.fold(this.sampleRate) }
            this.sampleRate = sampleRate
            reportSamples = max(1, (sampleRate * reportIntervalSeconds).roundToInt())
            warmupSamples = (sampleRate * WARMUP_SECONDS).roundToInt()
            dcAlpha = exp(-2.0 * PI * DC_CUTOFF_HZ / sampleRate)
            channels = FrequencyWeighting.entries.mapIndexed { i, w ->
                Channel(WeightingFilter(w, sampleRate), sampleRate, stats[i])
            }
        }
        dcX1 = 0.0
        dcY1 = 0.0
        channels.forEach { it.restartSignal() }
        warmupIndex = 0
        silentSamples = 0
        intervalOverload = false
        signalLost = false
    }

    /** Clears every statistic of the measurement. */
    fun reset() {
        stats.forEach { it.clear() }
        samplesSinceReport = 0
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
            silentSamples += count
            if (silentSamples >= reportSamples) {
                silentSamples = 0
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
                for (c in channels) c.warmUp(y)
                warmupIndex++
                continue
            }

            if (abs(x) >= OVERLOAD_THRESHOLD) intervalOverload = true
            for (c in channels) c.measure(y)

            if (++samplesSinceReport >= reportSamples) {
                closeInterval()
                onReading(snapshot())
                intervalOverload = false
            }
        }
    }

    /** Accounts the measured part of the current report interval. */
    private fun closeInterval() {
        if (samplesSinceReport == 0) return
        measuredSeconds += samplesSinceReport.toDouble() / sampleRate
        overloadLatched = overloadLatched || intervalOverload
        samplesSinceReport = 0
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

    /** Measurement statistics of one frequency weighting, kept across runs. */
    private class Stats {
        val minMs = DoubleArray(TimeWeighting.entries.size)
        val maxMs = DoubleArray(TimeWeighting.entries.size)
        var peak = 0.0

        /** Energy and sample count at the current sample rate. */
        var energy = 0.0
        var samples = 0L

        /** Energy·seconds and seconds of earlier sample rates. */
        private var foldedEnergy = 0.0
        private var foldedSeconds = 0.0

        init {
            clear()
        }

        fun clear() {
            minMs.fill(Double.POSITIVE_INFINITY)
            maxMs.fill(NOT_SET)
            peak = 0.0
            energy = 0.0
            samples = 0
            foldedEnergy = 0.0
            foldedSeconds = 0.0
        }

        fun fold(sampleRate: Int) {
            foldedEnergy += energy / sampleRate
            foldedSeconds += samples.toDouble() / sampleRate
            energy = 0.0
            samples = 0
        }

        fun leqMeanSquare(sampleRate: Int): Double? {
            val seconds = foldedSeconds + samples.toDouble() / sampleRate
            return if (seconds > 0.0) (foldedEnergy + energy / sampleRate) / seconds else null
        }
    }

    private class Channel(
        private val filter: WeightingFilter,
        private val sampleRate: Int,
        private val stats: Stats,
    ) {
        private val times = TimeWeighting.entries
        private val impulseIndex = TimeWeighting.IMPULSE.ordinal
        private val coefficients = DoubleArray(times.size) { 1.0 - exp(-1.0 / (sampleRate * times[it].tauSeconds)) }
        private val impulseDecay = exp(-1.0 / (sampleRate * TimeWeighting.IMPULSE_DECAY_SECONDS))
        private val settleSamples = LongArray(times.size) { (sampleRate * times[it].settleSeconds).toLong() }

        private val meanSquare = DoubleArray(times.size)
        private var impulseHeld = 0.0
        private var samplesSinceStart = 0L

        fun restartSignal() {
            filter.reset()
            meanSquare.fill(0.0)
            impulseHeld = 0.0
            samplesSinceStart = 0
        }

        fun warmUp(x: Double) {
            filter.process(x)
        }

        fun measure(x: Double) {
            val y = filter.process(x)
            val sq = y * y
            stats.energy += sq
            stats.samples++
            val a = abs(y)
            if (a > stats.peak) stats.peak = a

            for (t in times.indices) {
                meanSquare[t] += (sq - meanSquare[t]) * coefficients[t]
            }
            impulseHeld = max(meanSquare[impulseIndex], impulseHeld * impulseDecay)

            samplesSinceStart++
            for (t in times.indices) {
                if (samplesSinceStart < settleSamples[t]) continue
                val ms = level(t)
                if (ms < stats.minMs[t]) stats.minMs[t] = ms
                if (ms > stats.maxMs[t]) stats.maxMs[t] = ms
            }
        }

        private fun level(t: Int): Double = if (t == impulseIndex) impulseHeld else meanSquare[t]

        fun levels(): WeightedLevels = WeightedLevels(
            leqDb = stats.leqMeanSquare(sampleRate)?.let(::powerDb),
            peakMaxDb = stats.peak.takeIf { it > 0.0 }?.let(::amplitudeDb),
            byTime = times.associateWith { t ->
                val i = t.ordinal
                LevelStats(
                    levelDb = powerDb(level(i)),
                    minDb = stats.minMs[i].takeIf { it != Double.POSITIVE_INFINITY }?.let(::powerDb),
                    maxDb = stats.maxMs[i].takeIf { it != NOT_SET }?.let(::powerDb),
                )
            },
        )
    }

    companion object {
        /** Lowest level ever reported, far below any microphone noise floor. */
        const val FLOOR_DB = -200f

        /** First samples of each run are only used to settle the filters. */
        const val WARMUP_SECONDS = 0.5
        private const val DC_CUTOFF_HZ = 2.0
        private const val OVERLOAD_THRESHOLD = 0.989 // -0.1 dBFS
        private const val NOT_SET = -1.0

        fun powerDb(meanSquare: Double): Float =
            if (meanSquare <= 0.0) FLOOR_DB
            else max(FLOOR_DB.toDouble(), 10.0 * log10(meanSquare)).toFloat()

        fun amplitudeDb(amplitude: Double): Float = powerDb(amplitude * amplitude)
    }
}
