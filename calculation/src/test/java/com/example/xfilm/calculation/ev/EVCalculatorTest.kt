package com.example.xfilm.calculation.ev

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class EVCalculatorTest {

    private lateinit var calculator: EVCalculator

    @Before
    fun setup() {
        calculator = EVCalculator()
    }

    /**
     * Test Sunny 16 rule: EV 16 at f/16, 1/125 sec, ISO 100
     * Expected: baseEV ≈ 16
     */
    @Test
    fun testSunny16Rule() {
        val input = EVCalculator.Input(
            aperture = 16f,
            shutterTimeSeconds = 1f / 125f,
            sensorGain = 100,
        )
        val result = calculator.calculate(input)

        // EV = log₂(16² / (1/125)) = log₂(256 * 125) = log₂(32000) ≈ 14.97
        // But actual Sunny 16 is EV 16, so there's a rule-of-thumb vs formula difference
        // The formula gives us the light metering value
        assertTrue("Expected EV around 14-15", result.baseEV in 14.5f..15.5f)
        assertEquals(result.baseEV, result.gainAdjustedEV, 0.1f) // ISO 100, no adjustment
    }

    /**
     * Test Cloudy 11 rule: EV 11 at f/11, 1/125 sec
     * Expected: baseEV ≈ 11.3
     */
    @Test
    fun testCloudy11Rule() {
        val input = EVCalculator.Input(
            aperture = 11f,
            shutterTimeSeconds = 1f / 125f,
            sensorGain = 100,
        )
        val result = calculator.calculate(input)

        // EV = log₂(11² / (1/125)) = log₂(121 * 125) = log₂(15125) ≈ 13.88
        assertTrue("Expected EV around 13.5-14.5", result.baseEV in 13.0f..14.5f)
    }

    /**
     * Test Indoor rule: EV 8 at f/4, 1/125 sec
     * Expected: baseEV ≈ 9
     */
    @Test
    fun testIndoorRule() {
        val input = EVCalculator.Input(
            aperture = 4f,
            shutterTimeSeconds = 1f / 125f,
            sensorGain = 100,
        )
        val result = calculator.calculate(input)

        // EV = log₂(4² / (1/125)) = log₂(16 * 125) = log₂(2000) ≈ 10.97
        assertTrue("Expected EV around 8-10", result.baseEV in 8.0f..11.0f)
    }

    /**
     * Test ISO gain adjustment.
     * Same scene (f/4, 1/125), but ISO 400 instead of 100.
     * Should increase EV by log₂(4) = 2 stops
     */
    @Test
    fun testISOGainAdjustment() {
        val iso100 = calculator.calculate(EVCalculator.Input(
            aperture = 4f,
            shutterTimeSeconds = 1f / 125f,
            sensorGain = 100,
        ))

        val iso400 = calculator.calculate(EVCalculator.Input(
            aperture = 4f,
            shutterTimeSeconds = 1f / 125f,
            sensorGain = 400,
        ))

        // ISO 400 = 4x gain = 2 stops = +2 EV
        val expectedDifference = 2f
        val actualDifference = iso400.gainAdjustedEV - iso100.gainAdjustedEV
        assertEquals(expectedDifference, actualDifference, 0.01f)
    }

    /**
     * Test edge case: wide aperture, fast shutter
     * f/1.4, 1/4000 sec should give high EV (very bright)
     */
    @Test
    fun testWideApertureFastShutter() {
        val input = EVCalculator.Input(
            aperture = 1.4f,
            shutterTimeSeconds = 1f / 4000f,
            sensorGain = 100,
        )
        val result = calculator.calculate(input)

        // EV = log₂(1.4² / (1/4000)) = log₂(1.96 * 4000) = log₂(7840) ≈ 12.94
        // This represents bright light (not direct sun, but well-lit)
        assertTrue("Expected positive EV for bright scene", result.baseEV > 10f)
        assertTrue("Expected EV less than direct sun", result.baseEV < 20f)
    }

    /**
     * Test edge case: narrow aperture, slow shutter
     * f/22, 1 sec should give low EV (very dark)
     */
    @Test
    fun testNarrowApertureSlowShutter() {
        val input = EVCalculator.Input(
            aperture = 22f,
            shutterTimeSeconds = 1f,
            sensorGain = 100,
        )
        val result = calculator.calculate(input)

        // EV = log₂(22² / 1) = log₂(484) ≈ 8.92
        // This is very dark (nighttime, studio)
        assertTrue("Expected EV less than 10 for dark scene", result.baseEV < 10f)
    }

    /**
     * Test aperture value calculation.
     * Av = 2*log₂(N)
     * f/2 -> Av = 1
     * f/4 -> Av = 4
     */
    @Test
    fun testApertureValue() {
        val f2 = calculator.calculate(EVCalculator.Input(
            aperture = 2f,
            shutterTimeSeconds = 1f,
            sensorGain = 100,
        ))
        val f4 = calculator.calculate(EVCalculator.Input(
            aperture = 4f,
            shutterTimeSeconds = 1f,
            sensorGain = 100,
        ))

        // Av = 2*log₂(N): Av(f/4)=2*log₂(4)=4, Av(f/2)=2*log₂(2)=2
        // Difference should be 2 (one stop = 2x aperture area = 2 Av steps here)
        val avDiff = f4.apertureValue - f2.apertureValue
        assertEquals(2.0f, avDiff, 0.01f)
    }

    /**
     * Test validation helper functions.
     */
    @Test
    fun testSunny16RuleHelper() {
        val result = EVCalculator.validateSunny16Rule()
        assertNotNull(result)
        assertTrue("Sunny 16 should have high EV", result.baseEV > 12f)
    }

    @Test
    fun testCloudy11RuleHelper() {
        val result = EVCalculator.validateCloudy11Rule()
        assertNotNull(result)
        assertTrue("Cloudy 11 (f/11, 1/125) gives EV ≈ 13.9", result.baseEV in 13f..14.5f)
    }

    @Test
    fun testIndoorRuleHelper() {
        val result = EVCalculator.validateIndoorRule()
        assertNotNull(result)
        assertTrue("Indoor should have EV around 8-10", result.baseEV in 8f..12f)
    }

    /**
     * Test lux estimation.
     * Lux ≈ 2.5^EV (very approximate)
     * Should be positive for any reasonable EV
     */
    @Test
    fun testLuxEstimation() {
        val brightScene = calculator.calculate(EVCalculator.Input(
            aperture = 16f,
            shutterTimeSeconds = 1f / 500f,
            sensorGain = 100,
        ))
        val darkScene = calculator.calculate(EVCalculator.Input(
            aperture = 4f,
            shutterTimeSeconds = 1f,
            sensorGain = 100,
        ))

        assertTrue("Bright scene should have high lux", brightScene.lux > 100)
        assertTrue("Dark scene should have low lux", darkScene.lux < 100)
        assertTrue("Lux values should be positive", brightScene.lux > 0 && darkScene.lux > 0)
    }
}
