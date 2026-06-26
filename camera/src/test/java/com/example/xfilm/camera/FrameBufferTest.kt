package com.example.xfilm.camera

import org.junit.Assert.*
import org.junit.Test

class FrameBufferTest {

    @Test
    fun testFrameBufferCreation() {
        val data = ByteArray(1920 * 1080 * 2)
        val frame = FrameBuffer(
            data = data,
            width = 1920,
            height = 1080,
            format = FrameBuffer.ImageFormat.YUV_420_888,
        )

        assertEquals(1920, frame.width)
        assertEquals(1080, frame.height)
        assertEquals(FrameBuffer.ImageFormat.YUV_420_888, frame.format)
        assertEquals(data.size, frame.data.size)
    }

    @Test
    fun testFrameBufferEquality() {
        val data = ByteArray(100)
        val frame1 = FrameBuffer(
            data = data,
            width = 640,
            height = 480,
            format = FrameBuffer.ImageFormat.YUV_420_888,
            timestamp = 1000,
        )

        val frame2 = FrameBuffer(
            data = ByteArray(100),
            width = 640,
            height = 480,
            format = FrameBuffer.ImageFormat.YUV_420_888,
            timestamp = 1000,
        )

        assertEquals(frame1, frame2)
    }

    @Test
    fun testFrameBufferDifferentFormats() {
        val data = ByteArray(100)

        val yuvFrame = FrameBuffer(
            data = data,
            width = 640,
            height = 480,
            format = FrameBuffer.ImageFormat.YUV_420_888,
        )

        val rawFrame = FrameBuffer(
            data = data,
            width = 640,
            height = 480,
            format = FrameBuffer.ImageFormat.RAW_BAYER,
        )

        assertNotEquals(yuvFrame, rawFrame)
    }
}
