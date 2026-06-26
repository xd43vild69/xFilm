package com.example.xfilm.calculation.colorscience

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class HurterDriefieldTest {

    private lateinit var hd: HurterDriffield

    @Before
    fun setup() {
        hd = HurterDriffield()
    }

    /**
     * Test that input 0 maps to blackPoint.
     */
    @Test
    fun testBlackPointMapping() {
        val output = hd.toneMap(0f)
        assertEquals(hd.blackPoint, output, 0.01f)
    }

    /**
     * Test that input 1 maps to whitePoint.
     */
    @Test
    fun testWhitePointMapping() {
        val output = hd.toneMap(1f)
        assertEquals(hd.whitePoint, output, 0.01f)
    }

    /**
     * Test that midtone (0.5) is between black and white.
     */
    @Test
    fun testMidtoneMapping() {
        val output = hd.toneMap(0.5f)
        assertTrue("Midtone should be > blackPoint", output > hd.blackPoint)
        assertTrue("Midtone should be < whitePoint", output < hd.whitePoint)
    }

    /**
     * Test toe region: shadows should be compressed (S-curve).
     * Normalized output at a low input should sit below the linear diagonal.
     */
    @Test
    fun testToeCompression() {
        val toeInput = 0.1f
        val toeOutput = hd.toneMap(toeInput)

        // Normalize output into [0,1] within [blackPoint, whitePoint]
        val normalizedOutput = (toeOutput - hd.blackPoint) / (hd.whitePoint - hd.blackPoint)

        // S-curve compresses shadows: normalized output < input in the toe
        assertTrue(
            "Toe should compress shadows below linear (got $normalizedOutput vs $toeInput)",
            normalizedOutput < toeInput
        )
    }

    /**
     * Test that tone curve is monotonically increasing.
     */
    @Test
    fun testMonotonicIncrease() {
        var prevOutput = hd.toneMap(0f)
        for (i in 1..100) {
            val input = i / 100f
            val output = hd.toneMap(input)
            assertTrue("Tone curve should be monotonically increasing", output >= prevOutput)
            prevOutput = output
        }
    }

    /**
     * Test tone curve generation.
     */
    @Test
    fun testToneCurveGeneration() {
        val samples = 256
        val curve = hd.generateToneCurve(samples)

        assertEquals(samples, curve.size)
        // First sample should be close to blackPoint
        assertTrue(abs(curve[0] - hd.blackPoint) < 0.05f)
        // Last sample should be close to whitePoint
        assertTrue(abs(curve[samples - 1] - hd.whitePoint) < 0.05f)
    }

    /**
     * Test clamping: input outside [0,1] should be clamped.
     */
    @Test
    fun testInputClamping() {
        val negativeInput = hd.toneMap(-0.5f)
        val zeroInput = hd.toneMap(0f)
        assertEquals(negativeInput, zeroInput, 0.001f)

        val largeInput = hd.toneMap(2f)
        val oneInput = hd.toneMap(1f)
        assertEquals(largeInput, oneInput, 0.001f)
    }

    /**
     * Test output clamping: output should always be in [0,1].
     */
    @Test
    fun testOutputClamping() {
        for (i in 0..100) {
            val input = i / 100f
            val output = hd.toneMap(input)
            assertTrue("Output should be >= 0", output >= 0f)
            assertTrue("Output should be <= 1", output <= 1f)
        }
    }

    /**
     * Test Kodak Tri-X 400 curve.
     */
    @Test
    fun testKodakTriX400() {
        val triX = HurterDriffield.kodakTriX400()
        assertNotNull(triX)
        assertEquals("Kodak Tri-X 400", triX.name)
        // Should have high contrast (high gamma)
        assertTrue(triX.gamma > 2f)
    }

    /**
     * Test Kodak Portra 400 curve.
     */
    @Test
    fun testKodakPortra400() {
        val portra = HurterDriffield.kodakPortra400()
        assertNotNull(portra)
        assertEquals("Kodak Portra 400", portra.name)
        // Should have softer tone (lower gamma)
        assertTrue(portra.gamma < 2.2f)
    }

    /**
     * Test Fujifilm Velvia curve.
     */
    @Test
    fun testFujiFujivelvia() {
        val velvia = HurterDriffield.fujiFujivelvia()
        assertNotNull(velvia)
        assertEquals("Fujifilm Velvia 50", velvia.name)
        // Should have high contrast
        assertTrue(velvia.gamma > 2.2f)
    }

    /**
     * Test linear curve (reference).
     */
    @Test
    fun testLinearCurve() {
        val linear = HurterDriffield.linear()
        assertEquals(0f, linear.blackPoint, 0.001f)
        assertEquals(1f, linear.whitePoint, 0.001f)
        // Linear should map input ~= output in midtones
        val midtone = linear.toneMap(0.5f)
        assertTrue(abs(midtone - 0.5f) < 0.1f)
    }

    /**
     * Test that different H&D curves produce different results.
     */
    @Test
    fun testDifferentCurvesDifferentResults() {
        val hd1 = HurterDriffield.kodakTriX400()
        val hd2 = HurterDriffield.kodakPortra400()

        val input = 0.7f
        val output1 = hd1.toneMap(input)
        val output2 = hd2.toneMap(input)

        assertNotEquals("Different curves should produce different results", output1, output2, 0.02f)
    }

    /**
     * Test toString produces readable output.
     */
    @Test
    fun testToString() {
        val str = hd.toString()
        assertTrue("Should contain gamma info", str.contains("gamma"))
        assertTrue("Should contain name", str.contains(hd.name))
    }
}
