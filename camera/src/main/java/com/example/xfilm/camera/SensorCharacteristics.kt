package com.example.xfilm.camera

import android.hardware.camera2.CameraCharacteristics
import android.util.Range
import kotlin.math.sqrt

/**
 * Represents sensor characteristics extracted from Camera2 metadata.
 * Used for EV calculation and exposure control.
 */
data class SensorCharacteristics(
    val focalLength: Float,
    val maxAperture: Float,
    val sensorSize: SensorSize,
    val isoRange: Range<Int>,
    val exposureTimeRange: Range<Long>,
    val maxAnalogSensitivity: Int,
    val maxDigitalGain: Float,
) {

    data class SensorSize(
        val widthMm: Float,
        val heightMm: Float,
    ) {
        val diagonalMm: Float get() = sqrt(widthMm * widthMm + heightMm * heightMm)
    }

    companion object {
        /**
         * Extracts sensor characteristics from CameraCharacteristics.
         */
        fun fromCameraCharacteristics(
            characteristics: CameraCharacteristics,
        ): SensorCharacteristics? {
            val focalLength = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.firstOrNull() ?: return null

            val maxAperture = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                ?.minOrNull() ?: 2.0f

            val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                ?: return null
            val sensorSize = SensorSize(physicalSize.width, physicalSize.height)

            val isoRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
                ?: Range(100, 1600)

            val exposureTimeRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
                ?: Range(1000L, 30_000_000L)

            val maxAnalogSensitivity = characteristics.get(
                CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY
            ) ?: isoRange.upper

            val maxDigitalGain = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                ?: 1.0f

            return SensorCharacteristics(
                focalLength = focalLength,
                maxAperture = maxAperture,
                sensorSize = sensorSize,
                isoRange = isoRange,
                exposureTimeRange = exposureTimeRange,
                maxAnalogSensitivity = maxAnalogSensitivity,
                maxDigitalGain = maxDigitalGain,
            )
        }
    }

    fun getIsoRangeDisplayString(): String =
        "ISO ${isoRange.lower} - ${isoRange.upper}"

    fun getExposureTimeRangeDisplayString(): String {
        val minMs = exposureTimeRange.lower / 1_000_000f
        val maxMs = exposureTimeRange.upper / 1_000_000f
        return "${String.format("%.4f", minMs)} - ${String.format("%.2f", maxMs)} ms"
    }

    override fun toString(): String = """
        SensorCharacteristics(
            focalLength=$focalLength mm,
            maxAperture=f/$maxAperture,
            sensor=${sensorSize.widthMm}x${sensorSize.heightMm} mm (${String.format("%.1f", sensorSize.diagonalMm)} mm diagonal),
            ISO ${getIsoRangeDisplayString()},
            exposure time ${getExposureTimeRangeDisplayString()},
            maxAnalogSensitivity=$maxAnalogSensitivity,
            maxDigitalGain=$maxDigitalGain
        )
    """.trimIndent()
}
