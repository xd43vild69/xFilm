package com.example.xfilm.presentation.camera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.xfilm.di.CameraModule

/**
 * Manual ViewModel factory (Phase 1/2 DI without Hilt).
 * Wires Camera2Manager from [CameraModule] into the CameraViewModel.
 */
class CameraViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val capabilities = CameraModule.provideCameraCapabilities(context)
        val camera2Manager = CameraModule.provideCamera2Manager(context, capabilities)
        return CameraViewModel(context, camera2Manager) as T
    }
}
