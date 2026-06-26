package com.example.xfilm.camera

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Manages Camera2 lifecycle and emits real exposure metadata from CaptureResult.
 *
 * The caller provides a preview [Surface] (e.g. from a TextureView) and collects
 * a [Flow] of [CaptureMetadata] carrying the sensor's converged exposure values.
 */
class Camera2Manager(
    private val context: Context,
    private val cameraCapabilities: CameraCapabilities,
) {

    companion object {
        private const val TAG = "Camera2Manager"
    }

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    private val backgroundThread = HandlerThread("Camera2Background").apply { start() }
    private val backgroundHandler = Handler(backgroundThread.looper)

    /**
     * Opens the camera, drives a repeating preview request against [previewSurface],
     * and emits exposure metadata for each completed frame.
     *
     * Caller MUST hold the CAMERA permission before collecting this flow.
     */
    @RequiresPermission(Manifest.permission.CAMERA)
    fun previewMetadata(previewSurface: Surface): Flow<CaptureMetadata> = callbackFlow {
        val capabilities = cameraCapabilities.detectCameraCapabilities()
            ?: throw IllegalStateException("No camera available")

        Log.i(TAG, "Opening camera ${capabilities.cameraId} (${capabilities.hardwareLevelName})")
        val sensorChar = cameraCapabilities.getSensorCharacteristics(capabilities.cameraId)
        Log.i(TAG, "Sensor characteristics:\n$sensorChar")
        val fixedAperture = sensorChar?.maxAperture ?: 2.0f

        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val captureCallback = object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult,
            ) {
                val exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
                val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
                val aperture = result.get(CaptureResult.LENS_APERTURE) ?: fixedAperture
                val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: 0L

                trySendBlocking(
                    CaptureMetadata(
                        exposureTimeNs = exposure,
                        iso = iso,
                        aperture = aperture,
                        timestamp = timestamp,
                    )
                )
            }
        }

        val deviceCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.i(TAG, "Camera opened: ${capabilities.cameraId}")
                cameraDevice = camera
                createSession(camera, previewSurface, captureCallback)
            }

            override fun onDisconnected(camera: CameraDevice) {
                Log.w(TAG, "Camera disconnected")
                camera.close()
                cameraDevice = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e(TAG, "Camera error: $error")
                camera.close()
                cameraDevice = null
                close(IllegalStateException("Camera error: $error"))
            }
        }

        cameraManager.openCamera(capabilities.cameraId, deviceCallback, backgroundHandler)

        awaitClose {
            Log.i(TAG, "Closing camera preview flow")
            stopPreview()
        }
    }

    @Suppress("DEPRECATION")
    private fun createSession(
        camera: CameraDevice,
        previewSurface: Surface,
        captureCallback: CameraCaptureSession.CaptureCallback,
    ) {
        try {
            camera.createCaptureSession(
                listOf(previewSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.i(TAG, "Capture session configured")
                        captureSession = session

                        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        builder.addTarget(previewSurface)
                        builder.set(
                            CaptureRequest.CONTROL_MODE,
                            CameraMetadata.CONTROL_MODE_AUTO,
                        )
                        session.setRepeatingRequest(builder.build(), captureCallback, backgroundHandler)
                        Log.i(TAG, "Repeating preview request started")
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                    }
                },
                backgroundHandler,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }

    fun stopPreview() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            Log.i(TAG, "Camera stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview", e)
        }
    }

    fun release() {
        stopPreview()
        backgroundThread.quitSafely()
    }
}
