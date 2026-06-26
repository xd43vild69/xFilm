package com.example.xfilm.di

import android.content.Context
import com.example.xfilm.camera.Camera2Manager
import com.example.xfilm.camera.CameraCapabilities

/**
 * Camera module factory.
 * Phase 1: Simplified without Hilt DI (will add Hilt back in Phase 2).
 */
object CameraModule {

    fun provideCameraCapabilities(context: Context): CameraCapabilities =
        CameraCapabilities(context)

    fun provideCamera2Manager(
        context: Context,
        cameraCapabilities: CameraCapabilities,
    ): Camera2Manager = Camera2Manager(context, cameraCapabilities)
}
