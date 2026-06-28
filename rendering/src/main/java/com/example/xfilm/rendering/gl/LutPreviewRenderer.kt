package com.example.xfilm.rendering.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import com.example.xfilm.rendering.grain.GrainGenerator
import com.example.xfilm.rendering.lut.LUT3DGenerator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sin

fun interface FrameCaptureListener {
    fun onFrameCaptured(rgbaPixels: ByteArray, width: Int, height: Int)
}

/**
 * Renders the camera preview through a 3D LUT using OpenGL ES 3.0.
 *
 * The camera renders into an external OES texture (via a [SurfaceTexture] this
 * renderer creates); each frame the fragment shader remaps colors through the
 * baked H&D film LUT. The host wires the [SurfaceTexture] to Camera2 once it is
 * available through [onSurfaceTextureReady].
 */
class LutPreviewRenderer(
    private val lut: LUT3DGenerator.Lut3D,
    private val requestRender: () -> Unit,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "LutPreviewRenderer"

        // Full-screen quad: position (x,y) + texcoord (u,v)
        private val QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )
    }

    private var program = 0
    private var cameraTexId = 0
    private var lutTexId = 0
    private var grainTexId = 0
    private var surfaceTexture: SurfaceTexture? = null

    private var aPositionLoc = 0
    private var aTexCoordLoc = 0
    private var uTexMatrixLoc = 0
    private var uCameraTexLoc = 0
    private var uLutLoc = 0
    private var uLutSizeLoc = 0
    private var uGrainTexLoc = 0
    private var uGrainIntensityLoc = 0
    private var uGrainSizeLoc = 0
    private var uGrainLuminanceLoc = 0
    private var uGrainTimeSeedLoc = 0

    private val texMatrix = FloatArray(16)
    private lateinit var quadBuffer: FloatBuffer

    private var frameCaptureListener: FrameCaptureListener? = null
    private var shouldCaptureFrame = false
    private var frameWidth = 0
    private var frameHeight = 0

    private var frameCount = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        quadBuffer = ByteBuffer.allocateDirect(QUAD.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(QUAD); position(0) }

        program = GlUtil.buildProgram(GlShaders.VERTEX, GlShaders.FRAGMENT)
        aPositionLoc = GLES30.glGetAttribLocation(program, "aPosition")
        aTexCoordLoc = GLES30.glGetAttribLocation(program, "aTexCoord")
        uTexMatrixLoc = GLES30.glGetUniformLocation(program, "uTexMatrix")
        uCameraTexLoc = GLES30.glGetUniformLocation(program, "uCameraTex")
        uLutLoc = GLES30.glGetUniformLocation(program, "uLut")
        uLutSizeLoc = GLES30.glGetUniformLocation(program, "uLutSize")
        uGrainTexLoc = GLES30.glGetUniformLocation(program, "uGrainTex")
        uGrainIntensityLoc = GLES30.glGetUniformLocation(program, "uGrainIntensity")
        uGrainSizeLoc = GLES30.glGetUniformLocation(program, "uGrainSize")
        uGrainLuminanceLoc = GLES30.glGetUniformLocation(program, "uGrainLuminance")
        uGrainTimeSeedLoc = GLES30.glGetUniformLocation(program, "uGrainTimeSeed")

        cameraTexId = createExternalTexture()
        lutTexId = uploadLut3D(lut)
        grainTexId = uploadGrainTexture()

        surfaceTexture = SurfaceTexture(cameraTexId).apply {
            setOnFrameAvailableListener { requestRender() }
        }
        onSurfaceTextureReady(surfaceTexture!!)
        Log.i(TAG, "GL surface created: LUT ${lut.dimension}³ uploaded")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        frameWidth = width
        frameHeight = height
    }

    fun setFrameCaptureListener(listener: FrameCaptureListener?) {
        frameCaptureListener = listener
    }

    fun requestFrameCapture() {
        shouldCaptureFrame = true
        requestRender()
    }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glUseProgram(program)

        // Camera external texture -> unit 0
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexId)
        GLES30.glUniform1i(uCameraTexLoc, 0)

        // LUT 3D texture -> unit 1
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexId)
        GLES30.glUniform1i(uLutLoc, 1)
        GLES30.glUniform1f(uLutSizeLoc, lut.dimension.toFloat())

        // Grain 2D texture -> unit 2
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, grainTexId)
        GLES30.glUniform1i(uGrainTexLoc, 2)

        // Grain parameters (Kodak Tri-X 400)
        GLES30.glUniform1f(uGrainIntensityLoc, 0.75f)
        GLES30.glUniform1f(uGrainSizeLoc, 1.0f)
        GLES30.glUniform1f(uGrainLuminanceLoc, 0.85f)

        // Grain time seed (subtle evolution, ~10 second cycle)
        frameCount++
        val grainTimeSeed = (sin(frameCount * 0.001) * 0.01f).toFloat()
        GLES30.glUniform1f(uGrainTimeSeedLoc, grainTimeSeed)

        GLES30.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)

        // Attributes: interleaved position(2) + texcoord(2), stride 4 floats
        val stride = 4 * Float.SIZE_BYTES
        quadBuffer.position(0)
        GLES30.glEnableVertexAttribArray(aPositionLoc)
        GLES30.glVertexAttribPointer(aPositionLoc, 2, GLES30.GL_FLOAT, false, stride, quadBuffer)
        quadBuffer.position(2)
        GLES30.glEnableVertexAttribArray(aTexCoordLoc)
        GLES30.glVertexAttribPointer(aTexCoordLoc, 2, GLES30.GL_FLOAT, false, stride, quadBuffer)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // Capture frame if requested
        if (shouldCaptureFrame && frameWidth > 0 && frameHeight > 0) {
            captureFrameBuffer()
            shouldCaptureFrame = false
        }

        GLES30.glDisableVertexAttribArray(aPositionLoc)
        GLES30.glDisableVertexAttribArray(aTexCoordLoc)
        GlUtil.checkGlError("onDrawFrame")
    }

    private fun createExternalTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val texId = ids[0]
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE,
        )
        GLES30.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE,
        )
        return texId
    }

    private fun captureFrameBuffer() {
        val pixelBuffer = ByteBuffer.allocateDirect(frameWidth * frameHeight * 4)
        pixelBuffer.order(ByteOrder.nativeOrder())

        GLES30.glReadPixels(
            0, 0, frameWidth, frameHeight,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
            pixelBuffer
        )

        val pixelArray = ByteArray(frameWidth * frameHeight * 4)
        pixelBuffer.rewind()
        pixelBuffer.get(pixelArray)

        // Flip vertically (OpenGL coordinates are bottom-up, image is top-down)
        val flippedPixels = flipImageVertically(pixelArray, frameWidth, frameHeight)

        frameCaptureListener?.onFrameCaptured(flippedPixels, frameWidth, frameHeight)
    }

    private fun flipImageVertically(pixelArray: ByteArray, width: Int, height: Int): ByteArray {
        val flipped = ByteArray(pixelArray.size)
        val bytesPerRow = width * 4

        for (y in 0 until height) {
            System.arraycopy(
                pixelArray,
                (height - 1 - y) * bytesPerRow,
                flipped,
                y * bytesPerRow,
                bytesPerRow
            )
        }

        return flipped
    }

    private fun uploadLut3D(lut: LUT3DGenerator.Lut3D): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val texId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texId)

        // Convert [0,1] floats to RGB8 bytes (universally linear-filterable).
        val bytes = ByteBuffer.allocateDirect(lut.rgb.size).order(ByteOrder.nativeOrder())
        for (v in lut.rgb) {
            bytes.put((v.coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte())
        }
        bytes.position(0)

        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8,
            lut.dimension, lut.dimension, lut.dimension, 0,
            GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, bytes,
        )
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GlUtil.checkGlError("uploadLut3D")
        return texId
    }

    private fun uploadGrainTexture(): Int {
        val grainData = GrainGenerator.generateNoiseTexture(512, 512)
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val texId = ids[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB8,
            512, 512, 0,
            GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE,
            ByteBuffer.wrap(grainData)
        )

        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)

        GlUtil.checkGlError("uploadGrainTexture")
        return texId
    }
}
