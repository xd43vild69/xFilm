package com.example.xfilm.calculation.ev

import kotlin.math.log2
import kotlin.math.pow

/**
 * Calculates Exposure Value (EV) from camera sensor parameters.
 *
 * Formula: EV = log₂(N² / t)
 * Where: N = f-number (aperture), t = exposure time in seconds
 *
 * References:
 * - Sunny 16 rule: EV 16 at f/16 and 1/ISO shutter speed (for ASA 100 -> 1/100 ~= 1/125)
 * - EV scale: each step is one stop (2x brightness change)
 */
class EVCalculator {

    data class Input(
        val aperture: Float,        // f-stop, e.g., 2.8, 4.0, 5.6
        val shutterTimeSeconds: Float,  // seconds, e.g., 1.0, 0.008 (1/125)
        val sensorGain: Int = 100,  // ISO or sensor gain
    )

    data class EVResult(
        val baseEV: Float,          // EV at given aperture and shutter (without gain adjustment)
        val gainAdjustedEV: Float,  // EV adjusted for ISO gain (more common)
        val lux: Float,             // Estimated scene luminance in lux
        val apertureValue: Float,   // Av = 2*log₂(N)
        val shutterValue: Float,    // Tv = -log₂(t)
    ) {
        override fun toString(): String = """
            EVResult(
              baseEV=$baseEV,
              gainAdjustedEV=$gainAdjustedEV (ISO ${100}),
              apertureValue=$apertureValue (f/${String.format("%.1f", baseEV)}),
              shutterValue=$shutterValue,
              lux=${String.format("%.1f", lux)}
            )
        """.trimIndent()
    }

    /**
     * Calculates EV from aperture and shutter time.
     *
     * @param input EVCalculator.Input with aperture, shutter time, and optional ISO
     * @return EVResult containing EV values and derived metrics
     */
    fun calculate(input: Input): EVResult {
        // EV100 = log₂(N² / t)
        val ev100 = log2(input.aperture * input.aperture / input.shutterTimeSeconds)

        // Aperture Value: Av = 2*log₂(N)
        val apertureValue = 2 * log2(input.aperture)

        // Shutter Value: Tv = -log₂(t)
        val shutterValue = -log2(input.shutterTimeSeconds)

        // EV adjusted for ISO gain
        // EV_gain = EV100 + log₂(ISO / 100)
        val evGainAdjusted = ev100 + log2(input.sensorGain / 100f)

        // Estimate luminance (lux) from EV
        // Simplified: Lux ≈ 2.5^EV (very approximate)
        val lux = 2.5f.pow(ev100)

        return EVResult(
            baseEV = ev100,
            gainAdjustedEV = evGainAdjusted,
            lux = lux,
            apertureValue = apertureValue,
            shutterValue = shutterValue,
        )
    }

    companion object {
        /**
         * Validates common photography scenarios using Sunny 16 rule.
         * Sunny 16: EV 16 at f/16, shutter = 1/ISO (for ASA 100 ~= 1/125)
         */
        fun validateSunny16Rule(): EVResult {
            val calc = EVCalculator()
            // Sunny 16: f/16, 1/125 sec, ISO 100
            val input = Input(
                aperture = 16f,
                shutterTimeSeconds = 1f / 125f,
                sensorGain = 100,
            )
            return calc.calculate(input)
        }

        /**
         * Validates Cloudy 11 rule.
         * Cloudy 11: EV 11 at f/11, shutter = 1/ISO
         */
        fun validateCloudy11Rule(): EVResult {
            val calc = EVCalculator()
            val input = Input(
                aperture = 11f,
                shutterTimeSeconds = 1f / 125f,
                sensorGain = 100,
            )
            return calc.calculate(input)
        }

        /**
         * Validates Indoor rule.
         * Indoor (artificial light): EV 8 at f/4, shutter = 1/ISO
         */
        fun validateIndoorRule(): EVResult {
            val calc = EVCalculator()
            val input = Input(
                aperture = 4f,
                shutterTimeSeconds = 1f / 125f,
                sensorGain = 100,
            )
            return calc.calculate(input)
        }
    }
}
