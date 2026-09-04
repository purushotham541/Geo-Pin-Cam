package com.letscode.geopincam.domain.model

import android.net.Uri

/**
 * A photograph or recording owned by the app, as listed from MediaStore.
 */
data class CapturedPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateTakenMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,

    /** True for a recording, which lives in the video collection, not the image one. */
    val isVideo: Boolean = false,

    /** Running time of a recording; always zero for a photograph. */
    val durationMillis: Long = 0L
) {
    val mimeType: String get() = if (isVideo) "video/mp4" else "image/jpeg"
}

/** Location and time details read back from a saved photo's Exif data. */
data class PhotoDetails(
    val photo: CapturedPhoto,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val addressLine: String? = null,
    val accuracyMeters: Float? = null
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
}
