package com.letscode.geopincam.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.util.Size
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Loads photos for the gallery grid and the detail screen.
 *
 * Full resolution bitmaps are never decoded for display: thumbnails come from the
 * media store's own thumbnail service where available, and every other decode is
 * downsampled to the size actually needed. Thumbnails are kept in a memory cache
 * sized as a fraction of the heap.
 */
object PhotoImageLoader {

    private const val TAG = "PhotoImageLoader"
    private const val CACHE_HEAP_FRACTION = 8
    private const val BYTES_PER_KILOBYTE = 1024

    private val thumbnailCache: LruCache<String, Bitmap> by lazy {
        val maxKilobytes = (Runtime.getRuntime().maxMemory() / BYTES_PER_KILOBYTE).toInt()
        object : LruCache<String, Bitmap>(maxKilobytes / CACHE_HEAP_FRACTION) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                value.byteCount / BYTES_PER_KILOBYTE
        }
    }

    /** A square-ish thumbnail of at most [sizePx], cached in memory. */
    suspend fun loadThumbnail(
        context: Context,
        uri: Uri,
        sizePx: Int,
        isVideo: Boolean = false,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Bitmap? {
        val key = "$uri@$sizePx"
        thumbnailCache.get(key)?.let { return it }

        return withContext(dispatcher) {
            val bitmap = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    loadSystemThumbnail(context, uri, sizePx)
                        ?: if (isVideo) {
                            decodeVideoFrame(context, uri)
                        } else {
                            decodeDownsampled(context, uri, sizePx, sizePx)
                        }

                isVideo -> decodeVideoFrame(context, uri)
                else -> decodeDownsampled(context, uri, sizePx, sizePx)
            }
            bitmap?.also { thumbnailCache.put(key, it) }
        }
    }

    /** First frame of a recording, for gallery thumbnails on API 28 and below. */
    private fun decodeVideoFrame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.frameAtTime
        } catch (error: RuntimeException) {
            Log.w(TAG, "Could not read a frame from $uri", error)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** A downsampled copy that fits within [maxWidth] x [maxHeight], not cached. */
    suspend fun loadPreview(
        context: Context,
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): Bitmap? = withContext(dispatcher) {
        decodeDownsampled(context, uri, maxWidth, maxHeight)
    }

    fun clearCache() = thumbnailCache.evictAll()

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun loadSystemThumbnail(context: Context, uri: Uri, sizePx: Int): Bitmap? = try {
        context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
    } catch (error: IOException) {
        Log.d(TAG, "No media store thumbnail for $uri")
        null
    } catch (error: SecurityException) {
        Log.w(TAG, "Not allowed to read a thumbnail for $uri", error)
        null
    }

    /**
     * Reads the image bounds first and decodes with the smallest sample size that
     * still covers the requested box, so a 50 MP photo never lands on the heap.
     */
    private fun decodeDownsampled(
        context: Context,
        uri: Uri,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }
    } catch (error: IOException) {
        Log.w(TAG, "Could not decode $uri", error)
        null
    } catch (error: SecurityException) {
        Log.w(TAG, "Not allowed to read $uri", error)
        null
    } catch (error: OutOfMemoryError) {
        Log.e(TAG, "Out of memory decoding $uri", error)
        null
    }

    internal fun sampleSizeFor(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        var sampleSize = 1
        if (maxWidth <= 0 || maxHeight <= 0) return sampleSize
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (halfWidth / sampleSize >= maxWidth && halfHeight / sampleSize >= maxHeight) {
            sampleSize *= 2
        }
        return sampleSize
    }
}
