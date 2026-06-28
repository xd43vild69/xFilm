package com.example.xfilm.rendering.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import com.example.xfilm.rendering.lut.LUT3DGenerator

/**
 * A [GLSurfaceView] preconfigured for OpenGL ES 3.0 that renders the camera
 * preview through a 3D film LUT.
 *
 * Render mode is on-demand: the renderer requests a draw whenever a new camera
 * frame arrives, avoiding wasteful continuous redraws.
 *
 * @param lut the baked H&D LUT to apply
 * @param onSurfaceTextureReady invoked (on the GL thread) once the camera-facing
 *        SurfaceTexture exists; the host should wrap it in a Surface for Camera2
 */
class LutGlSurfaceView(
    context: Context,
    lut: LUT3DGenerator.Lut3D,
    onSurfaceTextureReady: (SurfaceTexture) -> Unit,
) : GLSurfaceView(context) {

    private lateinit var renderer: LutPreviewRenderer

    init {
        setEGLContextClientVersion(3)
        renderer = LutPreviewRenderer(
            lut = lut,
            requestRender = { requestRender() },
            onSurfaceTextureReady = onSurfaceTextureReady,
        )
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setFrameCaptureListener(listener: FrameCaptureListener?) {
        renderer.setFrameCaptureListener(listener)
    }

    fun requestFrameCapture() {
        renderer.requestFrameCapture()
    }

    fun setPinholeEffects(
        vignetteEnabled: Boolean,
        chromaticEnabled: Boolean,
        softnessEnabled: Boolean
    ) {
        renderer.setPinholeEffects(vignetteEnabled, chromaticEnabled, softnessEnabled)
    }
}
