package com.example.xfilm.presentation.camera

import android.Manifest
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xfilm.calculation.analog.AnalogConfig
import com.example.xfilm.calculation.analog.PentaxK1000Translator
import com.example.xfilm.calculation.ev.EVCalculator
import com.example.xfilm.camera.Camera2Manager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraViewModel(
    private val camera2Manager: Camera2Manager,
    private val evCalculator: EVCalculator = EVCalculator(),
    private val translator: PentaxK1000Translator = PentaxK1000Translator(),
) : ViewModel() {

    companion object {
        private const val TAG = "CameraViewModel"
    }

    private val _uiState = MutableStateFlow<CameraUIState>(CameraUIState.Initializing)
    val uiState: StateFlow<CameraUIState> = _uiState.asStateFlow()

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
                    _uiState.value = CameraUIState.Metering(
                        ev = ev.gainAdjustedEV,
                        iso = metadata.iso,
                        exposureSeconds = metadata.exposureTimeSeconds,
                        aperture = metadata.aperture,
                        configs = configs,
                    )
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
