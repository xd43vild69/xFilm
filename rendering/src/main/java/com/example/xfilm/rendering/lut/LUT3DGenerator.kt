package com.example.xfilm.rendering.lut

import com.example.xfilm.calculation.colorscience.HurterDriffield

/**
 * Generates a 3D LUT (look-up table) from a Hurter-Driffield film curve.
 *
 * The LUT maps an input RGB color cube [0,1]³ to an output RGB cube where each
 * channel has been passed through the film's tone curve. It is uploaded to the
 * GPU as a 3D texture and sampled (with hardware trilinear interpolation) in the
 * fragment shader to emulate the film's densitometric response in real time.
 *
 * Layout: tightly packed RGB, index = (b * dim * dim + g * dim + r) * 3.
 * The blue axis is the outermost (slowest-varying) to match GL_TEXTURE_3D upload
 * order (depth = blue, height = green, width = red).
 */
class LUT3DGenerator {

    data class Lut3D(
        val dimension: Int,
        /** Tightly packed RGB floats in [0,1], size = dimension³ * 3. */
        val rgb: FloatArray,
    ) {
        val sizeBytes: Int get() = rgb.size * Float.SIZE_BYTES

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Lut3D
            return dimension == other.dimension && rgb.contentEquals(other.rgb)
        }

        override fun hashCode(): Int = 31 * dimension + rgb.contentHashCode()
    }

    /**
     * Builds a [Lut3D] of the given cube [dimension] (e.g. 32 → 32³ entries).
     *
     * @param curve the H&D film curve to bake into the LUT
     * @param dimension per-axis resolution; 32 is a good quality/size tradeoff
     */
    fun generate(curve: HurterDriffield, dimension: Int = DEFAULT_DIMENSION): Lut3D {
        require(dimension >= 2) { "LUT dimension must be >= 2, was $dimension" }

        val rgb = FloatArray(dimension * dimension * dimension * 3)
        val step = 1f / (dimension - 1)

        var i = 0
        for (b in 0 until dimension) {
            val mappedB = curve.toneMap(b * step)
            for (g in 0 until dimension) {
                val mappedG = curve.toneMap(g * step)
                for (r in 0 until dimension) {
                    rgb[i++] = curve.toneMap(r * step)
                    rgb[i++] = mappedG
                    rgb[i++] = mappedB
                }
            }
        }

        return Lut3D(dimension = dimension, rgb = rgb)
    }

    companion object {
        const val DEFAULT_DIMENSION = 32
    }
}
