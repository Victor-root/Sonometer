package com.example.djmeter.utils

/**
 * Snapshot of all dB values exposed by the measurement chain.
 *
 * - [rawDbFs]       raw 20·log10(rms / 32768), strictly negative full-scale dB.
 * - [calibratedDb]  rawDbFs + base mic offset + user calibration, clamped 0..120.
 * - [currentDb]     Fast-smoothed RMS reading shown in the main display.
 * - [minDb]         lowest calibrated currentDb of the session.
 * - [maxDb]         highest calibrated currentDb of the session.
 * - [peakDb]        short-term per-window peak (instantaneous max sample), calibrated.
 * - [leqDb]         energy-equivalent average (10·log10(mean(10^(db/10)))).
 */
data class SoundLevelReading(
    val rawDbFs: Float,
    val calibratedDb: Float,
    val currentDb: Float,
    val minDb: Float,
    val maxDb: Float,
    val peakDb: Float,
    val leqDb: Float,
)

/**
 * Baseline mic-sensitivity correction applied on top of dBFS to get a usable
 * dB SPL estimate. Tuned for typical phone microphones.
 * The user-tunable [CalibrationStore] offset stacks on top of this.
 */
const val BASE_OFFSET_DB = 90f
