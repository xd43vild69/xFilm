package com.example.xfilm.presentation.camera

import android.Manifest
import android.content.Context
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
        viewModelScope.launch {
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
                Log.e(TAG, "Camera error", e)
                _uiState.value = CameraUIState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun stopCamera() {
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

                val imageMetadata = ImageMetadata(
                    ev = metadata.ev,
                    iso = metadata.iso,
                    aperture = metadata.aperture,
                    exposureSeconds = metadata.exposureSeconds,
                    filmName = "Kodak Tri-X 400"
                )

                val filename = ImageSaver.generateFilename()
                val uri = ImageSaver.saveBitmapToGalleryWithMetadata(
                    context,
                    bitmap,
                    filename,
                    imageMetadata
                )

                if (uri != null) {
                    Toast.makeText(context, "Foto guardada", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Error al guardar foto", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame capture error", e)
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
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
