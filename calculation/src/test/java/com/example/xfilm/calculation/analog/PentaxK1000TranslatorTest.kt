package com.example.xfilm.calculation.analog

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PentaxK1000TranslatorTest {

    private lateinit var translator: PentaxK1000Translator

    @Before
    fun setup() {
        translator = PentaxK1000Translator()
    }

    /**
     * Test that APERTURES list contains expected values.
     */
    @Test
    fun testAperturesList() {
        assertEquals(7, PentaxK1000Translator.APERTURES.size)
        assertTrue(PentaxK1000Translator.APERTURES.map { it.value }.contains(2f))
        assertTrue(PentaxK1000Translator.APERTURES.map { it.value }.contains(16f))
    }

    /**
     * Test that SHUTTER_SPEEDS list has expected values.
     */
    @Test
    fun testShutterSpeedsList() {
        assertEquals(11, PentaxK1000Translator.SHUTTER_SPEEDS.size)
        assertTrue(PentaxK1000Translator.SHUTTER_SPEEDS.any { it.seconds == 1f / 125f })
        assertTrue(PentaxK1000Translator.SHUTTER_SPEEDS.any { it.seconds == 1f / 1000f })
    }

    /**
     * Test EV translation for bright scene (EV ~16, Sunny 16 rule).
     * Should suggest f/16 + 1/125 as one option.
     */
    @Test
    fun testEVTranslationSunnyScene() {
        val ev = 15f  // Approximate Sunny 16
        val configs = translator.translateEV(ev, maxResults = 5)

        assertTrue("Configs should not be empty", configs.isNotEmpty())
        assertTrue("Should have multiple options", configs.size >= 3)

        // Check that at least one config is close to f/16 aperture
        val hasWideAperture = configs.any { it.aperture.value >= 16f }
        assertTrue("Should have wide aperture option for bright scene", hasWideAperture)
    }

    /**
     * Test EV translation for dark scene (EV ~9, indoor artificial light).
     * Should suggest f/4 or wider.
     */
    @Test
    fun testEVTranslationDarkScene() {
        val ev = 9f  // Indoor artificial light
        val configs = translator.translateEV(ev, maxResults = 5)

        assertTrue("Configs should not be empty", configs.isNotEmpty())

        // Check that at least one config is f/4 or wider (smaller f-number)
        val hasWideAperture = configs.any { it.aperture.value <= 4f }
        assertTrue("Should have wide aperture option for dark scene", hasWideAperture)
    }

    /**
     * Test that all returned configs are valid.
     */
    @Test
    fun testAllReturnedConfigsAreValid() {
        val ev = 12f
        val configs = translator.translateEV(ev, maxResults = 10)

        for (config in configs) {
            assertTrue("Config should be valid", translator.isValidConfig(config))
            assertEquals("Film speed should be ASA 100", 100, config.filmSpeed)
        }
    }

    /**
     * Test getBestConfig returns single best match.
     */
    @Test
    fun testGetBestConfig() {
        val ev = 14f
        val best = translator.getBestConfig(ev)

        assertNotNull(best)
        assertNotNull(best!!.aperture)
        assertNotNull(best.shutter)
        assertEquals(100, best.filmSpeed)
    }

    /**
     * Test getPresetsForEV returns diverse options.
     */
    @Test
    fun testGetPresetsForEV() {
        val ev = 11f
        val presets = translator.getPresetsForEV(ev)

        assertTrue("Should return at least 1 preset", presets.size >= 1)
        assertTrue("Should return at most 3 presets", presets.size <= 3)

        // All presets should be valid
        for (preset in presets) {
            assertTrue(translator.isValidConfig(preset))
        }
    }

    /**
     * Test that configs are ordered by EV closeness.
     */
    @Test
    fun testConfigsOrderedByClosestEV() {
        val targetEV = 12f
        val configs = translator.translateEV(targetEV, maxResults = 10)

        // First config should be closest to target
        val firstDistance = kotlin.math.abs(configs[0].evValue - targetEV)

        for (i in 1 until configs.size) {
            val distance = kotlin.math.abs(configs[i].evValue - targetEV)
            assertTrue(
                "Configs should be sorted by EV closeness",
                firstDistance <= distance
            )
        }
    }

    /**
     * Test AnalogConfig display string.
     */
    @Test
    fun testAnalogConfigDisplay() {
        val config = AnalogConfig(
            aperture = ApertureStop(4f),
            shutter = ShutterSpeed.fromFraction(1, 125),
        )
        val display = config.toString()
        assertTrue("Display should contain aperture", display.contains("f/4"))
        assertTrue("Display should contain shutter", display.contains("1/125"))
    }

    /**
     * Test ApertureStop comparison.
     */
    @Test
    fun testApertureStopComparison() {
        val f2 = ApertureStop(2f)
        val f4 = ApertureStop(4f)
        val f4again = ApertureStop(4f)

        assertTrue(f2 < f4)
        assertEquals(f4, f4again)
    }

    /**
     * Test ShutterSpeed comparison.
     */
    @Test
    fun testShutterSpeedComparison() {
        val slow = ShutterSpeed(1f, "1\"")
        val fast = ShutterSpeed(1f / 1000f, "1/1000")

        assertTrue(fast < slow)
    }

    /**
     * Test ShutterSpeed.fromFraction factory.
     */
    @Test
    fun testShutterSpeedFactory() {
        val speed = ShutterSpeed.fromFraction(1, 125)
        assertEquals(1f / 125f, speed.seconds, 0.0001f)
        assertEquals("1/125", speed.display)
    }

    /**
     * Test maxResults limits returned configs.
     */
    @Test
    fun testMaxResultsLimit() {
        val ev = 12f
        val all = translator.translateEV(ev, maxResults = Int.MAX_VALUE)
        val limited = translator.translateEV(ev, maxResults = 3)

        assertTrue("Limited should have fewer results", limited.size <= all.size)
        assertEquals(3, limited.size)
    }

    /**
     * Test that all aperture values are between f/1.4 and f/32 (reasonable range).
     */
    @Test
    fun testApertureValuesInReasonableRange() {
        for (aperture in PentaxK1000Translator.APERTURES) {
            assertTrue("Aperture should be > 1", aperture.value > 1f)
            assertTrue("Aperture should be < 32", aperture.value < 32f)
        }
    }

    /**
     * Test that all shutter speeds are positive.
     */
    @Test
    fun testShutterSpeedsArePositive() {
        for (speed in PentaxK1000Translator.SHUTTER_SPEEDS) {
            assertTrue("Shutter speed should be positive", speed.seconds > 0f)
        }
    }
}
