package com.example.xfilm.rendering.media

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Samsung Galaxy S24+ RAW capture optimization.
 *
 * Specifications:
 * - Main sensor: Samsung GN5 (50MP, 1/1.56")
 * - Resolution: 8160 x 6120 pixels
 * - Sensor size: 8mm diagonal (43.3mm²)
 * - Pixel size: 1.0μm (native)
 * - Aperture: f/1.8 fixed
 * - ISO range: 50-3200 (auto), up to 6400 (manual)
 * - Supports RAW/DNG output
 * - Maximum storage per RAW: ~75-100MB (12-bit Bayer)
 */
object S24RawCapture {

    private const val TAG = "S24RawCapture"

    // Samsung Galaxy S24+ specifications
    const val S24_MAIN_WIDTH = 8160
    const val S24_MAIN_HEIGHT = 6120
    const val S24_MEGAPIXELS = 50
    const val S24_APERTURE = 1.8f
    const val S24_SENSOR_DIAGONAL_MM = 8.0f
    const val S24_PIXEL_SIZE_UM = 1.0f

    // RAW image storage (DNG format)
    const val DNG_RAW_SIZE_MB_MIN = 75
    const val DNG_RAW_SIZE_MB_MAX = 100
    const val JPEG_PROCESSED_SIZE_MB = 8

    data class S24Specifications(
        val modelName: String,
        val mainSensorMp: Int,
        val mainResolutionW: Int,
        val mainResolutionH: Int,
        val sensorApertureF: Float,
        val sensorDiagonalMm: Float,
        val pixelSizeMicrometers: Float,
        val supportsDng: Boolean,
        val supportsRaw: Boolean,
        val maxIso: Int,
        val estimatedRawSizeMb: Int,
    )

    /**
     * Detect if device is S24+ and get its specifications
     */
    fun detectS24Specifications(context: Context): S24Specifications? {
        val deviceModel = Build.MODEL
        val deviceBrand = Build.BRAND

        Log.i(TAG, "Device: $deviceBrand $deviceModel")

        // Check for S24+ variants
        val isS24Plus = deviceModel.contains("SM-S926B", ignoreCase = true) ||
                        deviceModel.contains("SM-S926U", ignoreCase = true) ||
                        deviceModel.contains("SM-S926W", ignoreCase = true) ||
                        deviceModel.contains("Galaxy S24+", ignoreCase = true) ||
                        deviceModel.contains("Galaxy S24 Plus", ignoreCase = true)

        if (!isS24Plus) {
            Log.w(TAG, "Device is not identified as S24+. Model: $deviceModel")
            return null
        }

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return null

        // Detect main rear camera (typically ID "0")
        val cameraId = "0"
        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get camera characteristics", e)
            return null
        }

        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?: intArrayOf()
        val supportsRaw = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

        Log.i(TAG, "S24+ Sensor Support:")
        Log.i(TAG, "  Supports RAW: $supportsRaw")
        if (sensorSize != null) {
            val diagonalMm = hypot(sensorSize.width, sensorSize.height).roundToInt()
            Log.i(TAG, "  Sensor size: ${sensorSize.width.roundToInt()}mm x ${sensorSize.height.roundToInt()}mm (${diagonalMm}mm diagonal)")
        }
        Log.i(TAG, "  Sensor orientation: $sensorOrientation°")

        return S24Specifications(
            modelName = "Samsung Galaxy S24+",
            mainSensorMp = S24_MEGAPIXELS,
            mainResolutionW = S24_MAIN_WIDTH,
            mainResolutionH = S24_MAIN_HEIGHT,
            sensorApertureF = S24_APERTURE,
            sensorDiagonalMm = S24_SENSOR_DIAGONAL_MM,
            pixelSizeMicrometers = S24_PIXEL_SIZE_UM,
            supportsDng = supportsRaw,
            supportsRaw = supportsRaw,
            maxIso = 6400,
            estimatedRawSizeMb = (DNG_RAW_SIZE_MB_MIN + DNG_RAW_SIZE_MB_MAX) / 2,
        )
    }

    /**
     * Calculate recommended ISO based on scene EV for S24+ optimal quality
     */
    fun getRecommendedIsoForEv(ev: Float): Int {
        return when {
            ev >= 13f -> 50        // Very bright (outdoor noon)
            ev >= 10f -> 100       // Bright
            ev >= 7f -> 200        // Normal daylight
            ev >= 5f -> 400        // Indoor with good light
            ev >= 3f -> 800        // Low light
            else -> 1600           // Very low light
        }
    }

    /**
     * Estimate storage needed for capture sequence
     */
    fun estimateStorageNeeded(
        numRawCaptures: Int,
        numJpegCaptures: Int
    ): Int {
        val rawSize = numRawCaptures * DNG_RAW_SIZE_MB_MAX
        val jpegSize = numJpegCaptures * JPEG_PROCESSED_SIZE_MB
        return rawSize + jpegSize
    }

    /**
     * Format specifications for logging/display
     */
    fun formatSpecifications(specs: S24Specifications): String {
        return """
            |=== Samsung Galaxy S24+ Specifications ===
            |Model: ${specs.modelName}
            |Main Sensor: ${specs.mainSensorMp}MP (${specs.mainResolutionW}x${specs.mainResolutionH})
            |Aperture: f/${specs.sensorApertureF}
            |Sensor Size: ${specs.sensorDiagonalMm}mm diagonal
            |Pixel Size: ${specs.pixelSizeMicrometers}μm
            |RAW Support: ${specs.supportsRaw}
            |DNG Support: ${specs.supportsDng}
            |Max ISO: ${specs.maxIso}
            |Est. RAW File Size: ${specs.estimatedRawSizeMb}MB per image
        """.trimMargin()
    }
}
