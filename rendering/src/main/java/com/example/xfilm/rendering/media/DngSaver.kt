package com.example.xfilm.rendering.media

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves RAW images in DNG (Digital Negative) format to Pictures folder.
 * Compatible with Samsung Galaxy S24+ (50MP, 8160x6120).
 *
 * DNG Specification: Adobe DNG 1.6 - TIFF-based RAW format
 * Generates valid TIFF with all required tags for professional editing software
 */
object DngSaver {

    private const val TAG = "DngSaver"

    // TIFF constants
    private const val TIFF_LE = 0x4949.toShort()  // 'II' little-endian
    private const val TIFF_BE = 0x4D4D.toShort()  // 'MM' big-endian
    private const val TIFF_MAGIC = 42.toShort()


    suspend fun saveBayerRawToDng(
        context: Context,
        rawData: ByteArray,
        width: Int,
        height: Int,
        filename: String,
        metadata: ImageMetadata
    ): Uri? = withContext(Dispatchers.IO) {
        return@withContext when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                saveToMediaStoreQ(context, rawData, width, height, filename, metadata)
            }
            else -> {
                saveToLegacyGallery(context, rawData, width, height, filename, metadata)?.let { file ->
                    Uri.fromFile(file)
                }
            }
        }
    }

    private suspend fun saveToMediaStoreQ(
        context: Context,
        rawData: ByteArray,
        width: Int,
        height: Int,
        filename: String,
        metadata: ImageMetadata
    ): Uri? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/x-adobe-dng")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    writeDngTiff(outputStream, rawData, width, height, metadata)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, values, null, null)
                }

                Log.i(TAG, "DNG saved to MediaStore: $filename")
                it
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save DNG to MediaStore", e)
                null
            }
        }
    }

    private suspend fun saveToLegacyGallery(
        context: Context,
        rawData: ByteArray,
        width: Int,
        height: Int,
        filename: String,
        metadata: ImageMetadata
    ): File? = withContext(Dispatchers.IO) {
        try {
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "xFilm"
            )

            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }

            val dngFile = File(picturesDir, filename)
            FileOutputStream(dngFile).use { fos ->
                writeDngTiff(fos, rawData, width, height, metadata)
            }

            Log.i(TAG, "DNG saved to legacy: ${dngFile.absolutePath}")
            dngFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save DNG to legacy gallery", e)
            null
        }
    }

    /**
     * Write valid TIFF/DNG file structure compatible with Adobe DNG spec
     */
    private fun writeDngTiff(
        os: OutputStream,
        rawData: ByteArray,
        width: Int,
        height: Int,
        metadata: ImageMetadata
    ) {
        val headerSize = 8
        val ifdEntriesCount = 19
        val ifdSize = 2 + (ifdEntriesCount * 12) + 4 // count + entries + nextOffset = 234
        val cameraModelOffset = headerSize + ifdSize  // 8 + 234 = 242
        val colorMatrixOffset = cameraModelOffset + 12 // 242 + 12 = 254
        val pixelDataOffset = 336 // 254 + 72 + 10 bytes padding
        val stripByteCount = rawData.size

        val buffer = ByteBuffer.allocate(pixelDataOffset + rawData.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // 1. TIFF Header
        buffer.putShort(TIFF_LE)
        buffer.putShort(TIFF_MAGIC)
        buffer.putInt(8) // Offset to first IFD

        // 2. IFD entries (must be sorted by tag number!)
        buffer.putShort(ifdEntriesCount.toShort())

        writeTag(buffer, 254, 4, 1, 0)                             // NewSubfileType (0 = main image)
        writeTag(buffer, 256, 4, 1, width)                         // ImageWidth
        writeTag(buffer, 257, 4, 1, height)                        // ImageLength
        writeTag(buffer, 258, 3, 1, 16)                            // BitsPerSample (16-bit uncompressed)
        writeTag(buffer, 259, 3, 1, 1)                             // Compression (1 = uncompressed)
        writeTag(buffer, 262, 3, 1, 32803)                         // PhotometricInterpretation (32803 = CFA)
        writeTag(buffer, 273, 4, 1, pixelDataOffset)               // StripOffsets
        writeTag(buffer, 277, 3, 1, 1)                             // SamplesPerPixel
        writeTag(buffer, 278, 4, 1, height)                        // RowsPerStrip
        writeTag(buffer, 279, 4, 1, stripByteCount)                // StripByteCounts
        writeTag(buffer, 33422, 1, 4, 0x02010100)                  // CFAPattern (inline RGGB: 0, 1, 1, 2)
        writeTag(buffer, 50706, 1, 4, 0x00000401)                  // DNGVersion (1.4.0.0)
        writeTag(buffer, 50707, 1, 4, 0x00000101)                  // DNGBackwardVersion (1.1.0.0)
        writeTag(buffer, 50708, 2, 12, cameraModelOffset)          // UniqueCameraModel ("Samsung S24\u0000")
        writeTag(buffer, 50710, 1, 3, 0x00020100)                  // CFAPlaneColor (inline: 0, 1, 2)
        writeTag(buffer, 50711, 3, 1, 1)                           // CFALayout (1 = Rectangular)
        writeTag(buffer, 50714, 3, 1, 64)                          // BlackLevel
        writeTag(buffer, 50717, 4, 1, 65535)                       // WhiteLevel
        writeTag(buffer, 50721, 5, 9, colorMatrixOffset)           // ColorMatrix1 (points to 9 rational values)

        // Next IFD offset (0 = end of IFDs)
        buffer.putInt(0)

        // 3. Write out-of-line data
        // UniqueCameraModel string at cameraModelOffset (242)
        val cameraModelBytes = "Samsung S24\u0000".toByteArray(Charsets.US_ASCII)
        buffer.put(cameraModelBytes)

        // ColorMatrix1 data at colorMatrixOffset (254)
        // 9 Rational values (numerator, denominator) for identity matrix
        val matrix = intArrayOf(
            1, 1,  0, 1,  0, 1,
            0, 1,  1, 1,  0, 1,
            0, 1,  0, 1,  1, 1
        )
        for (v in matrix) {
            buffer.putInt(v)
        }

        // Pad to pixelDataOffset (336)
        while (buffer.position() < pixelDataOffset) {
            buffer.put(0.toByte())
        }

        // 4. Append pixel data
        buffer.put(rawData)

        // Write to output stream
        buffer.flip()
        os.write(buffer.array(), 0, buffer.limit())
        os.flush()

        Log.i(TAG, "TIFF written: ${width}x${height}, ${rawData.size} bytes")
    }

    private fun writeTag(buf: ByteBuffer, tag: Int, type: Int, count: Int, valueOrOffset: Int) {
        buf.putShort(tag.toShort())
        buf.putShort(type.toShort())
        buf.putInt(count)
        buf.putInt(valueOrOffset)
    }
}

