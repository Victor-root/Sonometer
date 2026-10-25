package com.example.djmeter.utils

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Frequency weightings defined by IEC 61672-1.
 *
 * The analog poles are the exact values of the standard. Their digital
 * version (see [WeightingFilter]) is obtained with the bilinear transform and
 * stays within Class 1 tolerances up to 20 kHz at 44.1 kHz and 48 kHz.
 */
enum class FrequencyWeighting {
    A, C, Z;

    /** Analog response of the standard in dB, normalised to 0 dB at 1 kHz. */
    fun referenceGainDb(frequencyHz: Double): Double {
        val f2 = frequencyHz * frequencyHz
        return when (this) {
            A -> 20.0 * log10(
                F4_SQ * f2 * f2 /
                    ((f2 + F1_SQ) * sqrt((f2 + F2 * F2) * (f2 + F3 * F3)) * (f2 + F4_SQ))
            ) - A1000_DB
            C -> 20.0 * log10(F4_SQ * f2 / ((f2 + F1_SQ) * (f2 + F4_SQ))) - C1000_DB
            Z -> 0.0
        }
    }

    internal companion object {
        const val F1 = 20.598997
        const val F2 = 107.65265
        const val F3 = 737.86223
        const val F4 = 12194.217
        const val F1_SQ = F1 * F1
        const val F4_SQ = F4 * F4
        const val A1000_DB = -2.000
        const val C1000_DB = -0.062
    }
}

/**
 * Digital frequency weighting filter: a cascade of first-order sections, one
 * per analog pole, each discretised with the bilinear transform.
 */
class WeightingFilter(weighting: FrequencyWeighting, private val sampleRate: Int) {

    private val sections: Array<FirstOrderSection>

    init {
        val w = FrequencyWeighting
        val (zerosAtDc, poles) = when (weighting) {
            FrequencyWeighting.A -> 4 to doubleArrayOf(w.F1, w.F1, w.F4, w.F4, w.F2, w.F3)
            FrequencyWeighting.C -> 2 to doubleArrayOf(w.F1, w.F1, w.F4, w.F4)
            FrequencyWeighting.Z -> 0 to doubleArrayOf()
        }
        val k = 2.0 * sampleRate
        sections = Array(poles.size) { i ->
            val wp = 2.0 * PI * poles[i]
            val a0 = k + wp
            val a1 = (wp - k) / a0
            if (i < zerosAtDc) FirstOrderSection(k / a0, -k / a0, a1)
            else FirstOrderSection(1.0 / a0, 1.0 / a0, a1)
        }
        if (sections.isNotEmpty()) sections[0].scale(1.0 / magnitudeAt(1000.0))
    }

    fun process(x: Double): Double {
        var y = x
        for (s in sections) y = s.process(y)
        return y
    }

    fun reset() {
        for (s in sections) s.reset()
    }

    /** Magnitude of the digital response at [frequencyHz] (linear). */
    fun magnitudeAt(frequencyHz: Double): Double {
        val omega = 2.0 * PI * frequencyHz / sampleRate
        val cosW = cos(omega)
        val sinW = sin(omega)
        var magnitude = 1.0
        for (s in sections) {
            // H(z) = (b0 + b1 z^-1) / (1 + a1 z^-1), z^-1 = cos(w) - j sin(w)
            val num = hypot(s.b0 + s.b1 * cosW, -s.b1 * sinW)
            val den = hypot(1.0 + s.a1 * cosW, -s.a1 * sinW)
            magnitude *= num / den
        }
        return magnitude
    }

    private class FirstOrderSection(var b0: Double, var b1: Double, val a1: Double) {
        private var x1 = 0.0
        private var y1 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 - a1 * y1
            x1 = x
            y1 = y
            return y
        }

        fun scale(g: Double) {
            b0 *= g
            b1 *= g
        }

        fun reset() {
            x1 = 0.0
            y1 = 0.0
        }
    }
}
