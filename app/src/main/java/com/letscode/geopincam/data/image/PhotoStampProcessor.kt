package com.letscode.geopincam.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import androidx.core.graphics.withClip
import androidx.exifinterface.media.ExifInterface
import com.letscode.geopincam.domain.model.StampPosition
import com.letscode.geopincam.domain.model.StampTextAlignment
import com.letscode.geopincam.domain.usecase.StampLine
import com.letscode.geopincam.domain.usecase.StampLineStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/** Everything the renderer needs to draw the overlay, with no framework state. */
data class StampRenderSpec(
    val lines: List<StampLine>,
    val position: StampPosition,
    val alignment: StampTextAlignment,
    val fontScale: Float,
    val backgroundOpacity: Float,

    /** Square map picture drawn to the left of the text, when one is available. */
    val mapImage: Bitmap? = null
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/** Result of stamping one photograph. */
data class ProcessedPhoto(
    val file: File,
    val width: Int,
    val height: Int,

    /**
     * Tags read off the capture, handed back so the caller can write them and the
     * location metadata in a single Exif pass instead of rewriting the JPEG twice.
     */
    val carriedExif: Map<String, String> = emptyMap(),

    /**
     * True when the Exif rotation was baked into the pixels, so the saved file must
     * declare a normal orientation.
     */
    val orientationBaked: Boolean = true
)

/**
 * Draws the GPS stamp onto a captured photograph and writes the result as JPEG.
 *
 * Kept free of Activity and Composable references so it can run on a background
 * dispatcher. Bitmap handling is deliberately frugal: the source is decoded once
 * as a mutable bitmap and drawn on in place, and a decode that runs out of memory
 * is retried at a smaller sample size instead of crashing the app.
 */
class PhotoStampProcessor(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Stamps [source] and writes the JPEG to [target].
     *
     * @param spec null or empty to copy the photo through with no overlay.
     */
    suspend fun process(
        source: File,
        target: File,
        spec: StampRenderSpec?,
        jpegQuality: Int
    ): Result<ProcessedPhoto> = withContext(dispatcher) {
        var bitmap: Bitmap? = null
        try {
            val exif = readExif(source)
            val carried = carriedTags(exif)
            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            // With nothing to draw there is nothing a decode and re-encode would
            // change, and skipping that round trip is the difference between a
            // couple of seconds and a file copy.
            if (spec == null || spec.isEmpty) {
                return@withContext copyThrough(source, target, carried, orientation)
            }

            val image = decodeMutable(source)
                ?: return@withContext Result.failure(IOException("Could not decode ${source.name}"))
            bitmap = image

            // The overlay is drawn through the inverse of the Exif rotation instead
            // of turning the pixels first. Rotating a 12 MP bitmap costs a second
            // full-size allocation and copy - the most expensive step after the
            // encode - and the orientation flag is carried over to the result, so
            // every viewer still agrees on which way is up.
            drawStampOriented(image, spec, orientation)

            target.parentFile?.mkdirs()
            BufferedOutputStream(target.outputStream(), IO_BUFFER_BYTES).use { output ->
                if (!image.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)) {
                    return@withContext Result.failure(IOException("JPEG encoding failed"))
                }
            }

            // The Exif block is left to the caller, which writes the capture
            // metadata into the same single pass over the file.
            val quarterTurned = isQuarterTurn(orientation)
            Result.success(
                ProcessedPhoto(
                    file = target,
                    width = if (quarterTurned) image.height else image.width,
                    height = if (quarterTurned) image.width else image.height,
                    carriedExif = carried +
                        (ExifInterface.TAG_ORIENTATION to orientation.toString()),
                    orientationBaked = false
                )
            )
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while stamping", error)
            Result.failure(IOException("Not enough memory to process this photo"))
        } catch (error: IOException) {
            Log.e(TAG, "Failed to stamp photo", error)
            Result.failure(error)
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * Stamps the in-memory capture [sourceBytes] and writes the JPEG to [target].
     *
     * This is the live capture path: the sensor JPEG never touches the disk before
     * it is stamped, which takes a read and a write off the time between the
     * shutter and the photo landing in the gallery. [rotationDegrees] is the
     * clockwise turn CameraX reports for the frame, since the raw buffer carries
     * no orientation of its own.
     */
    suspend fun process(
        sourceBytes: ByteArray,
        rotationDegrees: Int,
        target: File,
        spec: StampRenderSpec?,
        jpegQuality: Int
    ): Result<ProcessedPhoto> = withContext(dispatcher) {
        var bitmap: Bitmap? = null
        try {
            val carried = carriedTags(readExif(sourceBytes))
            val orientation = orientationFor(rotationDegrees)

            if (spec == null || spec.isEmpty) {
                return@withContext copyThrough(sourceBytes, target, carried, orientation)
            }

            val image = decodeMutable(sourceBytes)
                ?: return@withContext Result.failure(IOException("Could not decode the capture"))
            bitmap = image

            drawStampOriented(image, spec, orientation)

            target.parentFile?.mkdirs()
            BufferedOutputStream(target.outputStream(), IO_BUFFER_BYTES).use { output ->
                if (!image.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)) {
                    return@withContext Result.failure(IOException("JPEG encoding failed"))
                }
            }

            val quarterTurned = isQuarterTurn(orientation)
            Result.success(
                ProcessedPhoto(
                    file = target,
                    width = if (quarterTurned) image.height else image.width,
                    height = if (quarterTurned) image.width else image.height,
                    carriedExif = carried +
                        (ExifInterface.TAG_ORIENTATION to orientation.toString()),
                    orientationBaked = false
                )
            )
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while stamping", error)
            Result.failure(IOException("Not enough memory to process this photo"))
        } catch (error: IOException) {
            Log.e(TAG, "Failed to stamp photo", error)
            Result.failure(error)
        } finally {
            bitmap?.recycle()
        }
    }

    /**
     * Rewrites [source] into [target] turned by [degrees] clockwise.
     *
     * Used by the photo detail screen; the rotation is baked into the pixels and
     * the Exif orientation is reset, so every viewer agrees on which way is up.
     */
    suspend fun rotate(
        source: File,
        target: File,
        degrees: Int,
        jpegQuality: Int
    ): Result<ProcessedPhoto> = withContext(dispatcher) {
        var bitmap: Bitmap? = null
        try {
            val exif = readExif(source)
            bitmap = decodeMutable(source)
                ?: return@withContext Result.failure(IOException("Could not decode ${source.name}"))

            // Honour any orientation flag first, then apply the user's turn, so the
            // result is right whatever the original file claimed.
            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL
            bitmap = applyOrientation(bitmap, orientation)
            bitmap = turn(bitmap, degrees)

            target.parentFile?.mkdirs()
            target.outputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)) {
                    return@withContext Result.failure(IOException("JPEG encoding failed"))
                }
            }
            val result = ProcessedPhoto(target, bitmap.width, bitmap.height)
            copyExif(exif, target)
            Result.success(result)
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "Out of memory while rotating", error)
            Result.failure(IOException("Not enough memory to rotate this photo"))
        } catch (error: IOException) {
            Log.e(TAG, "Failed to rotate photo", error)
            Result.failure(error)
        } finally {
            bitmap?.recycle()
        }
    }

    private fun turn(bitmap: Bitmap, degrees: Int): Bitmap {
        val normalized = ((degrees % FULL_TURN) + FULL_TURN) % FULL_TURN
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return try {
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (error: OutOfMemoryError) {
            Log.w(TAG, "Not enough memory to turn the photo", error)
            bitmap
        }
    }

    /**
     * Copies the captured JPEG across untouched, reading only its header for the
     * dimensions. The orientation flag travels with it rather than being baked in,
     * because without an overlay the pixels never have to be turned at all.
     */
    private fun copyThrough(
        source: File,
        target: File,
        carried: Map<String, String>,
        orientation: Int
    ): Result<ProcessedPhoto> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return Result.failure(IOException("Could not decode ${source.name}"))
        }

        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, IO_BUFFER_BYTES) }
        }

        // The media store wants the dimensions as they will be displayed.
        val quarterTurned = isQuarterTurn(orientation)
        return Result.success(
            ProcessedPhoto(
                file = target,
                width = if (quarterTurned) bounds.outHeight else bounds.outWidth,
                height = if (quarterTurned) bounds.outWidth else bounds.outHeight,
                carriedExif = carried,
                orientationBaked = false
            )
        )
    }

    /** [copyThrough] for the in-memory capture path: the bytes are already decoded. */
    private fun copyThrough(
        source: ByteArray,
        target: File,
        carried: Map<String, String>,
        orientation: Int
    ): Result<ProcessedPhoto> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return Result.failure(IOException("Could not decode the capture"))
        }

        target.parentFile?.mkdirs()
        target.outputStream().use { output -> output.write(source) }

        val quarterTurned = isQuarterTurn(orientation)
        return Result.success(
            ProcessedPhoto(
                file = target,
                width = if (quarterTurned) bounds.outHeight else bounds.outWidth,
                height = if (quarterTurned) bounds.outWidth else bounds.outHeight,
                // The raw buffer has no orientation of its own, so hand it on for
                // the metadata pass to write.
                carriedExif = carried + (ExifInterface.TAG_ORIENTATION to orientation.toString()),
                orientationBaked = false
            )
        )
    }

    /** The Exif orientation that stands for a clockwise turn of [rotationDegrees]. */
    private fun orientationFor(rotationDegrees: Int, mirrored: Boolean = false): Int {
        val r = ((rotationDegrees % FULL_TURN) + FULL_TURN) % FULL_TURN
        return if (!mirrored) {
            when (r) {
                90 -> ExifInterface.ORIENTATION_ROTATE_90
                180 -> ExifInterface.ORIENTATION_ROTATE_180
                270 -> ExifInterface.ORIENTATION_ROTATE_270
                else -> ExifInterface.ORIENTATION_NORMAL
            }
        } else {
            when (r) {
                90 -> ExifInterface.ORIENTATION_TRANSVERSE
                180 -> ExifInterface.ORIENTATION_FLIP_VERTICAL
                270 -> ExifInterface.ORIENTATION_TRANSPOSE
                else -> ExifInterface.ORIENTATION_FLIP_HORIZONTAL
            }
        }
    }

    /**
     * Draws the stamp onto an arbitrary [canvas] whose backing buffer is
     * [bufferWidth] x [bufferHeight], turned so it reads upright after the viewer
     * applies [rotationDegrees] (and a horizontal flip when [mirrored]). This is
     * the video overlay path: it never allocates and leaves the canvas as it
     * found it. [spec] must carry no map image - see the call site.
     */
    fun drawStampOnCanvas(
        canvas: Canvas,
        bufferWidth: Int,
        bufferHeight: Int,
        rotationDegrees: Int,
        mirrored: Boolean,
        spec: StampRenderSpec
    ) {
        if (spec.isEmpty || bufferWidth <= 0 || bufferHeight <= 0) return
        val orientation = orientationFor(rotationDegrees, mirrored)
        val toDisplay = orientationMatrix(orientation, bufferWidth, bufferHeight)
        val toBuffer = Matrix()
        val checkpoint = canvas.save()
        try {
            if (!toDisplay.isIdentity && toDisplay.invert(toBuffer)) {
                canvas.concat(toBuffer)
                val quarterTurned = isQuarterTurn(orientation)
                drawStamp(
                    canvas = canvas,
                    width = if (quarterTurned) bufferHeight else bufferWidth,
                    height = if (quarterTurned) bufferWidth else bufferHeight,
                    spec = spec
                )
            } else {
                drawStamp(canvas, bufferWidth, bufferHeight, spec)
            }
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    private fun carriedTags(source: ExifInterface?): Map<String, String> {
        if (source == null) return emptyMap()
        return COPIED_EXIF_TAGS.mapNotNull { tag ->
            source.getAttribute(tag)?.let { tag to it }
        }.toMap()
    }

    /**
     * Decodes the file into a mutable bitmap, downsampling only as far as memory
     * pressure requires. Full resolution is preserved whenever it fits.
     */
    private fun decodeMutable(source: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = initialSampleSize(bounds.outWidth, bounds.outHeight)
        while (sampleSize <= MAX_SAMPLE_SIZE) {
            try {
                val options = BitmapFactory.Options().apply {
                    inMutable = true
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(source.absolutePath, options)?.let { return it }
            } catch (error: OutOfMemoryError) {
                Log.w(TAG, "Decode failed at sample size $sampleSize, halving resolution", error)
            }
            sampleSize *= 2
        }
        return null
    }

    /** [decodeMutable] for the in-memory capture path. */
    private fun decodeMutable(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = initialSampleSize(bounds.outWidth, bounds.outHeight)
        while (sampleSize <= MAX_SAMPLE_SIZE) {
            try {
                val options = BitmapFactory.Options().apply {
                    inMutable = true
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { return it }
            } catch (error: OutOfMemoryError) {
                Log.w(TAG, "Decode failed at sample size $sampleSize, halving resolution", error)
            }
            sampleSize *= 2
        }
        return null
    }

    /** Keeps very large sensors within a sane pixel budget before the first attempt. */
    private fun initialSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var pixels = width.toLong() * height.toLong()
        while (pixels > MAX_PIXELS && sampleSize < MAX_SAMPLE_SIZE) {
            sampleSize *= 2
            pixels /= 4
        }
        return sampleSize
    }

    /**
     * Bakes the Exif orientation into the pixels so the stamp is drawn the right
     * way up and the saved file needs no orientation flag of its own.
     */
    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(QUARTER_TURN)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(HALF_TURN)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(THREE_QUARTER_TURN)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(QUARTER_TURN)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(THREE_QUARTER_TURN)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (error: OutOfMemoryError) {
            Log.w(TAG, "Not enough memory to rotate; stamping unrotated pixels", error)
            bitmap
        }
    }

    /** True when [orientation] swaps the width and the height on screen. */
    private fun isQuarterTurn(orientation: Int): Boolean =
        orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE

    /**
     * Draws the stamp so it reads correctly once [orientation] has been applied by
     * the viewer, without ever turning the pixels.
     *
     * The canvas is concatenated with the inverse of the orientation transform, so
     * the layout below can go on thinking in upright, on-screen coordinates.
     */
    private fun drawStampOriented(bitmap: Bitmap, spec: StampRenderSpec, orientation: Int) {
        val canvas = Canvas(bitmap)
        val toDisplay = orientationMatrix(orientation, bitmap.width, bitmap.height)
        val toPixels = Matrix()
        if (toDisplay.isIdentity || !toDisplay.invert(toPixels)) {
            drawStamp(canvas, bitmap.width, bitmap.height, spec)
            return
        }
        canvas.concat(toPixels)
        val quarterTurned = isQuarterTurn(orientation)
        drawStamp(
            canvas = canvas,
            width = if (quarterTurned) bitmap.height else bitmap.width,
            height = if (quarterTurned) bitmap.width else bitmap.height,
            spec = spec
        )
    }

    /**
     * The transform an [orientation] flag stands for, mapping stored pixels onto
     * the picture as it is displayed. Composed the way Bitmap.createBitmap would,
     * including the shift that keeps the result in positive coordinates.
     */
    private fun orientationMatrix(orientation: Int, width: Int, height: Int): Matrix {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(QUARTER_TURN)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(HALF_TURN)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(THREE_QUARTER_TURN)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(QUARTER_TURN)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(THREE_QUARTER_TURN)
                matrix.postScale(-1f, 1f)
            }
            else -> return matrix
        }
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        matrix.mapRect(bounds)
        matrix.postTranslate(-bounds.left, -bounds.top)
        return matrix
    }

    /** Draws the translucent panel and its text lines onto [bitmap] in place. */
    fun drawStamp(bitmap: Bitmap, spec: StampRenderSpec) =
        drawStamp(Canvas(bitmap), bitmap.width, bitmap.height, spec)

    /** Lays the stamp out for a [width] x [height] picture on the given canvas. */
    private fun drawStamp(canvas: Canvas, width: Int, height: Int, spec: StampRenderSpec) {
        val base = minOf(width, height).toFloat()
        val margin = base * MARGIN_RATIO
        val maxPanelWidth = width - margin * 2

        var bodySize = base * spec.fontScale
        var paints = buildPaints(bodySize)
        var padding = bodySize * PADDING_RATIO
        var widest = widestLine(spec.lines, paints)
        var metrics = blockMetrics(spec, paints, bodySize)

        // Shrink the whole block rather than clipping when a line would overflow.
        // The map is square and as tall as the text, so it shrinks along with it.
        val available = maxPanelWidth - padding * 2 - metrics.mapWidth
        if (widest > available && available > 0f) {
            val shrink = (available / widest).coerceAtLeast(MIN_SHRINK_FACTOR)
            bodySize *= shrink
            paints = buildPaints(bodySize)
            padding = bodySize * PADDING_RATIO
            widest = widestLine(spec.lines, paints)
            metrics = blockMetrics(spec, paints, bodySize)
        }

        val panelWidth = (widest + metrics.mapWidth + padding * 2).coerceAtMost(maxPanelWidth)
        val panelHeight = metrics.textHeight + padding * 2
        val panelLeft = when (spec.position) {
            StampPosition.TOP_LEFT, StampPosition.BOTTOM_LEFT -> margin
            StampPosition.TOP_CENTER, StampPosition.BOTTOM_CENTER ->
                (width - panelWidth) / 2f
            StampPosition.TOP_RIGHT, StampPosition.BOTTOM_RIGHT ->
                width - margin - panelWidth
        }
        val panelTop = if (spec.position.isTop) margin else height - margin - panelHeight
        val panel = RectF(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight)
        val radius = bodySize * CORNER_RADIUS_RATIO

        if (spec.backgroundOpacity > 0f) {
            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                alpha = (spec.backgroundOpacity * MAX_ALPHA).toInt().coerceIn(0, MAX_ALPHA)
            }
            canvas.drawRoundRect(panel, radius, radius, background)
        }

        spec.mapImage?.let { map ->
            drawMap(
                canvas = canvas,
                map = map,
                target = RectF(
                    panel.left + padding,
                    panel.top + padding,
                    panel.left + padding + metrics.mapSize,
                    panel.top + padding + metrics.mapSize
                ),
                radius = radius * MAP_CORNER_RATIO
            )
        }

        val textLeft = panel.left + padding + metrics.mapWidth
        val textRight = panel.right - padding
        var baseline = panel.top + padding
        val lineGap = bodySize * LINE_GAP_RATIO

        spec.lines.forEachIndexed { index, line ->
            val paint = paints.forStyle(line.style)
            baseline += -paint.fontMetrics.top
            val x = when (spec.alignment) {
                StampTextAlignment.START -> textLeft
                StampTextAlignment.CENTER ->
                    (textLeft + textRight) / 2f - paint.measureText(line.text) / 2f
                StampTextAlignment.END -> textRight - paint.measureText(line.text)
            }
            canvas.drawText(line.text, x, baseline, paint)
            baseline += paint.fontMetrics.bottom + if (index < spec.lines.lastIndex) lineGap else 0f
        }
    }

    /** Text block height plus the width the map panel occupies beside it. */
    private fun blockMetrics(
        spec: StampRenderSpec,
        paints: StampPaints,
        bodySize: Float
    ): BlockMetrics {
        val lineGap = bodySize * LINE_GAP_RATIO
        val textHeight = spec.lines.sumOf { paints.forStyle(it.style).lineHeight().toDouble() }
            .toFloat() + lineGap * (spec.lines.size - 1).coerceAtLeast(0)
        val mapSize = if (spec.mapImage != null) textHeight else 0f
        val mapWidth = if (spec.mapImage != null) mapSize + bodySize * PADDING_RATIO else 0f
        return BlockMetrics(textHeight = textHeight, mapSize = mapSize, mapWidth = mapWidth)
    }

    /** Draws the map square with rounded corners, scaled to fill without stretching. */
    private fun drawMap(canvas: Canvas, map: Bitmap, target: RectF, radius: Float) {
        val clip = Path().apply { addRoundRect(target, radius, radius, Path.Direction.CW) }
        canvas.withClip(clip) {
            drawBitmap(map, null, target, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        canvas.drawRoundRect(
            target,
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = MAP_BORDER_ALPHA
                style = Paint.Style.STROKE
                strokeWidth = radius * MAP_BORDER_RATIO
            }
        )
    }

    private data class BlockMetrics(
        val textHeight: Float,
        val mapSize: Float,
        val mapWidth: Float
    )

    private fun widestLine(lines: List<StampLine>, paints: StampPaints): Float =
        lines.maxOfOrNull { paints.forStyle(it.style).measureText(it.text) } ?: 0f

    private fun buildPaints(bodySize: Float) = StampPaints(
        title = textPaint(bodySize * TITLE_RATIO, Typeface.create(FONT_FAMILY, Typeface.BOLD), MAX_ALPHA),
        body = textPaint(bodySize, Typeface.create(FONT_FAMILY, Typeface.NORMAL), BODY_ALPHA),
        footer = textPaint(
            bodySize * FOOTER_RATIO,
            Typeface.create(FONT_FAMILY, Typeface.NORMAL),
            FOOTER_ALPHA
        )
    )

    private fun textPaint(size: Float, typeface: Typeface, textAlpha: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = textAlpha
            textSize = size
            this.typeface = typeface
            // A soft shadow keeps the text legible even at zero background opacity.
            setShadowLayer(size * SHADOW_RADIUS_RATIO, 0f, 0f, Color.BLACK)
        }

    private fun readExif(file: File): ExifInterface? = try {
        ExifInterface(file)
    } catch (error: IOException) {
        Log.w(TAG, "Could not read Exif from ${file.name}", error)
        null
    }

    private fun readExif(bytes: ByteArray): ExifInterface? = try {
        ExifInterface(ByteArrayInputStream(bytes))
    } catch (error: IOException) {
        Log.w(TAG, "Could not read Exif from the capture", error)
        null
    }

    /**
     * Carries the capture metadata over to the stamped file. Orientation is reset
     * because the rotation is already baked into the pixels.
     */
    private fun copyExif(source: ExifInterface?, target: File) {
        if (source == null) return
        try {
            val exif = ExifInterface(target)
            COPIED_EXIF_TAGS.forEach { tag ->
                source.getAttribute(tag)?.let { exif.setAttribute(tag, it) }
            }
            exif.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL.toString()
            )
            exif.saveAttributes()
        } catch (error: IOException) {
            Log.w(TAG, "Could not copy Exif to ${target.name}", error)
        }
    }

    private class StampPaints(val title: Paint, val body: Paint, val footer: Paint) {
        fun forStyle(style: StampLineStyle): Paint = when (style) {
            StampLineStyle.TITLE -> title
            StampLineStyle.BODY -> body
            StampLineStyle.FOOTER -> footer
        }
    }

    private companion object {
        const val TAG = "PhotoStampProcessor"
        const val FONT_FAMILY = "sans-serif"

        /** ~48 MP, above which the first decode is downsampled pre-emptively. */
        const val MAX_PIXELS = 48_000_000L
        const val MAX_SAMPLE_SIZE = 8

        /** Large enough that a multi-megabyte JPEG moves in a handful of writes. */
        const val IO_BUFFER_BYTES = 64 * 1024

        const val QUARTER_TURN = 90f
        const val HALF_TURN = 180f
        const val THREE_QUARTER_TURN = 270f
        const val FULL_TURN = 360

        const val MARGIN_RATIO = 0.028f
        const val PADDING_RATIO = 0.62f
        const val LINE_GAP_RATIO = 0.30f
        const val CORNER_RADIUS_RATIO = 0.45f
        const val TITLE_RATIO = 1.14f
        const val FOOTER_RATIO = 0.82f
        const val SHADOW_RADIUS_RATIO = 0.12f
        const val MIN_SHRINK_FACTOR = 0.45f
        const val MAP_CORNER_RATIO = 0.8f
        const val MAP_BORDER_RATIO = 0.25f
        const val MAP_BORDER_ALPHA = 90

        const val MAX_ALPHA = 255
        const val BODY_ALPHA = 240
        const val FOOTER_ALPHA = 190

        val COPIED_EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH
        )
    }
}

/** Line height used when stacking stamp rows. */
private fun Paint.lineHeight(): Float = -fontMetrics.top + fontMetrics.bottom
