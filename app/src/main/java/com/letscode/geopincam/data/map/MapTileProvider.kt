package com.letscode.geopincam.data.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.tan

/**
 * Builds the small map picture that sits beside the stamp text.
 *
 * Tiles come from the OpenStreetMap tile servers, which need no API key. The tiles
 * covering the fix are stitched together and cropped so the point sits exactly in
 * the middle, then a pin and the required attribution are drawn on top.
 *
 * Everything about this is best effort: with no network, or if a tile server says
 * no, the caller gets null and the stamp is rendered without a map.
 */
class MapTileProvider(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val composedMaps = LruCache<String, Bitmap>(COMPOSED_CACHE_ENTRIES)

    /** A [sizePx] square map centred on the coordinates, or null if unavailable. */
    suspend fun mapFor(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM,
        sizePx: Int = DEFAULT_SIZE_PX
    ): Bitmap? {
        val key = cacheKey(latitude, longitude, zoom, sizePx)
        composedMaps.get(key)?.let { return it }

        return withContext(ioDispatcher) {
            val map = compose(latitude, longitude, zoom, sizePx) ?: return@withContext null
            composedMaps.put(key, map)
            map
        }
    }

    /** The cached map for these coordinates, without touching the network. */
    fun cachedMapFor(
        latitude: Double,
        longitude: Double,
        zoom: Int = DEFAULT_ZOOM,
        sizePx: Int = DEFAULT_SIZE_PX
    ): Bitmap? = composedMaps.get(cacheKey(latitude, longitude, zoom, sizePx))

    private fun compose(latitude: Double, longitude: Double, zoom: Int, sizePx: Int): Bitmap? {
        val centreX = worldPixelX(longitude, zoom)
        val centreY = worldPixelY(latitude, zoom)
        val left = centreX - sizePx / 2.0
        val top = centreY - sizePx / 2.0

        val firstTileX = floor(left / TILE_SIZE).toInt()
        val lastTileX = floor((left + sizePx - 1) / TILE_SIZE).toInt()
        val firstTileY = floor(top / TILE_SIZE).toInt()
        val lastTileY = floor((top + sizePx - 1) / TILE_SIZE).toInt()

        val canvasBitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(canvasBitmap)
        canvas.drawColor(BACKDROP_COLOR)

        var drewAnything = false
        for (tileY in firstTileY..lastTileY) {
            for (tileX in firstTileX..lastTileX) {
                val tile = downloadTile(zoom, tileX, tileY) ?: continue
                canvas.drawBitmap(
                    tile,
                    (tileX * TILE_SIZE - left).toFloat(),
                    (tileY * TILE_SIZE - top).toFloat(),
                    null
                )
                tile.recycle()
                drewAnything = true
            }
        }

        if (!drewAnything) {
            canvasBitmap.recycle()
            return null
        }

        drawPin(canvas, sizePx / 2f, sizePx / 2f, sizePx * PIN_SIZE_RATIO)
        drawAttribution(canvas, sizePx)
        return canvasBitmap
    }

    private fun downloadTile(zoom: Int, tileX: Int, tileY: Int): Bitmap? {
        val wrapped = wrapTileIndex(tileX, zoom)
        if (tileY < 0 || tileY >= tileCount(zoom)) return null

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$TILE_BASE_URL/$zoom/$wrapped/$tileY.png").openConnection()
                as HttpURLConnection).apply {
                // The OSM tile usage policy requires an identifying User-Agent.
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Tile server returned ${connection.responseCode}")
                null
            } else {
                connection.inputStream.use { BitmapFactory.decodeStream(it) }
            }
        } catch (error: IOException) {
            Log.d(TAG, "Could not fetch tile $zoom/$wrapped/$tileY: ${error.message}")
            null
        } catch (error: OutOfMemoryError) {
            Log.w(TAG, "Out of memory decoding a map tile", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /** A teardrop pin, drawn centred on the point with its tip at the coordinates. */
    private fun drawPin(canvas: Canvas, x: Float, y: Float, size: Float) {
        val radius = size / 2f
        val tipY = y + size * PIN_TIP_RATIO
        val body = Path().apply {
            addCircle(x, y, radius, Path.Direction.CW)
            moveTo(x - radius * PIN_SHOULDER_RATIO, y + radius * PIN_SHOULDER_RATIO)
            lineTo(x, tipY)
            lineTo(x + radius * PIN_SHOULDER_RATIO, y + radius * PIN_SHOULDER_RATIO)
            close()
        }

        canvas.drawPath(
            body,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = PIN_OUTLINE_COLOR
                style = Paint.Style.STROKE
                strokeWidth = size * PIN_OUTLINE_RATIO
            }
        )
        canvas.drawPath(
            body,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PIN_COLOR }
        )
        canvas.drawCircle(
            x,
            y,
            radius * PIN_HOLE_RATIO,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        )
    }

    /** OpenStreetMap's licence requires visible attribution on the rendered map. */
    private fun drawAttribution(canvas: Canvas, sizePx: Int) {
        val textSize = sizePx * ATTRIBUTION_TEXT_RATIO
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
        }
        val width = paint.measureText(ATTRIBUTION)
        val padding = textSize * ATTRIBUTION_PADDING_RATIO

        canvas.drawRect(
            sizePx - width - padding * 2,
            sizePx - textSize - padding * 2,
            sizePx.toFloat(),
            sizePx.toFloat(),
            Paint().apply { color = ATTRIBUTION_BACKDROP }
        )
        canvas.drawText(ATTRIBUTION, sizePx - width - padding, sizePx - padding, paint)
    }

    private fun cacheKey(latitude: Double, longitude: Double, zoom: Int, sizePx: Int): String {
        val lat = Math.round(latitude * CELL_FACTOR) / CELL_FACTOR
        val lon = Math.round(longitude * CELL_FACTOR) / CELL_FACTOR
        return "$lat,$lon,$zoom,$sizePx"
    }

    private fun tileCount(zoom: Int) = 1 shl zoom

    /** Longitude wraps around the globe, so the tile column wraps with it. */
    private fun wrapTileIndex(tileX: Int, zoom: Int): Int {
        val count = tileCount(zoom)
        return ((tileX % count) + count) % count
    }

    private fun worldPixelX(longitude: Double, zoom: Int): Double =
        (longitude + HALF_TURN_DEGREES) / FULL_TURN_DEGREES * TILE_SIZE * tileCount(zoom)

    /** Web Mercator: latitude is projected before being scaled to pixels. */
    private fun worldPixelY(latitude: Double, zoom: Int): Double {
        val clamped = latitude.coerceIn(-MERCATOR_LIMIT_DEGREES, MERCATOR_LIMIT_DEGREES)
        val radians = clamped * PI / HALF_TURN_DEGREES
        val projected = asinh(tan(radians))
        return (1 - projected / PI) / 2 * TILE_SIZE * tileCount(zoom)
    }

    private companion object {
        const val TAG = "MapTileProvider"
        const val TILE_BASE_URL = "https://tile.openstreetmap.org"
        const val USER_AGENT = "GeoPinCam/1.0 (Android photo stamping app)"
        const val ATTRIBUTION = "© OpenStreetMap"

        const val TILE_SIZE = 256
        const val DEFAULT_ZOOM = 16
        const val DEFAULT_SIZE_PX = 256
        const val TIMEOUT_MILLIS = 8_000
        const val COMPOSED_CACHE_ENTRIES = 8
        const val CELL_FACTOR = 1000.0

        const val HALF_TURN_DEGREES = 180.0
        const val FULL_TURN_DEGREES = 360.0
        const val MERCATOR_LIMIT_DEGREES = 85.05112878

        const val BACKDROP_COLOR = 0xFFE8E4DE.toInt()
        const val PIN_COLOR = 0xFFE53935.toInt()
        const val PIN_OUTLINE_COLOR = 0xFFFFFFFF.toInt()
        const val ATTRIBUTION_BACKDROP = 0x99FFFFFF.toInt()

        const val PIN_SIZE_RATIO = 0.20f
        const val PIN_TIP_RATIO = 0.95f
        const val PIN_SHOULDER_RATIO = 0.72f
        const val PIN_OUTLINE_RATIO = 0.18f
        const val PIN_HOLE_RATIO = 0.34f
        const val ATTRIBUTION_TEXT_RATIO = 0.075f
        const val ATTRIBUTION_PADDING_RATIO = 0.35f
    }
}
