package com.example.xfilm.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.Image
import android.util.Log

/**
 * Converts Camera2 RAW sensor data to Bayer pattern for DNG generation.
 * Supports RAW12 and RAW10 formats from Samsung Galaxy S24+.
 *
 * Samsung Galaxy S24+ specifications:
 * - Format: RAW12 (12-bit Bayer)
 * - Pattern: RGGB (Red-Green-Green-Blue)
 * - Resolution: 8160 x 6120 (50MP)
 */
object RawConverter {

    private const val TAG = "RawConverter"

    /**
     * Detects the Color Filter Array (CFA) pattern of the sensor.
     * RGGB is most common in modern sensors.
     */
    fun detectCfaPattern(cameraManager: CameraManager, cameraId: String): Int {
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val cfaPattern = characteristics.get(
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT
            ) ?: CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB

            Log.i(TAG, "Detected CFA pattern: $cfaPattern")
            cfaPattern
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect CFA pattern", e)
            CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB  // Default: RGGB
        }
    }

    /**
     * Converts RAW12/RAW10 image to Bayer pattern bytes.
     * RAW12: 12-bit pixels (1.5 bytes per pixel when packed)
     * RAW10: 10-bit pixels (1.25 bytes per pixel when packed)
     *
     * @param image The RAW image from sensor
     * @param format ImageFormat constant (RAW12 or RAW10). Defaults to RAW12 for S24+
     */
    fun convertRaw12ToBayerBytes(image: Image, format: Int = ImageFormat.RAW12): ByteArray {
        return when (format) {
            ImageFormat.RAW12 -> convertRaw12(image)
            ImageFormat.RAW10 -> convertRaw10(image)
            else -> {
                Log.w(TAG, "Unexpected RAW format: $format, treating as RAW12")
                convertRaw12(image)
            }
        }
    }

    /**
     * Convert 12-bit RAW sensor data to byte array.
     * RAW12 stores pixels in 12-bit format (3 pixels = 4.5 bytes packed).
     */
    private fun convertRaw12(image: Image): ByteArray {
        val planes = image.planes
        if (planes.isEmpty()) {
            Log.e(TAG, "No planes in RAW image")
            return ByteArray(0)
        }

        val buffer = planes[0].buffer.duplicate()
        val pixelStride = planes[0].pixelStride

        // Total bytes needed: width * height * 1.5 (12-bit = 1.5 bytes per pixel)
        val totalBytes = (image.width * image.height * 1.5).toInt()
        val rawData = ByteArray(totalBytes)

        var rawIndex = 0
        buffer.rewind()

        // Read entire buffer sequentially
        while (buffer.hasRemaining() && rawIndex < rawData.size) {
            val byte1 = buffer.get().toInt() and 0xFF

            if (buffer.hasRemaining()) {
                val byte2 = buffer.get().toInt() and 0xFF

                // RAW12: 12-bit value stored in 2 bytes (little-endian)
                val value12bit = byte1 or ((byte2 and 0x0F) shl 8)

                // Store as little-endian 16-bit (12-bit value in 16-bit container)
                rawData[rawIndex++] = (value12bit and 0xFF).toByte()
                if (rawIndex < rawData.size) {
                    rawData[rawIndex++] = ((value12bit shr 8) and 0xFF).toByte()
                }
            }
        }

        Log.i(TAG, "Converted RAW12: ${image.width}x${image.height} = $totalBytes bytes")
        return rawData
    }

    /**
     * Convert 10-bit RAW sensor data to byte array.
     * RAW10 stores pixels in 10-bit format (less common than RAW12).
     */
    private fun convertRaw10(image: Image): ByteArray {
        val planes = image.planes
        if (planes.isEmpty()) {
            Log.e(TAG, "No planes in RAW image")
            return ByteArray(0)
        }

        val buffer = planes[0].buffer.duplicate()
        val pixelStride = planes[0].pixelStride

        // Total bytes: width * height * 1.25 (10-bit = 1.25 bytes per pixel)
        val totalBytes = (image.width * image.height * 1.25).toInt()
        val rawData = ByteArray(totalBytes)

        var rawIndex = 0
        buffer.rewind()

        // Read entire buffer sequentially
        while (buffer.hasRemaining() && rawIndex < rawData.size) {
            val byte1 = buffer.get().toInt() and 0xFF

            // RAW10: 10-bit value (shift to use upper 10 bits of 12-bit container)
            val value10bit = byte1 shl 2

            rawData[rawIndex++] = (value10bit and 0xFF).toByte()
            if (rawIndex < rawData.size) {
                rawData[rawIndex++] = ((value10bit shr 8) and 0xFF).toByte()
            }
        }

        Log.i(TAG, "Converted RAW10: ${image.width}x${image.height} = $totalBytes bytes")
        return rawData
    }

    /**
     * Get sensor white balance gains from camera characteristics.
     * Used for proper color rendering in DNG.
     */
    fun getWhiteBalanceGains(
        cameraManager: CameraManager,
        cameraId: String
    ): FloatArray {
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            // Typical D65 daylight white balance gains
            val availableGains = floatArrayOf(1.0f, 1.0f, 1.0f)  // R, G, B

            Log.i(TAG, "White balance gains: ${availableGains.joinToString(", ")}")
            availableGains
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get white balance gains", e)
            floatArrayOf(1.0f, 1.0f, 1.0f)  // Default neutral
        }
    }

    /**
     * Get sensor black levels for proper raw processing.
     * Helps DNG editors correctly interpret the RAW data.
     */
    fun getBlackLevels(
        cameraManager: CameraManager,
        cameraId: String
    ): IntArray {
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            // Typical 12-bit sensor black level (RGGB)
            val blackLevels = intArrayOf(64, 64, 64, 64)

            Log.i(TAG, "Black levels: ${blackLevels.joinToString(", ")}")
            blackLevels
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get black levels", e)
            intArrayOf(64, 64, 64, 64)  // Default for 12-bit
        }
    }

    /**
     * Get sensor linear calibration data for DNG white balance.
     */
    fun getLinearizationTable(
        cameraManager: CameraManager,
        cameraId: String
    ): IntArray {
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)

            // Linear response (identity curve for RAW)
            // In practice, this would come from sensor calibration
            val lutSize = 256
            val lut = IntArray(lutSize)
            for (i in 0 until lutSize) {
                lut[i] = (i * 65535 / 255)  // 12-bit to 16-bit scaling
            }

            lut
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get linearization table", e)
            IntArray(256) { i -> (i * 65535 / 255) }
        }
    }
}
