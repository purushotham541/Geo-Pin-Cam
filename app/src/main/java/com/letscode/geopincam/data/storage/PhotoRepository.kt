package com.letscode.geopincam.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.letscode.geopincam.data.image.PhotoStampProcessor
import com.letscode.geopincam.data.image.StampRenderSpec
import com.letscode.geopincam.domain.model.CapturedPhoto
import com.letscode.geopincam.domain.model.LocationData
import com.letscode.geopincam.domain.model.PhotoDetails
import com.letscode.geopincam.domain.model.PhotoQuality
import com.letscode.geopincam.utils.PhotoExif
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A photograph that reached the media store. */
data class SavedPhoto(
    val uri: Uri,
    val displayName: String,
    val capturedAtMillis: Long,
    val isStamped: Boolean
)

/** Outcome of the capture pipeline. */
sealed interface CaptureSaveResult {
    data class Saved(val photo: SavedPhoto) : CaptureSaveResult

    /**
     * The photo was stamped but could not be added to the gallery. The file is
     * still on disk, so the UI can offer to share it instead of losing the shot.
     */
    data class SavedToCacheOnly(val file: File, val reason: Throwable) : CaptureSaveResult

    data class Failed(val reason: Throwable) : CaptureSaveResult
}

/**
 * Owns the path a photograph takes from the camera to the gallery: stamp it,
 * write its metadata, hand it to the media store, then clean up the temporary
 * files. Also serves the gallery and detail screens their data.
 */
