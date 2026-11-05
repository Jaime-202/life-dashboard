package com.lifedashboard.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log

object ImageUtils {
    private const val TAG = "ImageUtils"
    private const val MAX_DIMENSION = 1600

    /**
     * Lee una imagen reducida y bien orientada. Una foto de 50 MP decodificada a tamaño completo
     * ocupa cientos de MB y cerraría la app, y la IA tampoco necesita tanta resolución.
     */
    fun decodeForAnalysis(context: Context, uri: Uri): Bitmap? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val rotation = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        if (decoded != null && rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer la imagen: ${e.message}")
        null
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "Imagen demasiado grande para la memoria disponible")
        null
    }

    /** Mayor potencia de 2 que mantiene el lado largo por encima de [maxDimension]. */
    fun sampleSizeFor(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        val longestSide = maxOf(width, height)
        while (longestSide / (sampleSize * 2) >= maxDimension) sampleSize *= 2
        return sampleSize
    }
}
