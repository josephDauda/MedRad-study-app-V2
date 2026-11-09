package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

object ImageCompressor {

  /**
   * Compresses an image picked from the phone gallery/storage to a medium resolution (max 800x800)
   * JPEG encoded in Base64 data URI format ("data:image/jpeg;base64,...").
   * This is optimal for medical radiography diagrams, anatomical sketches, and CBT diagrams,
   * avoiding consuming Supabase free storage quotas and loading universally and offline.
   */
  suspend fun compressToMediumDataUri(
    context: Context,
    uri: Uri,
    maxDimension: Int = 640,
    quality: Int = 70
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      val rawBytes = readBytesFromUri(context, uri)
        ?: return@withContext Result.failure(Exception("Unable to read image bytes from device. Please check file access permissions."))

      compressBytesToMediumDataUri(rawBytes, maxDimension, quality)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Compresses raw image bytes to a medium resolution Base64 data URI.
   */
  fun compressBytesToMediumDataUri(
    rawBytes: ByteArray,
    maxDimension: Int = 640,
    quality: Int = 70
  ): Result<String> {
    try {
      if (rawBytes.isEmpty()) {
        return Result.failure(Exception("Image file is empty."))
      }

      // 1. Decode bounds
      val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
      }
      BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)

      val origWidth = options.outWidth
      val origHeight = options.outHeight

      if (origWidth <= 0 || origHeight <= 0) {
        return Result.failure(Exception("Invalid or unsupported image format."))
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

      val sampledBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
        ?: return Result.failure(Exception("Could not decode image bitmap."))

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

      return Result.success(dataUri)
    } catch (e: Exception) {
      return Result.failure(e)
    }
  }

  /**
   * Resilient fallback reader for Android ContentResolver and File URIs.
   * Handles PhotoPicker URIs, Downloads URIs, DocumentProvider URIs, and direct file paths.
   */
  private fun readBytesFromUri(context: Context, uri: Uri): ByteArray? {
    // 1. Try ContentResolver openInputStream
    try {
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val bytes = stream.readBytes()
        if (bytes.isNotEmpty()) return bytes
      }
    } catch (_: Throwable) {}

    // 2. Try openFileDescriptor
    try {
      context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        FileInputStream(pfd.fileDescriptor).use { fis ->
          val bytes = fis.readBytes()
          if (bytes.isNotEmpty()) return bytes
        }
      }
    } catch (_: Throwable) {}

    // 3. Try openAssetFileDescriptor
    try {
      context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
        afd.createInputStream().use { stream ->
          val bytes = stream.readBytes()
          if (bytes.isNotEmpty()) return bytes
        }
      }
    } catch (_: Throwable) {}

    // 4. Try Direct File reading if file scheme or path
    try {
      val path = uri.path
      if (!path.isNullOrBlank()) {
        val file = File(path)
        if (file.exists() && file.canRead()) {
          val bytes = file.readBytes()
          if (bytes.isNotEmpty()) return bytes
        }
      }
    } catch (_: Throwable) {}

    return null
  }
}
