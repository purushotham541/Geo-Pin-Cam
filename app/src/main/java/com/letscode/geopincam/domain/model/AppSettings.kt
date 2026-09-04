package com.letscode.geopincam.domain.model

/**
 * JPEG encoding quality presets offered in Settings.
 *
 * The top preset stops at 90 rather than 95+: past this point the gain is
 * visually indistinguishable but the encode runs noticeably slower and the file
 * carries a third again as many bytes through the Exif pass and the copy into the
 * gallery, all of which the user waits on before the stamped photo appears.
 */
enum class PhotoQuality(val key: String, val jpegQuality: Int) {
    HIGH("high", 90),
    MEDIUM("medium", 85),
    LOW("low", 75);

    companion object {
        fun fromKey(key: String?): PhotoQuality =
            entries.firstOrNull { it.key == key } ?: HIGH
    }
}

enum class FlashMode(val key: String) {
    OFF("off"),
    AUTO("auto"),
    ON("on"),

    /** The lamp stays lit, so it also lights the preview, not just the shot. */
    TORCH("torch");

    /** True when the lamp should be held on rather than fired at capture time. */
    val isTorch: Boolean get() = this == TORCH

    /** Cycles the camera screen's flash button through every mode. */
    fun next(): FlashMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String?): FlashMode = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/** Which of the two things the shutter button does. */
enum class CaptureMode(val key: String) {
    PHOTO("photo"),
    VIDEO("video");

    fun toggled(): CaptureMode = if (this == PHOTO) VIDEO else PHOTO

    companion object {
        fun fromKey(key: String?): CaptureMode = entries.firstOrNull { it.key == key } ?: PHOTO
    }
}

/**
 * Recording resolution presets offered in Settings.
 *
 * A device that cannot record the chosen size falls back to the nearest one it
 * does support, so every entry here is safe to select on every phone.
 */
enum class VideoQuality(val key: String) {
    UHD("uhd"),
    FULL_HD("full_hd"),
    HD("hd");

    companion object {
        fun fromKey(key: String?): VideoQuality = entries.firstOrNull { it.key == key } ?: FULL_HD
    }
}

enum class CameraFacing(val key: String) {
    BACK("back"),
    FRONT("front");

    fun toggled(): CameraFacing = if (this == BACK) FRONT else BACK

    companion object {
        fun fromKey(key: String?): CameraFacing = entries.firstOrNull { it.key == key } ?: BACK
    }
}

/** How addresses are rendered in the live preview and on the stamp. */
enum class AddressDetail(val key: String) {
    /** "Hyderabad, Telangana, India" */
    SHORT("short"),

    /** The full address line supplied by the geocoder. */
    FULL("full");

    companion object {
        fun fromKey(key: String?): AddressDetail = entries.firstOrNull { it.key == key } ?: SHORT
    }
}

/** Camera behaviour preferences. */
data class CameraSettings(
    val photoQuality: PhotoQuality = PhotoQuality.HIGH,
    val videoQuality: VideoQuality = VideoQuality.FULL_HD,
    val flashMode: FlashMode = FlashMode.OFF,
    val defaultFacing: CameraFacing = CameraFacing.BACK,

    /** Remembered so the camera opens in whichever mode was used last. */
    val captureMode: CaptureMode = CaptureMode.PHOTO
)

/** What gets written to storage after a capture. */
data class StorageSettings(
    val saveStampedPhoto: Boolean = true,
    val saveOriginalPhoto: Boolean = false
) {
    /** The stamped copy is the product of the app, so it can never be fully disabled. */
    val effectiveSaveStamped: Boolean get() = saveStampedPhoto || !saveOriginalPhoto
}

/** Root settings object exposed to the UI as a single stream. */
data class AppSettings(
    val stamp: StampConfiguration = StampConfiguration(),
    val camera: CameraSettings = CameraSettings(),
    val storage: StorageSettings = StorageSettings(),
    val addressDetail: AddressDetail = AddressDetail.SHORT
)
