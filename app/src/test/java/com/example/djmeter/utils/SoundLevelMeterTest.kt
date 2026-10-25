package com.example.djmeter.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class SoundLevelMeterTest {

    private val fs = 48000

    /** Feeds [seconds] of a sine of [amplitude] at [frequency] and returns every reading. */
    private fun SoundLevelMeter.feed(
        seconds: Double,
        amplitude: Double,
        frequency: Double = 1000.0,
        startSample: Long = 0,
    ): List<MeterReading> {
        val readings = mutableListOf<MeterReading>()
        val total = (seconds * fs).toInt()
        val block = FloatArray(960)
        var n = 0
        while (n < total) {
            val count = minOf(block.size, total - n)
            for (i in 0 until count) {
                val t = (startSample + n + i).toDouble() / fs
                block[i] = (amplitude * sin(2 * PI * frequency * t)).toFloat()
            }
            process(block, count) { readings += it }
            n += count
        }
        return readings
    }

    private fun sineRmsDb(amplitude: Double) = 20 * log10(amplitude / sqrt(2.0))

    @Test
    fun steadySineReadsItsRmsLevelOnEveryWeighting() {
        val meter = SoundLevelMeter().apply { start(fs) }
        val last = meter.feed(3.0, 0.1).last()
        val expected = sineRmsDb(0.1)
        for (w in FrequencyWeighting.entries) {
            for (t in TimeWeighting.entries) {
                assertEquals("$w $t", expected, last[w][t].levelDb.toDouble(), 0.1)
            }
            assertEquals("Leq $w", expected, last[w].leqDb.toDouble(), 0.1)
            assertEquals("Peak $w", 20 * log10(0.1), last[w].peakMaxDb.toDouble(), 0.1)
        }
    }

    @Test
    fun startupTransientDoesNotPolluteMinimum() {
        val meter = SoundLevelMeter().apply { start(fs) }
        val last = meter.feed(2.0, 0.05).last()
        for (t in TimeWeighting.entries) {
            assertEquals("$t", sineRmsDb(0.05), last[FrequencyWeighting.A][t].minDb.toDouble(), 0.3)
        }
    }

    @Test
    fun weightingIsAppliedToLevels() {
        val meter = SoundLevelMeter().apply { start(fs) }
        val last = meter.feed(3.0, 0.1, frequency = 100.0).last()
        val z = last[FrequencyWeighting.Z].leqDb
        assertEquals(-19.1, (last[FrequencyWeighting.A].leqDb - z).toDouble(), 0.2)
        assertEquals(-0.3, (last[FrequencyWeighting.C].leqDb - z).toDouble(), 0.2)
    }

    /** IEC 61672-1 tone burst response: δ = 10·log10(1 − e^(−Tb/τ)). */
    @Test
    fun toneBurstMaxResponseMatchesStandard() {
        val cases = listOf(
            TimeWeighting.FAST to 0.2,
            TimeWeighting.FAST to 0.01,
            TimeWeighting.SLOW to 0.2,
        )
        for ((tw, burst) in cases) {
            val meter = SoundLevelMeter().apply { start(fs) }
            meter.feed(1.0, 1e-6, frequency = 4000.0)
            meter.reset()
            val warm = (SoundLevelMeter.WARMUP_SECONDS * fs).toLong()
            meter.feed(burst, 0.1, frequency = 4000.0, startSample = warm)
            val readings = meter.feed(3.0, 1e-6, frequency = 4000.0)
            val steady = sineRmsDb(0.1) + FrequencyWeighting.A.referenceGainDb(4000.0)
            val expected = 10 * log10(1 - exp(-burst / tw.tauSeconds))
            val actual = readings.last()[FrequencyWeighting.A][tw].maxDb - steady
            assertEquals("$tw ${burst}s", expected, actual, 0.3)
        }
    }

    @Test
    fun decayRatesMatchTimeConstants() {
        val expectations = mapOf(
            TimeWeighting.FAST to 34.7,
            TimeWeighting.SLOW to 4.3,
            TimeWeighting.IMPULSE to 2.9,
        )
        for ((tw, rate) in expectations) {
            val meter = SoundLevelMeter().apply { start(fs) }
            meter.feed(3.0, 0.1)
            val tail = meter.feed(1.0, 1e-7)
            // Readings every 100 ms: compare 0.3 s and 0.5 s after the stop.
            val l1 = tail[2][FrequencyWeighting.Z][tw].levelDb
            val l2 = tail[4][FrequencyWeighting.Z][tw].levelDb
            assertEquals("$tw", rate, ((l1 - l2) / 0.2), rate * 0.05)
        }
    }

    @Test
    fun leqIsEnergyAverageOfTheSignal() {
        val meter = SoundLevelMeter().apply { start(fs) }
        meter.feed(2.0, 0.1)
        val last = meter.feed(2.0, 0.01).last()
        // Warm-up removes 0.5 s of the first tone: 1.5 s at -23 dB, 2 s at -43 dB.
        val e = (1.5 * 0.005 + 2.0 * 0.00005) / 3.5
        assertEquals(10 * log10(e), last[FrequencyWeighting.Z].leqDb.toDouble(), 0.15)
        assertEquals(3.5, last.durationSeconds, 0.01)
    }

    @Test
    fun pauseAndResumeKeepStatistics() {
        val meter = SoundLevelMeter().apply { start(fs) }
        meter.feed(2.5, 0.1)
        meter.start(fs)
        val last = meter.feed(2.5, 0.1).last()
        assertEquals(4.0, last.durationSeconds, 0.01)
        assertEquals(sineRmsDb(0.1), last[FrequencyWeighting.A].leqDb.toDouble(), 0.1)
        assertEquals(sineRmsDb(0.1), last[FrequencyWeighting.A][TimeWeighting.FAST].minDb.toDouble(), 0.3)
    }

    @Test
    fun overloadIsDetectedAndLatched() {
        val meter = SoundLevelMeter().apply { start(fs) }
        val loud = meter.feed(1.0, 1.0)
        assertTrue(loud.last().overload)
        val quiet = meter.feed(1.0, 0.1, startSample = fs.toLong())
        assertFalse(quiet.last().overload)
        assertTrue(quiet.last().overloadLatched)
        meter.reset()
        assertFalse(meter.feed(0.5, 0.1).last().overloadLatched)
    }

    @Test
    fun silencedCaptureIsExcludedFromStatistics() {
        val meter = SoundLevelMeter().apply { start(fs) }
        meter.feed(2.5, 0.1)
        val silenced = meter.feed(1.0, 0.0)
        assertTrue(silenced.isNotEmpty())
        assertTrue(silenced.last().signalLost)
        val last = meter.feed(2.5, 0.1).last()
        assertFalse(last.signalLost)
        assertEquals(4.0, last.durationSeconds, 0.01)
        assertEquals(sineRmsDb(0.1), last[FrequencyWeighting.A][TimeWeighting.SLOW].minDb.toDouble(), 0.3)
        assertEquals(sineRmsDb(0.1), last[FrequencyWeighting.A].leqDb.toDouble(), 0.1)
    }

    @Test
    fun resetClearsStatistics() {
        val meter = SoundLevelMeter().apply { start(fs) }
        meter.feed(2.0, 0.5)
        meter.feed(1.0, 0.01, startSample = 2L * fs)
        meter.reset()
        val last = meter.feed(2.0, 0.01, startSample = 3L * fs).last()
        assertEquals(sineRmsDb(0.01), last[FrequencyWeighting.A].leqDb.toDouble(), 0.3)
        assertEquals(20 * log10(0.01), last[FrequencyWeighting.Z].peakMaxDb.toDouble(), 0.3)
    }
}
