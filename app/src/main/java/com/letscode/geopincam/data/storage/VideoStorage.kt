package com.letscode.geopincam.data.storage

import android.location.Location
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import com.letscode.geopincam.domain.model.LocationData
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Where a recording is written, and how it reaches the gallery once it is done.
 *
 * The two shapes exist because scoped storage only arrived in API 29: above it
 * CameraX writes straight into the media store entry, below it the recording
 * lands in a real directory that has to be registered afterwards.
 */
sealed interface VideoOutput {
    val displayName: String

    data class Gallery(
        override val displayName: String,
        val options: MediaStoreOutputOptions
    ) : VideoOutput

    data class Legacy(
        override val displayName: String,
        val file: File,
        val options: FileOutputOptions
    ) : VideoOutput
}

/**
 * Prepares recordings for the gallery.
 *
 * Nothing is re-encoded on the way out: the recorder writes the final MP4 once,
 * with the fix already attached as its location metadata, so stopping a
 * recording costs nothing beyond closing the file.
 */
class VideoStorage(private val mediaStore: MediaStoreGateway) {

    /** Reserves a name and a destination for a recording about to start. */
    fun outputFor(captureTimeMillis: Long, location: LocationData?): VideoOutput {
        val displayName = "$VIDEO_PREFIX${timestamp(captureTimeMillis)}$MP4_EXTENSION"
        val where = location?.toAndroidLocation()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val builder = MediaStoreOutputOptions.Builder(
                mediaStore.contentResolver,
                mediaStore.videoCollection()
            ).setContentValues(mediaStore.newVideoValues(displayName, captureTimeMillis))
            if (where != null) builder.setLocation(where)
            VideoOutput.Gallery(displayName, builder.build())
        } else {
            val file = mediaStore.legacyVideoFile(displayName)
            val builder = FileOutputOptions.Builder(file)
            if (where != null) builder.setLocation(where)
            VideoOutput.Legacy(displayName, file, builder.build())
        }
    }

    /**
     * @return the gallery URI of the finished recording, or null when it could
     *   not be published.
     */
    suspend fun publish(
        output: VideoOutput,
        recordedUri: Uri?,
        captureTimeMillis: Long
    ): Uri? = when (output) {
        // The recorder already cleared the pending flag on the entry it wrote.
        is VideoOutput.Gallery -> recordedUri?.takeIf { it != Uri.EMPTY }
        is VideoOutput.Legacy -> mediaStore.registerLegacyVideo(output.file, captureTimeMillis)
            .onFailure { Log.w(TAG, "Could not publish ${output.displayName}", it) }
            .getOrNull()
    }

    /** Removes whatever a failed recording left behind. */
    suspend fun discard(output: VideoOutput, recordedUri: Uri?) {
        when (output) {
            is VideoOutput.Gallery -> recordedUri
                ?.takeIf { it != Uri.EMPTY }
                ?.let { mediaStore.delete(it) }

            is VideoOutput.Legacy -> runCatching { output.file.delete() }
        }
    }

    /** Play services hands out fixes as plain data; the recorder wants a Location. */
    private fun LocationData.toAndroidLocation(): Location? {
        if (latitude.isNaN() || longitude.isNaN()) return null
        if (latitude !in MIN_LATITUDE..MAX_LATITUDE) return null
        if (longitude !in MIN_LONGITUDE..MAX_LONGITUDE) return null
        return Location(PROVIDER).also { target ->
            target.latitude = latitude
            target.longitude = longitude
            target.time = timestampMillis
            altitudeMeters?.let { target.altitude = it }
            accuracyMeters?.let { target.accuracy = it }
            speedMetersPerSecond?.let { target.speed = it }
            bearingDegrees?.let { target.bearing = it }
        }
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.US).format(Date(millis))

    private companion object {
        const val TAG = "VideoStorage"
        const val VIDEO_PREFIX = "GeoPinCam_"
        const val MP4_EXTENSION = ".mp4"
        const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss_SSS"
        const val PROVIDER = "gps"
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}
