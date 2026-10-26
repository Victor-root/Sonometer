package app.sonometer.vroot.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

class FrequencyWeightingTest {

    /** IEC 61672-1:2013 Table 3: nominal frequency, A, C, Class 1 limits. */
    private val table = listOf(
        Row(10.0, -70.4, -14.3, 3.5, Double.NEGATIVE_INFINITY),
        Row(12.5, -63.4, -11.2, 3.0, Double.NEGATIVE_INFINITY),
        Row(16.0, -56.7, -8.5, 2.5, -4.5),
        Row(20.0, -50.5, -6.2, 2.5, -2.5),
        Row(25.0, -44.7, -4.4, 2.5, -2.0),
        Row(31.5, -39.4, -3.0, 2.0, -2.0),
        Row(40.0, -34.6, -2.0, 1.5, -1.5),
        Row(50.0, -30.2, -1.3, 1.5, -1.5),
        Row(63.0, -26.2, -0.8, 1.5, -1.5),
        Row(80.0, -22.5, -0.5, 1.5, -1.5),
        Row(100.0, -19.1, -0.3, 1.5, -1.5),
        Row(125.0, -16.1, -0.2, 1.5, -1.5),
        Row(160.0, -13.4, -0.1, 1.5, -1.5),
        Row(200.0, -10.9, 0.0, 1.5, -1.5),
        Row(250.0, -8.6, 0.0, 1.4, -1.4),
        Row(315.0, -6.6, 0.0, 1.4, -1.4),
        Row(400.0, -4.8, 0.0, 1.4, -1.4),
        Row(500.0, -3.2, 0.0, 1.4, -1.4),
        Row(630.0, -1.9, 0.0, 1.4, -1.4),
        Row(800.0, -0.8, 0.0, 1.4, -1.4),
        Row(1000.0, 0.0, 0.0, 1.1, -1.1),
        Row(1250.0, 0.6, 0.0, 1.4, -1.4),
        Row(1600.0, 1.0, -0.1, 1.6, -1.6),
        Row(2000.0, 1.2, -0.2, 1.6, -1.6),
        Row(2500.0, 1.3, -0.3, 1.6, -1.6),
        Row(3150.0, 1.2, -0.5, 1.6, -1.6),
        Row(4000.0, 1.0, -0.8, 1.6, -1.6),
        Row(5000.0, 0.5, -1.3, 2.1, -2.1),
        Row(6300.0, -0.1, -2.0, 2.1, -2.6),
        Row(8000.0, -1.1, -3.0, 2.1, -3.1),
        Row(10000.0, -2.5, -4.4, 2.6, -3.6),
        Row(12500.0, -4.3, -6.2, 3.0, -6.0),
        Row(16000.0, -6.6, -8.5, 3.5, -17.0),
        Row(20000.0, -9.3, -11.2, 4.0, Double.NEGATIVE_INFINITY),
    )

    /** Table values are computed at the exact base-10 frequency, not the nominal one. */
    private data class Row(val nominal: Double, val a: Double, val c: Double, val upper: Double, val lower: Double) {
        val f: Double = 1000.0 * 10.0.pow(round(10 * log10(nominal / 1000.0)) / 10.0)
    }

    @Test
    fun referenceFormulaMatchesStandardTable() {
        for (r in table) {
            assertEquals("A @ ${r.nominal}", r.a, FrequencyWeighting.A.referenceGainDb(r.f), 0.1)
            assertEquals("C @ ${r.nominal}", r.c, FrequencyWeighting.C.referenceGainDb(r.f), 0.1)
        }
    }

    @Test
    fun digitalFiltersAreWithinClass1Tolerances() {
        for (sampleRate in intArrayOf(44100, 48000)) {
            for (w in listOf(FrequencyWeighting.A, FrequencyWeighting.C, FrequencyWeighting.Z)) {
                val filter = WeightingFilter(w, sampleRate)
                for (r in table) {
                    if (r.f >= sampleRate / 2.0) continue
                    val expected = when (w) {
                        FrequencyWeighting.A -> r.a
                        FrequencyWeighting.C -> r.c
                        FrequencyWeighting.Z -> 0.0
                    }
                    val actual = 20.0 * log10(filter.magnitudeAt(r.f))
                    val deviation = actual - expected
                    assertTrue(
                        "$w @ ${r.nominal} Hz, ${sampleRate} Hz: deviation $deviation dB",
                        deviation <= r.upper && deviation >= r.lower,
                    )
                }
            }
        }
    }

    @Test
    fun filtersAreExactAt1kHz() {
        for (sampleRate in intArrayOf(44100, 48000)) {
            assertEquals(0.0, 20 * log10(WeightingFilter(FrequencyWeighting.A, sampleRate).magnitudeAt(1000.0)), 1e-9)
            assertEquals(0.0, 20 * log10(WeightingFilter(FrequencyWeighting.C, sampleRate).magnitudeAt(1000.0)), 1e-9)
        }
    }
}