class PhotoRepository(
    context: Context,
    private val mediaStore: MediaStoreGateway,
    private val processor: PhotoStampProcessor,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val appContext = context.applicationContext

    /** Temporary home for in-flight captures; also the FileProvider's shared path. */
    val captureCacheDir: File
        get() = File(appContext.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }

    /**
     * Stamps the in-memory capture [source], writes the location metadata and
     * stores the result.
     *
     * @param source the raw sensor JPEG, straight from the CameraX buffer.
     * @param rotationDegrees the clockwise turn CameraX reported for the frame.
     * @param spec null when the user turned the stamp off, in which case the photo
     *   is stored as captured.
     */
    suspend fun saveCapture(
        source: ByteArray,
        rotationDegrees: Int,
        spec: StampRenderSpec?,
        location: LocationData?,
        addressLine: String?,
        captureTimeMillis: Long,
        quality: PhotoQuality,
        saveStamped: Boolean,
        saveOriginal: Boolean
    ): CaptureSaveResult {
        val originalUri = if (saveOriginal) {
            saveOriginalCopy(source, rotationDegrees, location, addressLine, captureTimeMillis)
        } else {
            null
        }

        // The user can choose to keep only the untouched original.
        if (!saveStamped) {
            return if (originalUri != null) {
                CaptureSaveResult.Saved(
                    SavedPhoto(
                        uri = originalUri,
                        displayName = originalName(captureTimeMillis),
                        capturedAtMillis = captureTimeMillis,
                        isStamped = false
                    )
                )
            } else {
                CaptureSaveResult.Failed(IOException("The original copy could not be saved"))
            }
        }

        val stampedFile = File(
            captureCacheDir,
            "$STAMPED_PREFIX${timestamp(captureTimeMillis)}$JPEG_EXTENSION"
        )

        val processed = processor.process(source, rotationDegrees, stampedFile, spec, quality.jpegQuality)
            .getOrElse { error ->
                cleanUp(stampedFile)
                return CaptureSaveResult.Failed(error)
            }

        return try {
            withContext(ioDispatcher) {
                PhotoExif.writeCaptureMetadata(
                    file = stampedFile,
                    location = location,
                    addressLine = addressLine,
                    captureTimeMillis = captureTimeMillis,
                    carriedTags = processed.carriedExif,
                    resetOrientation = processed.orientationBaked
                )
            }

            val displayName = "$PHOTO_PREFIX${timestamp(captureTimeMillis)}$JPEG_EXTENSION"
            mediaStore.savePhoto(
                source = stampedFile,
                displayName = displayName,
                captureTimeMillis = captureTimeMillis,
                width = processed.width,
                height = processed.height
            ).fold(
                onSuccess = { uri ->
                    cleanUp(stampedFile)
                    CaptureSaveResult.Saved(
                        SavedPhoto(
                            uri = uri,
                            displayName = displayName,
                            capturedAtMillis = captureTimeMillis,
                            isStamped = spec != null && !spec.isEmpty
                        )
                    )
                },
                onFailure = { error ->
                    // Keep the stamped file so the shot is not lost.
                    CaptureSaveResult.SavedToCacheOnly(stampedFile, error)
                }
            )
        } catch (error: IOException) {
            cleanUp(stampedFile)
            CaptureSaveResult.Failed(error)
        }
    }

    /** Best effort: an unstamped copy is a convenience, never a reason to fail. */
    private suspend fun saveOriginalCopy(
        source: ByteArray,
        rotationDegrees: Int,
        location: LocationData?,
        addressLine: String?,
        captureTimeMillis: Long
    ): Uri? {
        val file = File(captureCacheDir, "$RAW_PREFIX${timestamp(captureTimeMillis)}$JPEG_EXTENSION")
        return try {
            withContext(ioDispatcher) {
                file.outputStream().use { it.write(source) }
                PhotoExif.writeCaptureMetadata(
                    file = file,
                    location = location,
                    addressLine = addressLine,
                    captureTimeMillis = captureTimeMillis,
                    rotationDegrees = rotationDegrees
                )
            }
            mediaStore.savePhoto(
                source = file,
                displayName = originalName(captureTimeMillis),
                captureTimeMillis = captureTimeMillis,
                width = 0,
                height = 0
            ).getOrNull()
        } catch (error: IOException) {
            Log.w(TAG, "Could not save the original copy", error)
            null
        } finally {
            cleanUp(file)
        }
    }

    private fun originalName(captureTimeMillis: Long) =
        "$ORIGINAL_PREFIX${timestamp(captureTimeMillis)}$JPEG_EXTENSION"

    suspend fun loadPhotos(): Result<List<CapturedPhoto>> = mediaStore.queryPhotos()

    /**
     * Photos and recordings in one list, newest first.
     *
     * A failure in either collection is not allowed to empty the gallery, so
     * whichever half did load is still returned.
     */
    suspend fun loadMedia(): Result<List<CapturedPhoto>> {
        val photos = mediaStore.queryPhotos()
        val videos = mediaStore.queryVideos()
        if (photos.isFailure && videos.isFailure) {
            return Result.failure(photos.exceptionOrNull() ?: IOException("Media could not be read"))
        }
        val combined = photos.getOrDefault(emptyList()) + videos.getOrDefault(emptyList())
        return Result.success(combined.sortedByDescending { it.dateTakenMillis })
    }

    suspend fun loadPhoto(id: Long): Result<CapturedPhoto?> = mediaStore.queryPhotoById(id)

    /** Reads the location details a photo carries in its Exif block. */
    suspend fun loadDetails(photo: CapturedPhoto): PhotoDetails = withContext(ioDispatcher) {
        val info = try {
            mediaStore.openInputStream(photo.uri)?.use { PhotoExif.read(it) }
        } catch (error: IOException) {
            Log.w(TAG, "Could not read details for ${photo.displayName}", error)
            null
        } catch (error: SecurityException) {
            Log.w(TAG, "Not allowed to read ${photo.displayName}", error)
            null
        }

        PhotoDetails(
            photo = photo,
            latitude = info?.latitude,
            longitude = info?.longitude,
            altitudeMeters = info?.altitudeMeters,
            addressLine = info?.addressLine,
            accuracyMeters = info?.accuracyMeters
        )
    }

    suspend fun delete(uri: Uri): DeleteOutcome = mediaStore.delete(uri)

    /**
     * Turns a stored photo by [degrees] clockwise and writes it back in place, so
     * the gallery entry and any shares keep the same identity.
     */
    suspend fun rotatePhoto(
        photo: CapturedPhoto,
        degrees: Int,
        quality: PhotoQuality = PhotoQuality.HIGH
    ): Result<Unit> {
        val now = System.currentTimeMillis()
        val incoming = File(captureCacheDir, "$ROTATE_PREFIX${timestamp(now)}$JPEG_EXTENSION")
        val rotated = File(captureCacheDir, "$ROTATE_PREFIX${timestamp(now)}_out$JPEG_EXTENSION")

        return try {
            withContext(ioDispatcher) {
                val input = mediaStore.openInputStream(photo.uri)
                    ?: throw IOException("Could not open ${photo.displayName}")
                input.use { source ->
                    incoming.outputStream().use { target -> source.copyTo(target) }
                }
            }

            val processed = processor.rotate(incoming, rotated, degrees, quality.jpegQuality)
                .getOrElse { error ->
                    cleanUp(incoming, rotated)
                    return Result.failure(error)
                }

            mediaStore.overwrite(photo.uri, processed.file, processed.width, processed.height)
                .also { cleanUp(incoming, rotated) }
        } catch (error: IOException) {
            cleanUp(incoming, rotated)
            Result.failure(error)
        } catch (error: SecurityException) {
            cleanUp(incoming, rotated)
            Result.failure(error)
        }
    }

    /**
     * Writes a second copy of an existing photo into the gallery, which is what
     * the detail screen's export action offers.
     */
    suspend fun exportCopy(photo: CapturedPhoto): Result<Uri> {
        val now = System.currentTimeMillis()
        val temporary = File(captureCacheDir, "$EXPORT_PREFIX${timestamp(now)}$JPEG_EXTENSION")
        return try {
            withContext(ioDispatcher) {
                val input = mediaStore.openInputStream(photo.uri)
                    ?: throw IOException("Could not open ${photo.displayName}")
                input.use { source ->
                    temporary.outputStream().use { target -> source.copyTo(target) }
                }
            }
            mediaStore.savePhoto(
                source = temporary,
                displayName = "$COPY_PREFIX${timestamp(now)}$JPEG_EXTENSION",
                captureTimeMillis = now,
                width = photo.width,
                height = photo.height
            ).also { cleanUp(temporary) }
        } catch (error: IOException) {
            cleanUp(temporary)
            Result.failure(error)
        } catch (error: SecurityException) {
            cleanUp(temporary)
            Result.failure(error)
        }
    }

    /** Drops leftover temporary captures, for example after a crash mid-capture. */
    suspend fun clearStaleCache() = withContext(ioDispatcher) {
        val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MILLIS
        captureCacheDir.listFiles()
            ?.filter { it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private suspend fun cleanUp(vararg files: File) = withContext(ioDispatcher) {
        files.forEach { file -> runCatching { file.delete() } }
    }

    private fun timestamp(millis: Long): String =
        SimpleDateFormat(FILE_TIMESTAMP_PATTERN, Locale.US).format(Date(millis))

    private companion object {
        const val TAG = "PhotoRepository"
        const val CACHE_DIR_NAME = "captures"
        const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss_SSS"
        const val PHOTO_PREFIX = "GeoPinCam_"
        const val STAMPED_PREFIX = "stamped_"
        const val ORIGINAL_PREFIX = "GeoPinCam_original_"
        const val RAW_PREFIX = "raw_"
        const val EXPORT_PREFIX = "export_"
        const val ROTATE_PREFIX = "rotate_"
        const val COPY_PREFIX = "GeoPinCam_copy_"
        const val JPEG_EXTENSION = ".jpg"
        const val CACHE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1000L
    }
}
