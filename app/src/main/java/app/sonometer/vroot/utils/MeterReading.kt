package app.sonometer.vroot.utils

/**
 * Exponential time weightings of a sound level meter (IEC 61672-1 for Fast
 * and Slow, IEC 60651 for Impulse which also uses a slow peak-hold decay).
 */
enum class TimeWeighting(val tauSeconds: Double) {
    FAST(0.125),
    SLOW(1.0),
    IMPULSE(0.035);

    /** Time for a level starting from rest to come within 0.1 dB of a steady sound. */
    val settleSeconds: Double
        get() = SETTLE_TIME_CONSTANTS * tauSeconds

    companion object {
        const val IMPULSE_DECAY_SECONDS = 1.5
        private const val SETTLE_TIME_CONSTANTS = 4.0
    }
}

/** Time-weighted level with its measurement extremes (null until settled), in dB. */
data class LevelStats(
    val levelDb: Float,
    val minDb: Float?,
    val maxDb: Float?,
)

/**
 * Levels of one frequency weighting, in dB.
 *
 * - [leqDb]      equivalent continuous level over the whole measurement.
 * - [peakMaxDb]  highest instantaneous weighted sample of the measurement.
 */
data class WeightedLevels(
    val leqDb: Float?,
    val peakMaxDb: Float?,
    val byTime: Map<TimeWeighting, LevelStats>,
) {
    operator fun get(timeWeighting: TimeWeighting): LevelStats = byTime.getValue(timeWeighting)

    fun shifted(offsetDb: Float) = WeightedLevels(
        leqDb = leqDb?.plus(offsetDb),
        peakMaxDb = peakMaxDb?.plus(offsetDb),
        byTime = byTime.mapValues { (_, s) ->
            LevelStats(s.levelDb + offsetDb, s.minDb?.plus(offsetDb), s.maxDb?.plus(offsetDb))
        },
    )
}

/**
 * Snapshot produced by [SoundLevelMeter] for every frequency weighting at
 * once, so switching weighting never loses the running statistics.
 *
 * Levels are in dBFS (0 dB = full-scale amplitude) until shifted by the
 * microphone sensitivity and the user calibration. [signalLost] means the
 * system currently feeds silence instead of the microphone.
 */
data class MeterReading(
    val byWeighting: Map<FrequencyWeighting, WeightedLevels>,
    val overload: Boolean,
    val overloadLatched: Boolean,
    val durationSeconds: Double,
    val signalLost: Boolean,
) {
    operator fun get(weighting: FrequencyWeighting): WeightedLevels = byWeighting.getValue(weighting)
}
