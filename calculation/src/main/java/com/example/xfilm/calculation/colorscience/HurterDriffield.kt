package com.example.xfilm.calculation.colorscience

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Represents the Hurter-Driffield (H&D) curve of photographic film emulsion.
 *
 * The H&D curve characterizes the optical density response of film to light exposure.
 * It has three distinct zones:
 * 1. Toe (shadows): Non-linear compression, low contrast
 * 2. Linear (midtones): Proportional response, neutral gamma
 * 3. Shoulder (highlights): Non-linear compression, highlight rolloff
 *
 * For this application, we use a parametric model to approximate real film curves.
 *
 * References:
 * - Kodak Tri-X 400 (BW)
 * - Fujifilm Portra 400 (color)
 * - Kodak Ektachrome E100 (slide)
 */
data class HurterDriffield(
    /**
     * Gamma value for the linear region (typically 1.8-2.4).
     * Higher gamma = higher contrast.
     */
    val gamma: Float = 2.2f,

    /**
     * Lowest output density (shadows don't go fully black).
     * Typical: 0.01 - 0.05
     */
    val blackPoint: Float = 0.01f,

    /**
     * Highest output density (highlights compressed).
     * Typical: 0.90 - 0.98
     */
    val whitePoint: Float = 0.95f,

    /**
     * Slope of the toe region.
     * Lower = gentler shadows, higher = steeper toe.
     * Typical: 0.3 - 0.7
     */
    val toeSlope: Float = 0.5f,

    /**
     * Threshold where toe transitions to linear.
     * Typical: 0.1 - 0.3
     */
    val toeThreshold: Float = 0.2f,

    /**
     * Name of the film this curve approximates.
     */
    val name: String = "Generic BW",
) {

    /**
     * Applies the H&D tone curve to a normalized [0,1] input value.
     * Returns a normalized [0,1] output value mapped into [blackPoint, whitePoint].
     *
     * Implementation: a monotonic filmic S-curve.
     *   s(x) = x^a / (x^a + (1-x)^a),  a = gamma
     * This naturally produces:
     *   - A toe (shadow compression) near x=0
     *   - A higher-contrast linear region in the midtones
     *   - A shoulder (highlight rolloff) near x=1
     * The S-curve output [0,1] is then scaled into [blackPoint, whitePoint],
     * guaranteeing toneMap(0)=blackPoint and toneMap(1)=whitePoint.
     */
    fun toneMap(input: Float): Float {
        val x = input.coerceIn(0f, 1f)

        // Exact endpoints (avoids 0^a / 0 indeterminacy)
        val sCurve = when {
            x <= 0f -> 0f
            x >= 1f -> 1f
            else -> {
                val xa = x.pow(gamma)
                val ixa = (1f - x).pow(gamma)
                xa / (xa + ixa)
            }
        }

        return (blackPoint + sCurve * (whitePoint - blackPoint))
            .coerceIn(0f, 1f)
    }

    /**
     * Maps the entire [0,1] range to output densities.
     * Useful for generating LUT3D data.
     */
    fun generateToneCurve(samples: Int = 256): FloatArray {
        return FloatArray(samples) { i ->
            val input = i / (samples - 1).toFloat()
            toneMap(input)
        }
    }

    override fun toString(): String = """
        HurterDriffield($name):
          gamma=$gamma,
          blackPoint=$blackPoint,
          whitePoint=$whitePoint,
          toeSlope=$toeSlope,
          toeThreshold=$toeThreshold
    """.trimIndent()

    companion object {
        /**
         * Kodak Tri-X 400 (classic BW film).
         * High contrast, slight greenish cast, characteristic grain.
         */
        fun kodakTriX400(): HurterDriffield = HurterDriffield(
            gamma = 2.2f,
            blackPoint = 0.02f,
            whitePoint = 0.96f,
            toeSlope = 0.45f,
            toeThreshold = 0.18f,
            name = "Kodak Tri-X 400",
        )

        /**
         * Kodak Portra 400 (color negative).
         * Soft shadows, smooth rolloff, warm tone.
         */
        fun kodakPortra400(): HurterDriffield = HurterDriffield(
            gamma = 1.9f,
            blackPoint = 0.01f,
            whitePoint = 0.94f,
            toeSlope = 0.55f,
            toeThreshold = 0.25f,
            name = "Kodak Portra 400",
        )

        /**
         * Fujifilm Velvia (slide film).
         * High contrast, saturated, punchy blacks.
         */
        fun fujiFujivelvia(): HurterDriffield = HurterDriffield(
            gamma = 2.4f,
            blackPoint = 0.03f,
            whitePoint = 0.97f,
            toeSlope = 0.35f,
            toeThreshold = 0.15f,
            name = "Fujifilm Velvia 50",
        )

        /**
         * Generic linear response (no curve).
         * For reference/testing.
         */
        fun linear(): HurterDriffield = HurterDriffield(
            gamma = 1f,
            blackPoint = 0f,
            whitePoint = 1f,
            toeSlope = 1f,
            toeThreshold = 0f,
            name = "Linear (no curve)",
        )
    }
}
