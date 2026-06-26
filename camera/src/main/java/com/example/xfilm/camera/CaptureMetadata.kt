package com.example.xfilm.camera

/**
 * Per-frame exposure metadata extracted from Camera2 CaptureResult.
 *
 * These are the real sensor values the device auto-exposure converged on,
 * and form the basis for absolute EV computation in the calculation layer.
 */
data class CaptureMetadata(
    /** Sensor exposure (shutter) time, in nanoseconds. */
    val exposureTimeNs: Long,
    /** Sensor sensitivity (ISO / analog gain). */
    val iso: Int,
    /** Lens aperture (f-number). Fixed on most phone CMOS sensors. */
    val aperture: Float,
    /** Frame timestamp (nanoseconds, sensor clock). */
    val timestamp: Long,
) {
    /** Exposure time expressed in seconds (for EV math). */
    val exposureTimeSeconds: Float
        get() = exposureTimeNs / 1_000_000_000f
}
