package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

object ImageUrlResolver {
  /**
   * Resolves common external image links to direct image streaming endpoints:
   * - Google Drive file share links -> direct streaming thumbnail / content URL
   * - Dropbox links -> raw direct download URL
   */
  fun resolveDirectImageUrl(url: String): String {
    val trimmed = url.trim()

    // 1. Google Drive direct stream translation
    if (trimmed.contains("drive.google.com", ignoreCase = true)) {
      // Pattern: /file/d/{FILE_ID}/
      val fileIdMatch = Regex("""/file/d/([a-zA-Z0-9_-]+)""").find(trimmed)
      val fileId = fileIdMatch?.groupValues?.getOrNull(1)
        ?: Regex("""[?&]id=([a-zA-Z0-9_-]+)""").find(trimmed)?.groupValues?.getOrNull(1)

      if (!fileId.isNullOrBlank()) {
        // High quality direct image content CDN from Google
        return "https://lh3.googleusercontent.com/d/$fileId"
      }
    }

    // 2. Dropbox direct download translation
    if (trimmed.contains("dropbox.com", ignoreCase = true)) {
      if (trimmed.contains("?dl=0")) {
        return trimmed.replace("?dl=0", "?raw=1")
      } else if (!trimmed.contains("raw=1")) {
        return if (trimmed.contains("?")) "$trimmed&raw=1" else "$trimmed?raw=1"
      }
    }

    return trimmed
  }
}

/**
 * Universal diagram and medical radiography image loader.
 * Supports:
 * 1. Base64 data URIs ("data:image/jpeg;base64,...") decoded directly to Bitmap
 * 2. Google Drive image preview links
 * 3. Dropbox links
 * 4. Standard HTTP / HTTPS image links
 */
@Composable
fun QuestionDiagramImage(
  imageUrl: String?,
  contentDescription: String = "Radiographic anatomical diagram or question illustration",
  modifier: Modifier = Modifier
    .fillMaxWidth()
    .heightIn(max = 240.dp)
    .clip(RoundedCornerShape(8.dp)),
  contentScale: ContentScale = ContentScale.Fit
) {
  if (imageUrl.isNullOrBlank()) return

  val trimmedUrl = imageUrl.trim()

  // Case 1: Base64 data URI
  if (trimmedUrl.startsWith("data:", ignoreCase = true)) {
    val bitmap = remember(trimmedUrl) {
      try {
        val commaIndex = trimmedUrl.indexOf(',')
        if (commaIndex != -1 && commaIndex < trimmedUrl.length - 1) {
          val base64Data = trimmedUrl.substring(commaIndex + 1)
          val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
          BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } else {
          null
        }
      } catch (_: Exception) {
        null
      }
    }

    if (bitmap != null) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
      )
    } else {
      ImageLoadFallback(message = "Unable to decode attached diagram data")
    }
    return
  }

  // Case 2: Web URL (Google Drive / Dropbox / Direct Image Link)
  val resolvedUrl = remember(trimmedUrl) {
    ImageUrlResolver.resolveDirectImageUrl(trimmedUrl)
  }

  val context = LocalContext.current
  var isError by remember(resolvedUrl) { mutableStateOf(false) }
  var isLoading by remember(resolvedUrl) { mutableStateOf(true) }

  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    if (isLoading) {
      CircularProgressIndicator(
        modifier = Modifier.size(28.dp),
        strokeWidth = 2.5.dp,
        color = MaterialTheme.colorScheme.primary
      )
    }

    if (isError) {
      ImageLoadFallback(message = "Diagram image unavailable from link")
    } else {
      AsyncImage(
        model = ImageRequest.Builder(context)
          .data(resolvedUrl)
          .crossfade(true)
          .build(),
        contentDescription = contentDescription,
        modifier = Modifier.matchParentSize(),
        contentScale = contentScale,
        onLoading = { isLoading = true; isError = false },
        onSuccess = { isLoading = false; isError = false },
        onError = { isLoading = false; isError = true }
      )
    }
  }
}

@Composable
private fun ImageLoadFallback(message: String) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(140.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(12.dp)
    ) {
      Icon(
        imageVector = Icons.Default.BrokenImage,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
        modifier = Modifier.size(32.dp)
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
