package com.example.xfilm.calculation.analog

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Translates EV (Exposure Value) to valid Pentax K1000 camera configurations.
 *
 * Pentax K1000 specs:
 * - Fixed apertures: f/2, f/2.8, f/4, f/5.6, f/8, f/11, f/16
 * - Shutter speeds: Bulb, 1, 1/2, 1/4, 1/8, 1/15, 1/30, 1/60, 1/125, 1/250, 1/500, 1/1000
 * - Film speed: ASA 25-3200 (we focus on ASA 100)
 *
 * For ASA 100, the base EV formula applies directly.
 * This translator generates the closest valid combinations.
 */
data class ApertureStop(
    val value: Float,
    val name: String = "f/$value",
) : Comparable<ApertureStop> {
    override fun compareTo(other: ApertureStop) = value.compareTo(other.value)
}

data class ShutterSpeed(
    val seconds: Float,
    val display: String,
) : Comparable<ShutterSpeed> {
    override fun compareTo(other: ShutterSpeed) = seconds.compareTo(other.seconds)

    companion object {
        fun fromFraction(numerator: Int, denominator: Int) = ShutterSpeed(
            seconds = numerator.toFloat() / denominator.toFloat(),
            display = if (numerator == 1) "1/$denominator" else "$numerator"
        )
    }
}

data class AnalogConfig(
    val aperture: ApertureStop,
    val shutter: ShutterSpeed,
    val filmSpeed: Int = 100,
    val evValue: Float = 0f,
) {
    override fun toString() = "${aperture.name} at ${shutter.display}"
}

class PentaxK1000Translator {

    companion object {
        val APERTURES = listOf(
            ApertureStop(2f),
            ApertureStop(2.8f),
            ApertureStop(4f),
            ApertureStop(5.6f),
            ApertureStop(8f),
            ApertureStop(11f),
            ApertureStop(16f),
        )

        val SHUTTER_SPEEDS = listOf(
            ShutterSpeed(1f, "1\""),          // Bulb (1 second equivalent)
            ShutterSpeed(0.5f, "1/2"),
            ShutterSpeed(0.25f, "1/4"),
            ShutterSpeed(1f / 8f, "1/8"),
            ShutterSpeed(1f / 15f, "1/15"),
            ShutterSpeed(1f / 30f, "1/30"),
            ShutterSpeed(1f / 60f, "1/60"),
            ShutterSpeed(1f / 125f, "1/125"),
            ShutterSpeed(1f / 250f, "1/250"),
            ShutterSpeed(1f / 500f, "1/500"),
            ShutterSpeed(1f / 1000f, "1/1000"),
        )

        const val FILM_SPEED_ASA = 100
    }

    /**
     * Generates all valid Pentax K1000 configurations for a given EV value.
     *
     * @param ev Target EV value
     * @param maxResults Maximum number of results to return (ordered by closest match)
     * @return List of AnalogConfig sorted by distance from target EV
     */
    fun translateEV(ev: Float, maxResults: Int = 5): List<AnalogConfig> {
        val configs = mutableListOf<AnalogConfig>()

        for (aperture in APERTURES) {
            for (shutter in SHUTTER_SPEEDS) {
                // Calculate EV for this combination
                // EV = log₂(N² / t)
                // N = aperture, t = shutter time
                val configEV = calculateEV(aperture.value, shutter.seconds)
                val evError = abs(configEV - ev)

                configs.add(AnalogConfig(
                    aperture = aperture,
                    shutter = shutter,
                    filmSpeed = FILM_SPEED_ASA,
                    evValue = configEV,
                ))
            }
        }

        // Sort by closest to target EV
        return configs.sortedBy { abs(it.evValue - ev) }
            .take(maxResults)
    }

    /**
     * Gets the best single configuration for a given EV.
     */
    fun getBestConfig(ev: Float): AnalogConfig? = translateEV(ev, 1).firstOrNull()

    /**
     * Gets common "presets" for typical photography scenarios.
     */
    fun getPresetsForEV(ev: Float): List<AnalogConfig> {
        val all = translateEV(ev, Int.MAX_VALUE)
        // Return diverse options: fast aperture, standard, narrow aperture
        val results = mutableListOf<AnalogConfig>()

        // Fast aperture (wide open)
        all.find { it.aperture.value <= 2.8f }?.let { results.add(it) }

        // Standard (mid-range aperture)
        all.find { it.aperture.value in 4f..8f }?.let { results.add(it) }

        // Stopped down (for depth of field)
        all.find { it.aperture.value >= 11f }?.let { results.add(it) }

        return results.take(3)
    }

    /**
     * Calculates EV from aperture and shutter time.
     * This mirrors EVCalculator but is kept here for convenience.
     */
    private fun calculateEV(aperture: Float, shutterSeconds: Float): Float {
        return kotlin.math.log2(aperture * aperture / shutterSeconds)
    }

    /**
     * Validates if a configuration would work on Pentax K1000.
     */
    fun isValidConfig(config: AnalogConfig): Boolean {
        return APERTURES.contains(config.aperture) &&
            SHUTTER_SPEEDS.contains(config.shutter) &&
            config.filmSpeed == FILM_SPEED_ASA
    }
}
