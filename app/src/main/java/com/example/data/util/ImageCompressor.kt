package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ImageCompressor {

  /**
   * Compresses an image picked from the phone gallery/storage to a medium resolution (max 800x800)
   * JPEG encoded in Base64 data URI format ("data:image/jpeg;base64,...").
   * This is optimal for medical radiography diagrams, anatomical sketches, and CBT diagrams,
   * avoiding consuming Supabase free storage quotas and loading universally and offline via Coil.
   */
  suspend fun compressToMediumDataUri(
    context: Context,
    uri: Uri,
    maxDimension: Int = 800,
    quality: Int = 78
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      // 1. Decode bounds
      val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
      } ?: return@withContext Result.failure(Exception("Unable to read image from device."))

      val origWidth = options.outWidth
      val origHeight = options.outHeight

      if (origWidth <= 0 || origHeight <= 0) {
        return@withContext Result.failure(Exception("Invalid image dimensions."))
      }

      // 2. Compute sample size
      var inSampleSize = 1
      while ((origWidth / inSampleSize) > maxDimension * 2 || (origHeight / inSampleSize) > maxDimension * 2) {
        inSampleSize *= 2
      }

      val decodeOptions = BitmapFactory.Options().apply {
        this.inSampleSize = inSampleSize
        inPreferredConfig = Bitmap.Config.RGB_565 // Memory and size optimal
      }

      val sampledBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
      } ?: return@withContext Result.failure(Exception("Could not decode image bitmap."))

      // 3. Scale down precisely to fit within maxDimension
      val currentW = sampledBitmap.width
      val currentH = sampledBitmap.height
      val scale = minOf(1.0f, maxDimension.toFloat() / maxOf(currentW, currentH))

      val finalBitmap = if (scale < 1.0f) {
        val targetW = (currentW * scale).toInt().coerceAtLeast(1)
        val targetH = (currentH * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(sampledBitmap, targetW, targetH, true).also {
          if (it != sampledBitmap) sampledBitmap.recycle()
        }
      } else {
        sampledBitmap
      }

      // 4. Compress to JPEG
      val byteStream = ByteArrayOutputStream()
      finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteStream)
      finalBitmap.recycle()

      val byteArray = byteStream.toByteArray()
      val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
      val dataUri = "data:image/jpeg;base64,$base64String"

      Result.success(dataUri)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
