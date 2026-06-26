package com.example.xfilm.rendering.lut

import com.example.xfilm.calculation.colorscience.HurterDriffield
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LUT3DGeneratorTest {

    private lateinit var generator: LUT3DGenerator

    @Before
    fun setup() {
        generator = LUT3DGenerator()
    }

    @Test
    fun testDimensionsAndSize() {
        val lut = generator.generate(HurterDriffield(), dimension = 32)
        assertEquals(32, lut.dimension)
        // 32³ entries × 3 channels
        assertEquals(32 * 32 * 32 * 3, lut.rgb.size)
    }

    @Test
    fun testDefaultDimension() {
        val lut = generator.generate(HurterDriffield())
        assertEquals(LUT3DGenerator.DEFAULT_DIMENSION, lut.dimension)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidDimensionThrows() {
        generator.generate(HurterDriffield(), dimension = 1)
    }

    @Test
    fun testFirstEntryIsBlackPoint() {
        val curve = HurterDriffield()
        val lut = generator.generate(curve, dimension = 8)
        // Origin (0,0,0) maps each channel through toneMap(0) = blackPoint
        assertEquals(curve.toneMap(0f), lut.rgb[0], 0.001f)
        assertEquals(curve.toneMap(0f), lut.rgb[1], 0.001f)
        assertEquals(curve.toneMap(0f), lut.rgb[2], 0.001f)
    }

    @Test
    fun testLastEntryIsWhitePoint() {
        val curve = HurterDriffield()
        val dim = 8
        val lut = generator.generate(curve, dimension = dim)
        // Final corner (1,1,1) maps each channel through toneMap(1) = whitePoint
        val lastIndex = lut.rgb.size - 3
        assertEquals(curve.toneMap(1f), lut.rgb[lastIndex], 0.001f)
        assertEquals(curve.toneMap(1f), lut.rgb[lastIndex + 1], 0.001f)
        assertEquals(curve.toneMap(1f), lut.rgb[lastIndex + 2], 0.001f)
    }

    @Test
    fun testAllValuesInRange() {
        val lut = generator.generate(HurterDriffield.kodakTriX400(), dimension = 16)
        for (v in lut.rgb) {
            assertTrue("LUT value $v out of [0,1]", v in 0f..1f)
        }
    }

    @Test
    fun testRedAxisVariesFastest() {
        // Two adjacent entries along red (index 0 vs 1) should differ in R channel
        val curve = HurterDriffield()
        val lut = generator.generate(curve, dimension = 8)
        val r0 = lut.rgb[0]
        val r1 = lut.rgb[3] // next entry along red
        assertNotEquals(r0, r1, 0.0001f)
        // ...but their green channel should be identical (same g row)
        assertEquals(lut.rgb[1], lut.rgb[4], 0.0001f)
    }

    @Test
    fun testDifferentCurvesProduceDifferentLuts() {
        val triX = generator.generate(HurterDriffield.kodakTriX400(), dimension = 16)
        val portra = generator.generate(HurterDriffield.kodakPortra400(), dimension = 16)
        assertNotEquals(triX, portra)
    }

    @Test
    fun testSizeBytes() {
        val lut = generator.generate(HurterDriffield(), dimension = 32)
        assertEquals(lut.rgb.size * 4, lut.sizeBytes)
    }
}
