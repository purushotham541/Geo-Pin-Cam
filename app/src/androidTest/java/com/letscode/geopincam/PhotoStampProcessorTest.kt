package com.letscode.geopincam

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.letscode.geopincam.data.image.PhotoStampProcessor
import com.letscode.geopincam.data.image.StampRenderSpec
import com.letscode.geopincam.domain.model.PhotoQuality
import com.letscode.geopincam.domain.model.StampPosition
import com.letscode.geopincam.domain.model.StampTextAlignment
import com.letscode.geopincam.domain.usecase.StampLine
import com.letscode.geopincam.domain.usecase.StampLineStyle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exercises the real stamping pipeline end to end on device: decode, draw, encode.
 */
@RunWith(AndroidJUnit4::class)
class PhotoStampProcessorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val processor = PhotoStampProcessor()
    private lateinit var workingDir: File

    @Before
    fun setUp() {
        workingDir = File(context.cacheDir, "stamp-test").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        workingDir.deleteRecursively()
    }

    private fun sourcePhoto(width: Int = 1200, height: Int = 1600): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val file = File(workingDir, "source.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun spec(position: StampPosition) = StampRenderSpec(
        lines = listOf(
            StampLine("📍 Hyderabad, Telangana, India", StampLineStyle.TITLE),
            StampLine("17.3850° N, 78.4867° E", StampLineStyle.BODY),
            StampLine("31 Aug 2026 • 02:30 PM", StampLineStyle.BODY),
            StampLine("Geo Pin Cam", StampLineStyle.FOOTER)
        ),
        position = position,
        alignment = StampTextAlignment.START,
        fontScale = 0.028f,
        backgroundOpacity = 0.55f
    )

    @Test
    fun stampingKeepsTheOriginalResolution() = runTest {
        val source = sourcePhoto()
        val target = File(workingDir, "stamped.jpg")

        val result = processor.process(
            source,
            target,
            spec(StampPosition.BOTTOM_LEFT),
            PhotoQuality.HIGH.jpegQuality
        )

        val processed = result.getOrThrow()
        assertEquals(1200, processed.width)
        assertEquals(1600, processed.height)
        assertTrue(target.exists())
        assertTrue(target.length() > 0)
    }

    @Test
    fun theStampIsDrawnWhereTheConfigurationAsksForIt() = runTest {
        val source = sourcePhoto()
        val bottom = File(workingDir, "bottom.jpg")
        val top = File(workingDir, "top.jpg")

        processor.process(
            source,
            bottom,
            spec(StampPosition.BOTTOM_LEFT),
            PhotoQuality.HIGH.jpegQuality
        ).getOrThrow()
        processor.process(source, top, spec(StampPosition.TOP_LEFT), PhotoQuality.HIGH.jpegQuality)
            .getOrThrow()

        val bottomPixels = BitmapFactory.decodeFile(bottom.absolutePath)
        val topPixels = BitmapFactory.decodeFile(top.absolutePath)

        // The source is pure white, so any darkened pixel is stamp pixels.
        assertTrue(bottomPixels.hasDarkPixelsIn(bottomBand = true))
        assertTrue(topPixels.hasDarkPixelsIn(bottomBand = false))
        assertNotEquals(
            bottomPixels.hasDarkPixelsIn(bottomBand = false),
            bottomPixels.hasDarkPixelsIn(bottomBand = true)
        )

        bottomPixels.recycle()
        topPixels.recycle()
    }

    @Test
    fun aNullSpecCopiesThePhotoThroughUnstamped() = runTest {
        val source = sourcePhoto(600, 600)
        val target = File(workingDir, "clean.jpg")

        processor.process(source, target, null, PhotoQuality.HIGH.jpegQuality).getOrThrow()

        val pixels = BitmapFactory.decodeFile(target.absolutePath)
        assertTrue(!pixels.hasDarkPixelsIn(bottomBand = true))
        assertTrue(!pixels.hasDarkPixelsIn(bottomBand = false))
        pixels.recycle()
    }

    @Test
    fun rotatingAQuarterTurnSwapsTheDimensions() = runTest {
        val source = sourcePhoto(width = 1200, height = 800)
        val target = File(workingDir, "rotated.jpg")

        val processed = processor.rotate(source, target, 90, PhotoQuality.HIGH.jpegQuality)
            .getOrThrow()

        assertEquals(800, processed.width)
        assertEquals(1200, processed.height)
    }

    @Test
    fun rotatingFullCircleRestoresTheOriginalShape() = runTest {
        val source = sourcePhoto(width = 1200, height = 800)
        val target = File(workingDir, "full-circle.jpg")

        val processed = processor.rotate(source, target, 360, PhotoQuality.HIGH.jpegQuality)
            .getOrThrow()

        assertEquals(1200, processed.width)
        assertEquals(800, processed.height)
    }

    @Test
    fun rotatingByANegativeAngleTurnsTheOtherWay() = runTest {
        val source = sourcePhoto(width = 1200, height = 800)
        val target = File(workingDir, "anticlockwise.jpg")

        val processed = processor.rotate(source, target, -90, PhotoQuality.HIGH.jpegQuality)
            .getOrThrow()

        assertEquals(800, processed.width)
        assertEquals(1200, processed.height)
    }

    @Test
    fun anUnreadableSourceFailsWithoutThrowing() = runTest {
        val broken = File(workingDir, "broken.jpg").apply { writeText("not an image") }
        val target = File(workingDir, "out.jpg")

        val result = processor.process(
            broken,
            target,
            spec(StampPosition.BOTTOM_LEFT),
            PhotoQuality.HIGH.jpegQuality
        )

        assertTrue(result.isFailure)
    }

    /** Samples the top or bottom fifth of the image for non-white pixels. */
    private fun Bitmap.hasDarkPixelsIn(bottomBand: Boolean): Boolean {
        val bandHeight = height / 5
        val startY = if (bottomBand) height - bandHeight else 0
        for (y in startY until startY + bandHeight step SAMPLE_STEP) {
            for (x in 0 until width step SAMPLE_STEP) {
                if (Color.red(getPixel(x, y)) < DARK_THRESHOLD) return true
            }
        }
        return false
    }

    private companion object {
        const val SAMPLE_STEP = 4
        const val DARK_THRESHOLD = 200
    }
}
