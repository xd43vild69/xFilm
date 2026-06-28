package com.example.xfilm.rendering.media

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImageMetadata(
    val ev: Float,
    val iso: Int,
    val aperture: Float,
    val exposureSeconds: Float,
    val filmName: String = "Kodak Tri-X 400"
)

object ImageSaver {

    suspend fun saveRGBAToBitmap(
        rgbaPixels: ByteArray,
        width: Int,
        height: Int
    ): Bitmap = withContext(Dispatchers.Default) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Copiar RGBA bytes → Bitmap
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = rgbaPixels[i * 4].toInt() and 0xFF
            val g = rgbaPixels[i * 4 + 1].toInt() and 0xFF
            val b = rgbaPixels[i * 4 + 2].toInt() and 0xFF
            val a = rgbaPixels[i * 4 + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    }

    suspend fun saveBitmapToGalleryWithMetadata(
        context: Context,
        bitmap: Bitmap,
        filename: String,
        metadata: ImageMetadata
    ): Uri? = withContext(Dispatchers.IO) {
        return@withContext when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                saveToMediaStoreQ(context, bitmap, filename, metadata)
            }
            else -> {
                saveToLegacyGallery(context, bitmap, filename, metadata)?.let { file ->
                    Uri.fromFile(file)
                }
            }
        }
    }

    private suspend fun saveToMediaStoreQ(
        context: Context,
        bitmap: Bitmap,
        filename: String,
        metadata: ImageMetadata
    ): Uri? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }

                // Guardar metadatos EXIF
                try {
                    resolver.openInputStream(it)?.use { inputStream ->
                        val exif = ExifInterface(inputStream)
                        exif.setAttribute(
                            ExifInterface.TAG_USER_COMMENT,
                            "EV: ${String.format("%.1f", metadata.ev)} | " +
                            "ISO: ${metadata.iso} | " +
                            "f/${String.format("%.1f", metadata.aperture)} | " +
                            "${formatShutter(metadata.exposureSeconds)} | " +
                            "Film: ${metadata.filmName}"
                        )
                        exif.saveAttributes()
                    }
                } catch (e: Exception) {
                    // EXIF save is optional
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(it, values, null, null)
                }

                it
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun saveToLegacyGallery(
        context: Context,
        bitmap: Bitmap,
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

            val imageFile = File(picturesDir, filename)
            FileOutputStream(imageFile).use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }

            // Guardar metadatos EXIF
            val exif = ExifInterface(imageFile.absolutePath)
            exif.setAttribute(
                ExifInterface.TAG_USER_COMMENT,
                "EV: ${String.format("%.1f", metadata.ev)} | " +
                "ISO: ${metadata.iso} | " +
                "f/${String.format("%.1f", metadata.aperture)} | " +
                "${formatShutter(metadata.exposureSeconds)} | " +
                "Film: ${metadata.filmName}"
            )
            exif.saveAttributes()

            imageFile
        } catch (e: Exception) {
            null
        }
    }

    fun generateFilename(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "xfilm_$timestamp.jpg"
    }

    private fun formatShutter(seconds: Float): String {
        return when {
            seconds <= 0f -> "—"
            seconds >= 1f -> "${String.format("%.1f", seconds)}\""
            else -> "1/${(1f / seconds).toInt()}"
        }
    }
}
