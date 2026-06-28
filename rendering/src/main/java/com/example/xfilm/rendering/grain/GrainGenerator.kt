package com.example.xfilm.rendering.grain

import java.util.Random
import kotlin.math.floor
import kotlin.math.sqrt

data class GrainParameters(
    val intensity: Float = 0.55f,
    val size: Float = 1.0f,
    val luminanceDependence: Float = 0.85f
)

object GrainGenerator {

    fun generateNoiseTexture(
        width: Int = 512,
        height: Int = 512,
        seed: Long = 42L
    ): ByteArray {
        val random = Random(seed)
        val pixels = ByteArray(width * height * 3)
        val perlinNoise = PerlinNoise(random)

        // FBM (Fractional Brownian Motion) con 5 octavas
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = fbm(
                    x.toFloat() / width,
                    y.toFloat() / height,
                    perlinNoise,
                    octaves = 5
                )

                // Convertir a rango [0, 255]
                val pixelValue = ((value + 1.0f) / 2.0f * 255f).toInt().coerceIn(0, 255).toByte()

                val pixelIndex = (y * width + x) * 3
                pixels[pixelIndex] = pixelValue
                pixels[pixelIndex + 1] = pixelValue
                pixels[pixelIndex + 2] = pixelValue
            }
        }

        return pixels
    }

    private fun fbm(
        x: Float,
        y: Float,
        noise: PerlinNoise,
        octaves: Int,
        persistence: Float = 0.5f,
        lacunarity: Float = 2.0f
    ): Float {
        var value = 0.0f
        var amplitude = 1.0f
        var frequency = 1.0f
        var maxValue = 0.0f

        for (i in 0 until octaves) {
            value += amplitude * noise.perlin(x * frequency, y * frequency)
            maxValue += amplitude

            amplitude *= persistence
            frequency *= lacunarity
        }

        return value / maxValue
    }

    /**
     * Simple Perlin noise implementation optimized for mobile
     */
    private class PerlinNoise(random: Random) {
        private val permutation = IntArray(256)
        private val p = IntArray(512)

        init {
            for (i in 0 until 256) {
                permutation[i] = i
            }

            // Fisher-Yates shuffle
            for (i in 255 downTo 1) {
                val j = random.nextInt(i + 1)
                val temp = permutation[i]
                permutation[i] = permutation[j]
                permutation[j] = temp
            }

            // Duplicate for easier indexing
            for (i in 0 until 256) {
                p[i] = permutation[i]
                p[i + 256] = permutation[i]
            }
        }

        fun perlin(x: Float, y: Float): Float {
            val xi = floor(x).toInt() and 255
            val yi = floor(y).toInt() and 255

            val xf = x - floor(x)
            val yf = y - floor(y)

            val u = fade(xf)
            val v = fade(yf)

            val n00 = grad(p[p[xi] + yi], xf, yf)
            val n10 = grad(p[p[xi + 1] + yi], xf - 1f, yf)
            val n01 = grad(p[p[xi] + yi + 1], xf, yf - 1f)
            val n11 = grad(p[p[xi + 1] + yi + 1], xf - 1f, yf - 1f)

            val nx0 = lerp(n00, n10, u)
            val nx1 = lerp(n01, n11, u)
            return lerp(nx0, nx1, v)
        }

        private fun fade(t: Float): Float {
            return t * t * t * (t * (t * 6f - 15f) + 10f)
        }

        private fun lerp(a: Float, b: Float, t: Float): Float {
            return a + t * (b - a)
        }

        private fun grad(hash: Int, x: Float, y: Float): Float {
            val h = hash and 15
            val u = if (h < 8) x else y
            val v = if (h < 8) y else x
            return ((if (h and 1 == 0) u else -u) + (if (h and 2 == 0) v else -v))
        }
    }
}
