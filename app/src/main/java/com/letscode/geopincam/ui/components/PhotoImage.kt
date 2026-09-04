package com.letscode.geopincam.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.letscode.geopincam.utils.PhotoImageLoader

/**
 * A media store image decoded at the size it is displayed at.
 *
 * Loading happens off the main thread and is keyed on the URI, so scrolling the
 * gallery never blocks and never decodes a full resolution bitmap.
 */
@Composable
fun PhotoThumbnail(
    uri: Uri,
    sizePx: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    isVideo: Boolean = false
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, sizePx, isVideo) {
        value = PhotoImageLoader.loadThumbnail(context, uri, sizePx, isVideo)
    }

    PhotoContent(bitmap, contentDescription, modifier, contentScale)
}

/** A downsampled full view of a photo, sized for the detail screen. */
@Composable
fun PhotoPreview(
    uri: Uri,
    maxWidthPx: Int,
    maxHeightPx: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    /** Change this to force a re-decode after the file behind [uri] is rewritten. */
    revision: Int = 0
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri, maxWidthPx, maxHeightPx, revision) {
        value = PhotoImageLoader.loadPreview(context, uri, maxWidthPx, maxHeightPx)
    }

    PhotoContent(bitmap, contentDescription, modifier, contentScale)
}

@Composable
private fun PhotoContent(
    bitmap: Bitmap?,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Placeholder keeps the grid stable while the decode runs.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}
