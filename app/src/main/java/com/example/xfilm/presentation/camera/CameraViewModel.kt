package com.example.xfilm.presentation.camera

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xfilm.calculation.analog.AnalogConfig
import com.example.xfilm.calculation.analog.PentaxK1000Translator
import com.example.xfilm.calculation.ev.EVCalculator
import com.example.xfilm.camera.Camera2Manager
import com.example.xfilm.rendering.gl.FrameCaptureListener
import com.example.xfilm.rendering.media.ImageMetadata
import com.example.xfilm.rendering.media.ImageSaver
import com.example.xfilm.rendering.media.S24RawCapture
import com.example.xfilm.rendering.media.DngSaver
import com.example.xfilm.utils.CaptureAudioFeedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraViewModel(
    private val context: Context,
    private val camera2Manager: Camera2Manager,
    private val evCalculator: EVCalculator = EVCalculator(),
    private val translator: PentaxK1000Translator = PentaxK1000Translator(),
) : ViewModel() {

    companion object {
        private const val TAG = "CameraViewModel"
    }

    private val _uiState = MutableStateFlow<CameraUIState>(CameraUIState.Initializing)
    val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()

    private var glSurfaceView: com.example.xfilm.rendering.gl.LutGlSurfaceView? = null
    private var lastMetadata: CameraUIState.Metering? = null
    private var cameraJob: kotlinx.coroutines.Job? = null

    fun setGlSurfaceView(surfaceView: com.example.xfilm.rendering.gl.LutGlSurfaceView) {
        glSurfaceView = surfaceView
        glSurfaceView?.setFrameCaptureListener(FrameCaptureListener { rgbaPixels, width, height ->
            handleFrameCapture(rgbaPixels, width, height)
        })
    }

    /**
     * Starts the camera preview pipeline against the given preview [surface].
     * MUST be called only after the CAMERA permission has been granted.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun startCamera(surface: Surface) {
        stopCamera()
        cameraJob = viewModelScope.launch {
            try {
                _uiState.value = CameraUIState.Loading
                camera2Manager.previewMetadata(surface).collect { metadata ->
                    val ev = evCalculator.calculate(
                        EVCalculator.Input(
                            aperture = metadata.aperture,
                            shutterTimeSeconds = metadata.exposureTimeSeconds,
                            sensorGain = metadata.iso,
                        )
                    )
                    val configs = translator.getPresetsForEV(ev.gainAdjustedEV)
                    val meteringState = CameraUIState.Metering(
                        ev = ev.gainAdjustedEV,
                        iso = metadata.iso,
                        exposureSeconds = metadata.exposureTimeSeconds,
                        aperture = metadata.aperture,
                        configs = configs,
                    )
                    lastMetadata = meteringState
                    _uiState.value = meteringState
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Camera permission denied", e)
                _uiState.value = CameraUIState.Error("Camera permission required")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "Camera error", e)
                _uiState.value = CameraUIState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun stopCamera() {
        cameraJob?.cancel()
        cameraJob = null
        camera2Manager.stopPreview()
    }

    fun captureFrame() {
        CaptureAudioFeedback.playShutterSound(context)
        glSurfaceView?.requestFrameCapture()
    }

    private fun handleFrameCapture(rgbaPixels: ByteArray, width: Int, height: Int) {
        viewModelScope.launch {
            try {
                val bitmap = ImageSaver.saveRGBAToBitmap(rgbaPixels, width, height)
                if (bitmap == null) {
                    Toast.makeText(context, "Error al procesar imagen", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val metadata = lastMetadata
                if (metadata == null) {
                    Toast.makeText(context, "Error: metadatos no disponibles", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Detect S24+ capabilities
                val s24Specs = S24RawCapture.detectS24Specifications(context)
                if (s24Specs != null) {
                    Log.i(TAG, S24RawCapture.formatSpecifications(s24Specs))
                }

                val captureFormat = if (s24Specs?.supportsRaw == true) "RAW/DNG" else "JPEG"
                val sensorRes = s24Specs?.let { "${it.mainSensorMp}MP (${it.mainResolutionW}x${it.mainResolutionH})" }
                    ?: "Unknown"

                val imageMetadata = ImageMetadata(
                    ev = metadata.ev,
                    iso = metadata.iso,
                    aperture = metadata.aperture,
                    exposureSeconds = metadata.exposureSeconds,
                    filmName = "Kodak Tri-X 400",
                    captureFormat = captureFormat,
                    sensorResolution = sensorRes,
                    deviceModel = s24Specs?.modelName ?: "Unknown Device"
                )

                val jpegFilename = ImageSaver.generateFilename("jpg")
                val dngFilename = ImageSaver.generateFilename("dng")

                // Save JPEG (processed via LUT)
                val jpegUri = ImageSaver.saveBitmapToGalleryWithMetadata(
                    context,
                    bitmap,
                    jpegFilename,
                    imageMetadata
                )

                // Save DNG (RAW - simulated from GL data for now)
                // TODO: In future, capture actual sensor RAW via Camera2 ImageReader
                var dngUri: Uri? = null
                if (s24Specs?.supportsRaw == true) {
                    // Convert bitmap RGBA to Bayer pattern (12-bit simulation)
                    val bayerRawData = convertRgbaToBayerRaw(rgbaPixels, width, height)
                    dngUri = DngSaver.saveBayerRawToDng(
                        context,
                        bayerRawData,
                        width,
                        height,
                        dngFilename,
                        imageMetadata
                    )
                }

                if (jpegUri != null) {
                    val message = if (dngUri != null) {
                        "Foto guardada: JPEG + RAW/DNG (${s24Specs?.mainSensorMp}MP)"
                    } else if (s24Specs?.supportsRaw == true) {
                        "Foto guardada: JPEG + RAW (${s24Specs?.mainSensorMp}MP)"
                    } else {
                        "Foto guardada (JPEG)"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "JPEG: $jpegUri\nDNG: $dngUri")
                } else {
                    Toast.makeText(context, "Error al guardar foto", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame capture error", e)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Convert RGBA buffer to Bayer pattern RAW data (12-bit simulation).
     * Uses RGGB (most common) pattern: R G R G... / G B G B...
     */
    private fun convertRgbaToBayerRaw(rgbaPixels: ByteArray, width: Int, height: Int): ByteArray {
        // Each pixel in Bayer is 12-bit (1.5 bytes), but stored as 16-bit for alignment
        val rawData = ByteArray(width * height * 2)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixelIndex = (y * width + x) * 4  // RGBA is 4 bytes per pixel
                val r = (rgbaPixels[pixelIndex].toInt() and 0xFF)
                val g = (rgbaPixels[pixelIndex + 1].toInt() and 0xFF)
                val b = (rgbaPixels[pixelIndex + 2].toInt() and 0xFF)

                // Simulate 12-bit Bayer pattern
                val bayerValue = when {
                    (y and 1) == 0 && (x and 1) == 0 -> r        // R
                    (y and 1) == 0 && (x and 1) == 1 -> g        // G
                    (y and 1) == 1 && (x and 1) == 0 -> g        // G
                    else -> b                                      // B
                }

                // Convert 8-bit to 16-bit (shift left by 8)
                val value16bit = (bayerValue shl 8) and 0xFFFF

                // Store as 16-bit little-endian
                val rawIndex = (y * width + x) * 2
                rawData[rawIndex] = (value16bit and 0xFF).toByte()
                rawData[rawIndex + 1] = ((value16bit shr 8) and 0xFF).toByte()
            }
        }

        return rawData
    }

    override fun onCleared() {
        super.onCleared()
        camera2Manager.release()
    }
}

sealed class CameraUIState {
    data object Initializing : CameraUIState()
    data object Loading : CameraUIState()
    data class Metering(
        val ev: Float,
        val iso: Int,
        val exposureSeconds: Float,
        val aperture: Float,
        val configs: List<AnalogConfig>,
    ) : CameraUIState()
    data class Error(val message: String) : CameraUIState()
}
