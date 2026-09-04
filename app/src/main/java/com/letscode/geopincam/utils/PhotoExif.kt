package com.letscode.geopincam.utils

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.letscode.geopincam.domain.model.LocationData
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Location and timing metadata read back from a photo's Exif block. */
data class ExifPhotoInfo(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val addressLine: String? = null,
    val dateTimeOriginalMillis: Long? = null
)

/**
 * Reads and writes the Exif tags the app cares about.
 *
 * Writing the fix into Exif means the coordinates survive independently of the
 * pixels, so the photo detail screen can show them even for files that were
 * stamped with the overlay turned off.
 */
object PhotoExif {

    private const val TAG = "PhotoExif"
    private const val EXIF_DATE_PATTERN = "yyyy:MM:dd HH:mm:ss"
    private const val SOFTWARE_NAME = "Geo Pin Cam"

    /**
     * Writes the fix, capture time and app signature into [file].
     *
     * [carriedTags] are camera tags copied over from the original capture and
     * [resetOrientation] declares the pixels already upright. Both ride along in
     * this one pass because saving Exif rewrites the whole JPEG, and doing that
     * twice for a multi-megabyte photo is time the user spends watching a spinner.
     */
    fun writeCaptureMetadata(
        file: File,
        location: LocationData?,
        addressLine: String?,
        captureTimeMillis: Long,
        carriedTags: Map<String, String> = emptyMap(),
        resetOrientation: Boolean = false,
        /**
         * When set, the clockwise turn CameraX reported for the frame, written as
         * the Exif orientation. Use for a raw capture buffer, which carries none.
         */
        rotationDegrees: Int? = null
    ) {
        try {
            val exif = ExifInterface(file)
            carriedTags.forEach { (tag, value) -> exif.setAttribute(tag, value) }
            if (resetOrientation) {
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
            } else if (rotationDegrees != null) {
                exif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    orientationTagFor(rotationDegrees).toString()
                )
            }
            val stamp = SimpleDateFormat(EXIF_DATE_PATTERN, Locale.US).format(Date(captureTimeMillis))
            exif.setAttribute(ExifInterface.TAG_DATETIME, stamp)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, stamp)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, stamp)
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, SOFTWARE_NAME)

            if (location != null) {
                exif.setLatLong(location.latitude, location.longitude)
                location.altitudeMeters?.let { exif.setAltitude(it) }
                location.accuracyMeters?.let {
                    exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, it.toRationalString())
                }
                exif.setAttribute(
                    ExifInterface.TAG_GPS_DATESTAMP,
                    SimpleDateFormat(GPS_DATE_PATTERN, Locale.US).format(Date(captureTimeMillis))
                )
            }
            addressLine?.takeIf { it.isNotBlank() }?.let {
                exif.setAttribute(ExifInterface.TAG_USER_COMMENT, it)
            }
            exif.saveAttributes()
        } catch (error: IOException) {
            // Metadata is a nice-to-have; a failure here must not lose the photo.
            Log.w(TAG, "Could not write Exif metadata to ${file.name}", error)
        }
    }

    fun read(stream: InputStream): ExifPhotoInfo = try {
        ExifInterface(stream).toInfo()
    } catch (error: IOException) {
        Log.w(TAG, "Could not read Exif metadata", error)
        ExifPhotoInfo()
    }

    private fun ExifInterface.toInfo(): ExifPhotoInfo {
        val coordinates = latLong
        val altitude = getAltitude(Double.NaN).takeIf { !it.isNaN() }
        return ExifPhotoInfo(
            latitude = coordinates?.getOrNull(0),
            longitude = coordinates?.getOrNull(1),
            altitudeMeters = altitude,
            accuracyMeters = getAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR)
                ?.parseRational(),
            addressLine = getAttribute(ExifInterface.TAG_USER_COMMENT)?.takeIf { it.isNotBlank() },
            dateTimeOriginalMillis = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?.let { parseExifDate(it) }
        )
    }

    private fun parseExifDate(value: String): Long? = try {
        SimpleDateFormat(EXIF_DATE_PATTERN, Locale.US).parse(value)?.time
    } catch (error: java.text.ParseException) {
        null
    }

    /** Exif rationals look like "17/2"; plain decimals are accepted too. */
    private fun String.parseRational(): Float? {
        val parts = split("/")
        return when {
            parts.size == 2 -> {
                val numerator = parts[0].toFloatOrNull()
                val denominator = parts[1].toFloatOrNull()
                if (numerator != null && denominator != null && denominator != 0f) {
                    numerator / denominator
                } else {
                    null
                }
            }
            else -> toFloatOrNull()
        }
    }

    /** The Exif orientation constant that stands for a clockwise turn of [degrees]. */
    private fun orientationTagFor(degrees: Int): Int = when (((degrees % 360) + 360) % 360) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    private fun Float.toRationalString(): String = "${(this * RATIONAL_SCALE).toInt()}/$RATIONAL_SCALE"

    private const val RATIONAL_SCALE = 100
    private const val GPS_DATE_PATTERN = "yyyy:MM:dd"
}
