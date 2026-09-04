package com.letscode.geopincam.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Builds and launches share intents.
 *
 * Only content:// URIs ever leave the app: media store items are shared directly,
 * and files that are still in the cache are wrapped by the FileProvider declared
 * in the manifest.
 */
object ShareUtils {

    private const val TAG = "ShareUtils"
    private const val IMAGE_MIME_TYPE = "image/jpeg"
    private const val VIDEO_MIME_TYPE = "video/mp4"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /** @return false when no app on the device can handle the share. */
    fun sharePhoto(
        context: Context,
        uri: Uri,
        chooserTitle: String,
        mimeType: String = IMAGE_MIME_TYPE
    ): Boolean = launch(context, uri, chooserTitle, mimeType)

    /** Shares a file that has not reached the media store yet. */
    fun shareCachedFile(context: Context, file: File, chooserTitle: String): Boolean = try {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + AUTHORITY_SUFFIX,
            file
        )
        launch(context, uri, chooserTitle, IMAGE_MIME_TYPE)
    } catch (error: IllegalArgumentException) {
        Log.e(TAG, "File is outside the paths the provider exposes", error)
        false
    }

    /**
     * Opens [uri] in whichever installed app plays video.
     *
     * @return false when the device has no video player.
     */
    fun playVideo(context: Context, uri: Uri): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, VIDEO_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(view)
            true
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "No app available to play the video", error)
            false
        }
    }

    private fun launch(
        context: Context,
        uri: Uri,
        chooserTitle: String,
        mimeType: String
    ): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(chooser)
            true
        } catch (error: ActivityNotFoundException) {
            Log.w(TAG, "No app available to share the photo", error)
            false
        }
    }
}
