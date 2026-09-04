package com.letscode.geopincam.data.storage

import android.annotation.SuppressLint
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.letscode.geopincam.domain.model.CapturedPhoto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Result of asking the media store to remove a photo. */
sealed interface DeleteOutcome {
    data object Deleted : DeleteOutcome

    /** Android wants the user to confirm; launch this sender and retry. */
    data class NeedsUserConsent(val intentSender: IntentSender) : DeleteOutcome

    data class Failed(val error: Throwable) : DeleteOutcome
}

/**
 * All reads and writes against the shared media collection.
 *
 * Photos live in Pictures/Geo Pin Cam so they show up in the system gallery.
 * On API 29 and above this uses scoped storage with a relative path and the
 * pending flag; on API 28 and below it writes the file itself and registers it,
 * which is why WRITE_EXTERNAL_STORAGE is declared with maxSdkVersion 28.
 */
class MediaStoreGateway(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    /**
     * CameraX writes recordings into the media store itself, so it needs the
     * resolver rather than a finished file to copy.
     */
    val contentResolver: ContentResolver get() = resolver

    /**
     * Copies [source] into the media collection under [displayName].
     *
     * @return the content:// URI of the stored photo.
     */
    suspend fun savePhoto(
        source: File,
        displayName: String,
        captureTimeMillis: Long,
        width: Int,
        height: Int
    ): Result<Uri> = withContext(ioDispatcher) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScoped(source, displayName, captureTimeMillis, width, height)
            } else {
                saveLegacy(source, displayName, captureTimeMillis)
            }
        } catch (error: IOException) {
            Log.e(TAG, "Failed to save $displayName", error)
            Result.failure(error)
        } catch (error: SecurityException) {
            Log.e(TAG, "Not allowed to save $displayName", error)
            Result.failure(error)
        }
    }

    // VOLUME_EXTERNAL_PRIMARY is a compile-time constant, so the inlined value is
    // harmless on older releases; this method only ever runs on API 29 and above.
    @SuppressLint("InlinedApi")
    private fun saveScoped(
        source: File,
        displayName: String,
        captureTimeMillis: Long,
        width: Int,
        height: Int
    ): Result<Uri> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = baseValues(displayName, captureTimeMillis).apply {
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Images.Media.WIDTH, width)
            put(MediaStore.Images.Media.HEIGHT, height)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(collection, values)
            ?: return Result.failure(IOException("Media store rejected the new photo"))

        return try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, IO_BUFFER_BYTES) }
            } ?: throw IOException("Could not open the media store entry for writing")

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
            Result.success(uri)
        } catch (error: IOException) {
            // Leave no half-written entry behind in the user's gallery.
            runCatching { resolver.delete(uri, null, null) }
            Result.failure(error)
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        source: File,
        displayName: String,
        captureTimeMillis: Long
    ): Result<Uri> {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            FOLDER_NAME
        )
        if (!directory.exists() && !directory.mkdirs()) {
            return Result.failure(IOException("Could not create $FOLDER_NAME"))
        }
        val target = File(directory, displayName)
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, IO_BUFFER_BYTES) }
        }

        val values = baseValues(displayName, captureTimeMillis).apply {
            put(MediaStore.Images.Media.DATA, target.absolutePath)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return Result.failure(IOException("Media store rejected the new photo"))

        // Makes the file visible to gallery apps that watch the media scanner.
        MediaScannerConnection.scanFile(
            appContext,
            arrayOf(target.absolutePath),
            arrayOf(MIME_TYPE),
            null
        )
        return Result.success(uri)
    }

    private fun baseValues(displayName: String, captureTimeMillis: Long) = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE)
        put(MediaStore.Images.Media.DATE_TAKEN, captureTimeMillis)
        put(MediaStore.Images.Media.DATE_ADDED, captureTimeMillis / MILLIS_PER_SECOND)
        put(MediaStore.Images.Media.DATE_MODIFIED, captureTimeMillis / MILLIS_PER_SECOND)
    }

    /** The collection recordings are listed from and written to. */
    @SuppressLint("InlinedApi")
    fun videoCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    /** The row CameraX should create for a recording it is about to write. */
    @SuppressLint("InlinedApi")
    fun newVideoValues(displayName: String, captureTimeMillis: Long): ContentValues =
        ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME_TYPE)
            put(MediaStore.Video.Media.DATE_TAKEN, captureTimeMillis)
            put(MediaStore.Video.Media.DATE_ADDED, captureTimeMillis / MILLIS_PER_SECOND)
            put(MediaStore.Video.Media.DATE_MODIFIED, captureTimeMillis / MILLIS_PER_SECOND)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_RELATIVE_PATH)
            }
        }

    /**
     * Where a recording is written on API 28 and below, which has no relative
     * path column and so needs a real directory on shared storage.
     */
    fun legacyVideoFile(displayName: String): File {
        val directory = File(
            @Suppress("DEPRECATION")
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            FOLDER_NAME
        )
        directory.mkdirs()
        return File(directory, displayName)
    }

    /** Publishes a recording written straight to disk, so gallery apps list it. */
    @Suppress("DEPRECATION")
    suspend fun registerLegacyVideo(
        file: File,
        captureTimeMillis: Long
    ): Result<Uri> = withContext(ioDispatcher) {
        try {
            val values = newVideoValues(file.name, captureTimeMillis).apply {
                put(MediaStore.Video.Media.DATA, file.absolutePath)
                put(MediaStore.Video.Media.SIZE, file.length())
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext Result.failure(
                    IOException("Media store rejected the recording")
                )
            MediaScannerConnection.scanFile(
                appContext,
                arrayOf(file.absolutePath),
                arrayOf(VIDEO_MIME_TYPE),
                null
            )
            Result.success(uri)
        } catch (error: SecurityException) {
            Log.e(TAG, "Not allowed to register ${file.name}", error)
            Result.failure(error)
        }
    }

    /** Lists the photos this app created, newest first. */
    suspend fun queryPhotos(): Result<List<CapturedPhoto>> = runQuery(video = false)

    /** Lists the recordings this app created, newest first. */
    suspend fun queryVideos(): Result<List<CapturedPhoto>> = runQuery(video = true)

    /** Looks up a single photo by its media store id. */
    suspend fun queryPhotoById(id: Long): Result<CapturedPhoto?> =
        runQuery(
            video = false,
            extraSelection = "${MediaStore.Images.Media._ID} = ?",
            extraArguments = arrayOf(id.toString())
        ).map { it.firstOrNull() }

    private suspend fun runQuery(
        video: Boolean,
        extraSelection: String? = null,
        extraArguments: Array<String>? = null
    ): Result<List<CapturedPhoto>> = withContext(ioDispatcher) {
        try {
            val collection = if (video) {
                videoCollection()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            // The column names are shared between the two collections; only a
            // recording carries a running time.
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            ) + if (video) arrayOf(MediaStore.Video.Media.DURATION) else emptyArray()
            val folderSelection: String
            val folderArguments: Array<String>
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                folderSelection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                folderArguments = arrayOf("%$FOLDER_NAME%")
            } else {
                @Suppress("DEPRECATION")
                folderSelection = "${MediaStore.Images.Media.DATA} LIKE ?"
                folderArguments = arrayOf("%/$FOLDER_NAME/%")
            }
            val selection = if (extraSelection == null) {
                folderSelection
            } else {
                "$folderSelection AND $extraSelection"
            }
            val arguments = folderArguments + extraArguments.orEmpty()
            val order = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val photos = mutableListOf<CapturedPhoto>()
            resolver.query(collection, projection, selection, arguments, order)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val takenColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val durationColumn = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val taken = if (takenColumn >= 0 && !cursor.isNull(takenColumn)) {
                        cursor.getLong(takenColumn)
                    } else {
                        cursor.getLong(addedColumn) * MILLIS_PER_SECOND
                    }
                    photos += CapturedPhoto(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameColumn).orEmpty(),
                        dateTakenMillis = taken,
                        sizeBytes = cursor.getLong(sizeColumn),
                        width = cursor.intOrZero(widthColumn),
                        height = cursor.intOrZero(heightColumn),
                        isVideo = video,
                        durationMillis = cursor.longOrZero(durationColumn)
                    )
                }
            }
            Result.success(photos)
        } catch (error: SecurityException) {
            Log.e(TAG, "Not allowed to read the media store", error)
            Result.failure(error)
        } catch (error: IllegalArgumentException) {
            Log.e(TAG, "Media store query rejected", error)
            Result.failure(error)
        }
    }

    /** Removes one photo, asking for user consent when the platform requires it. */
    suspend fun delete(uri: Uri): DeleteOutcome = withContext(ioDispatcher) {
        try {
            val removed = resolver.delete(uri, null, null)
            if (removed > 0) DeleteOutcome.Deleted else DeleteOutcome.Failed(
                FileNotFoundException("Photo no longer exists")
            )
        } catch (error: SecurityException) {
            val sender = recoverableIntentSender(error)
            if (sender != null) {
                DeleteOutcome.NeedsUserConsent(sender)
            } else {
                Log.e(TAG, "Not allowed to delete $uri", error)
                DeleteOutcome.Failed(error)
            }
        }
    }

    private fun recoverableIntentSender(error: SecurityException): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            error is RecoverableSecurityException
        ) {
            error.userAction.actionIntent.intentSender
        } else {
            null
        }

    fun openInputStream(uri: Uri) = resolver.openInputStream(uri)

    /**
     * Replaces the bytes of an existing entry, keeping its id and its place in the
     * user's gallery. Used when a photo is rotated in place.
     */
    suspend fun overwrite(
        uri: Uri,
        source: File,
        width: Int,
        height: Int
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            // "wt" truncates first, so a smaller result cannot leave a stale tail.
            resolver.openOutputStream(uri, "wt")?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: return@withContext Result.failure(IOException("Could not open $uri for writing"))

            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.WIDTH, width)
                    put(MediaStore.Images.Media.HEIGHT, height)
                    put(
                        MediaStore.Images.Media.DATE_MODIFIED,
                        System.currentTimeMillis() / MILLIS_PER_SECOND
                    )
                },
                null,
                null
            )
            Result.success(Unit)
        } catch (error: IOException) {
            Log.e(TAG, "Could not overwrite $uri", error)
            Result.failure(error)
        } catch (error: SecurityException) {
            Log.e(TAG, "Not allowed to overwrite $uri", error)
            Result.failure(error)
        }
    }

    private companion object {
        const val TAG = "MediaStoreGateway"
        const val FOLDER_NAME = "Geo Pin Cam"
        val RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/$FOLDER_NAME"
        const val MIME_TYPE = "image/jpeg"
        const val VIDEO_MIME_TYPE = "video/mp4"
        const val IO_BUFFER_BYTES = 64 * 1024

        val VIDEO_RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/$FOLDER_NAME"
        const val MILLIS_PER_SECOND = 1000L
    }
}

/** Reads a column that a collection may not have, or may have left null. */
private fun android.database.Cursor.intOrZero(column: Int): Int =
    if (column >= 0 && !isNull(column)) getInt(column) else 0

private fun android.database.Cursor.longOrZero(column: Int): Long =
    if (column >= 0 && !isNull(column)) getLong(column) else 0L
