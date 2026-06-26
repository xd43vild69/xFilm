package com.example.xfilm.camera

import android.media.Image
import java.nio.ByteBuffer

/**
 * Wrapper around a camera frame, supporting YUV420 and Raw Bayer formats.
 */
data class FrameBuffer(
    val data: ByteArray,
    val width: Int,
    val height: Int,
    val format: ImageFormat,
    val timestamp: Long = System.currentTimeMillis(),
    val planes: List<Plane>? = null,
) {
    enum class ImageFormat {
        YUV_420_888,
        RAW_BAYER,
    }

    data class Plane(
        val buffer: ByteBuffer,
        val pixelStride: Int,
        val rowStride: Int,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FrameBuffer
        return width == other.width &&
            height == other.height &&
            format == other.format &&
            timestamp == other.timestamp
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + format.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }

    companion object {
        /**
         * Creates a FrameBuffer from an Android Image object (typically from Camera2).
         */
        fun fromAndroidImage(image: Image): FrameBuffer {
            val format = when (image.format) {
                android.graphics.ImageFormat.YUV_420_888 -> ImageFormat.YUV_420_888
                android.graphics.ImageFormat.RAW_SENSOR -> ImageFormat.RAW_BAYER
                else -> throw IllegalArgumentException("Unsupported image format: ${image.format}")
            }

            val planes = image.planes.map { plane ->
                Plane(
                    buffer = plane.buffer.also { it.rewind() },
                    pixelStride = plane.pixelStride,
                    rowStride = plane.rowStride,
                )
            }

            // Copy all plane data into single buffer
            val totalSize = planes.sumOf { it.buffer.remaining() }
            val data = ByteArray(totalSize)
            var offset = 0
            for (plane in planes) {
                val buffer = plane.buffer
                val size = buffer.remaining()
                buffer.get(data, offset, size)
                offset += size
            }

            return FrameBuffer(
                data = data,
                width = image.width,
                height = image.height,
                format = format,
                timestamp = image.timestamp,
                planes = planes,
            )
        }
    }
}
