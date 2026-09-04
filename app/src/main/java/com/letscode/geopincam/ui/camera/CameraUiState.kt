package com.letscode.geopincam.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.StringRes
import com.letscode.geopincam.data.image.StampRenderSpec
import com.letscode.geopincam.domain.model.AppSettings
import com.letscode.geopincam.domain.model.CaptureMode
import com.letscode.geopincam.domain.model.LocationStatus
import com.letscode.geopincam.domain.usecase.StampLine
import com.letscode.geopincam.ui.UserMessage

/** Lifecycle of the camera itself, independent of permissions. */
sealed interface CameraState {
    data object Initializing : CameraState
    data object Ready : CameraState

    /** The shutter was pressed and the sensor is still working. */
    data object Capturing : CameraState

    data class Error(@param:StringRes val messageRes: Int) : CameraState
}

/** Where a recording is in its short lifecycle. */
enum class RecordingState {
    IDLE,

    /** The button was pressed; the recorder has not confirmed the start yet. */
    STARTING,
    ACTIVE,

    /** Stop was asked for and the file is being closed. */
    STOPPING
}

/** Runtime permission state as the UI needs to reason about it. */
enum class PermissionStatus {
    /** Not requested yet in this session. */
    Unknown,
    Granted,

    /** Denied once; asking again is still allowed and a rationale should be shown. */
    Denied,

    /** Denied for good; only the app settings screen can restore it. */
    PermanentlyDenied
}

data class CameraUiState(
    val cameraState: CameraState = CameraState.Initializing,
    val cameraPermission: PermissionStatus = PermissionStatus.Unknown,
    val locationPermission: PermissionStatus = PermissionStatus.Unknown,
    val onlyApproximateLocation: Boolean = false,
    val locationStatus: LocationStatus = LocationStatus.PermissionRequired,
    val settings: AppSettings = AppSettings(),
    val stampPreviewLines: List<StampLine> = emptyList(),

    /** The stamp the video overlay effect burns into a recording, kept current. */
    val videoStampSpec: StampRenderSpec? = null,
    val mapPreview: Bitmap? = null,
    val hasFlashUnit: Boolean = true,
    val lastPhotoUri: Uri? = null,
    val recordingState: RecordingState = RecordingState.IDLE,
    val recordedMillis: Long = 0L,

    /** False records silently, which is better than refusing to record at all. */
    val microphoneGranted: Boolean = false,

    /** Captures still being stamped and saved in the background. */
    val savesInFlight: Int = 0,
    val message: UserMessage? = null
) {
    val captureMode: CaptureMode get() = settings.camera.captureMode

    val isBusy: Boolean get() = cameraState is CameraState.Capturing

    val isRecording: Boolean
        get() = recordingState == RecordingState.ACTIVE ||
            recordingState == RecordingState.STOPPING

    /** A capture is still being written; the viewfinder stays usable throughout. */
    val isSaving: Boolean get() = savesInFlight > 0

    val canCapture: Boolean
        get() = cameraPermission == PermissionStatus.Granted &&
            cameraState is CameraState.Ready &&
            captureMode == CaptureMode.PHOTO

    val canRecord: Boolean
        get() = cameraPermission == PermissionStatus.Granted &&
            cameraState is CameraState.Ready &&
            captureMode == CaptureMode.VIDEO &&
            recordingState != RecordingState.STARTING &&
            recordingState != RecordingState.STOPPING

    /** Switching modes mid-recording would drop the file, so it waits. */
    val canSwitchMode: Boolean get() = recordingState == RecordingState.IDLE

    val showsStampPreview: Boolean
        get() = settings.stamp.stampEnabled && stampPreviewLines.isNotEmpty()
}
