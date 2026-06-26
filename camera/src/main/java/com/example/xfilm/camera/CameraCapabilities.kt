package com.example.xfilm.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

/**
 * Detects and reports camera hardware capabilities.
 */
class CameraCapabilities(private val context: Context) {

    data class DeviceCapabilities(
        val cameraId: String,
        val hardwareLevel: Int,
        val hardwareLevelName: String,
        val hasAutoFocus: Boolean,
        val supportsRawSensor: Boolean,
        val supportedFormats: List<Int>,
    )

    companion object {
        private const val TAG = "CameraCapabilities"

        // Hardware levels
        const val HARDWARE_LEVEL_LIMITED = 0
        const val HARDWARE_LEVEL_FULL = 1
        const val HARDWARE_LEVEL_3 = 2
    }

    /**
     * Detects the first available camera and returns its capabilities.
     * Prefers rear-facing camera.
     */
    fun detectCameraCapabilities(): DeviceCapabilities? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return null

        // Find rear-facing camera
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: continue

            // Prefer rear-facing
            if (facing != CameraCharacteristics.LENS_FACING_BACK) continue

            return getCapabilitiesForCamera(cameraManager, cameraId, characteristics)
        }

        // Fallback: any camera
        if (cameraManager.cameraIdList.isNotEmpty()) {
            val cameraId = cameraManager.cameraIdList[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            return getCapabilitiesForCamera(cameraManager, cameraId, characteristics)
        }

        return null
    }

    private fun getCapabilitiesForCamera(
        cameraManager: CameraManager,
        cameraId: String,
        characteristics: CameraCharacteristics,
    ): DeviceCapabilities {
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL) ?: 0
        val hardwareLevelName = when (hardwareLevel) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            else -> "LEGACY"
        }

        val autoFocusModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
        val hasAutoFocus = autoFocusModes.contains(CameraCharacteristics.CONTROL_AF_MODE_AUTO)

        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: intArrayOf()
        val supportsRaw = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)

        val formats = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.outputFormats?.toList() ?: emptyList()

        Log.i(TAG, "Camera $cameraId: Hardware=$hardwareLevelName, AutoFocus=$hasAutoFocus, Raw=$supportsRaw")
        Log.i(TAG, "Supported formats: $formats")

        return DeviceCapabilities(
            cameraId = cameraId,
            hardwareLevel = hardwareLevel,
            hardwareLevelName = hardwareLevelName,
            hasAutoFocus = hasAutoFocus,
            supportsRawSensor = supportsRaw,
            supportedFormats = formats,
        )
    }

    /**
     * Checks if device supports HARDWARE_LEVEL_FULL for absolute control.
     */
    fun supportsFullLevel(): Boolean {
        val cap = detectCameraCapabilities() ?: return false
        return cap.hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
    }

    /**
     * Gets sensor info like sensitivity range, exposure time range, etc.
     */
    fun getSensorCharacteristics(cameraId: String): SensorCharacteristics? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return null

        val characteristics = try {
            cameraManager.getCameraCharacteristics(cameraId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get camera characteristics", e)
            return null
        }

        return SensorCharacteristics.fromCameraCharacteristics(characteristics)
    }
}
