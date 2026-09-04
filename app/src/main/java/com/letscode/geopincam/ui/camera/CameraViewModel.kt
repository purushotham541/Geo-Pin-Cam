package com.letscode.geopincam.ui.camera

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.letscode.geopincam.GeoPinCamApp
import com.letscode.geopincam.R
import com.letscode.geopincam.data.image.StampRenderSpec
import com.letscode.geopincam.data.location.LocationRepository
import com.letscode.geopincam.data.map.MapTileProvider
import com.letscode.geopincam.data.preferences.SettingsRepository
import com.letscode.geopincam.data.storage.CaptureSaveResult
import com.letscode.geopincam.data.storage.PhotoRepository
import com.letscode.geopincam.data.storage.VideoOutput
import com.letscode.geopincam.data.storage.VideoStorage
import com.letscode.geopincam.domain.model.AppSettings
import com.letscode.geopincam.domain.model.CameraFacing
import com.letscode.geopincam.domain.model.CaptureMode
import com.letscode.geopincam.domain.model.FlashMode
import com.letscode.geopincam.domain.model.LocationData
import com.letscode.geopincam.domain.model.LocationStatus
import com.letscode.geopincam.domain.usecase.StampLabels
import com.letscode.geopincam.domain.usecase.StampTextBuilder
import com.letscode.geopincam.ui.UserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A shot the sensor is about to hand back, and the moment its stamp is dated from. */
data class PendingCapture(val startedAtMillis: Long)

/**
 * Drives the camera screen: permissions, the live location readout, the stamp
 * preview and the capture pipeline for both photos and video.
 *
 * Holds no Android UI references. The screen owns the CameraX controller and
 * reports results back here.
 */
class CameraViewModel(
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
    private val photoRepository: PhotoRepository,
    private val videoStorage: VideoStorage,
    private val mapTileProvider: MapTileProvider,
    private val captureScope: CoroutineScope,
    stampLabels: StampLabels
) : ViewModel() {

    private val stampTextBuilder = StampTextBuilder(stampLabels)

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    /** Bumped whenever the location permission changes, to restart the fix stream. */
    private val locationEpoch = MutableStateFlow(0)

    /** Map cell the preview thumbnail was last fetched for, to avoid refetching. */
    private var lastMapCell: String? = null

    /** When the current recording began, used to date its gallery entry. */
    private var recordingStartedAtMillis = 0L

    init {
        observeSettings()
        observeLocation()
        startPreviewClock()
        refreshLastCapture()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                refreshStampPreview()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLocation() {
        viewModelScope.launch {
            locationEpoch
                .flatMapLatest { locationRepository.observeStatus() }
                .collect { status ->
                    _uiState.update { it.copy(locationStatus = status) }
                    refreshStampPreview()
                    refreshMapPreview(status.locationOrNull)
                }
        }
    }

    /** Keeps the previewed clock and the staleness marker honest while idle. */
    private fun startPreviewClock() {
        viewModelScope.launch {
            while (isActive) {
                delay(PREVIEW_TICK_MILLIS)
                refreshStampPreview()
            }
        }
    }

    private fun refreshLastCapture() {
        viewModelScope.launch {
            photoRepository.loadMedia().onSuccess { media ->
                _uiState.update { it.copy(lastPhotoUri = media.firstOrNull()?.uri) }
            }
        }
    }

    /**
     * Downloads the map thumbnail once per map cell, so panning around a room does
     * not hit the tile servers repeatedly.
     */
    private fun refreshMapPreview(location: LocationData?) {
        if (!_uiState.value.settings.stamp.showMapThumbnail || location == null) {
            if (_uiState.value.mapPreview != null) {
                _uiState.update { it.copy(mapPreview = null) }
            }
            return
        }
        val cell = mapCell(location)
        if (cell == lastMapCell) return
        lastMapCell = cell

        viewModelScope.launch {
            val map = mapTileProvider.mapFor(location.latitude, location.longitude)
            if (map != null && mapCell(location) == lastMapCell) {
                _uiState.update { it.copy(mapPreview = map) }
            }
        }
    }

    private fun mapCell(location: LocationData): String {
        val latitude = Math.round(location.latitude * MAP_CELL_FACTOR) / MAP_CELL_FACTOR
        val longitude = Math.round(location.longitude * MAP_CELL_FACTOR) / MAP_CELL_FACTOR
        return "$latitude,$longitude"
    }

    private fun refreshStampPreview() {
        val state = _uiState.value
        val status = state.locationStatus
        val stamp = state.settings.stamp
        val lines = stampTextBuilder.build(
            location = status.locationOrNull,
            captureTimeMillis = System.currentTimeMillis(),
            config = stamp,
            addressDetail = state.settings.addressDetail,
            isLocationStale = (status as? LocationStatus.Available)?.isStale == true
        )
        // The same lines the photo would carry, handed to the video overlay effect
        // so a recording gets the stamp burned in just like a still does. No map
        // tile here: its bitmap is shared and could be recycled mid-frame.
        val videoSpec = if (stamp.stampEnabled && lines.isNotEmpty()) {
            StampRenderSpec(
                lines = lines,
                position = stamp.position,
                alignment = stamp.textAlignment,
                fontScale = stamp.fontSize.scale,
                backgroundOpacity = stamp.backgroundOpacity,
                mapImage = null
            )
        } else {
            null
        }
        _uiState.update { it.copy(stampPreviewLines = lines, videoStampSpec = videoSpec) }
    }

    fun onCameraPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        _uiState.update {
            it.copy(
                cameraPermission = when {
                    granted -> PermissionStatus.Granted
                    canAskAgain -> PermissionStatus.Denied
                    else -> PermissionStatus.PermanentlyDenied
                },
                cameraState = if (granted) it.cameraState else CameraState.Initializing
            )
        }
    }

    fun onLocationPermissionResult(granted: Boolean, precise: Boolean, canAskAgain: Boolean) {
        _uiState.update {
            it.copy(
                locationPermission = when {
                    granted -> PermissionStatus.Granted
                    canAskAgain -> PermissionStatus.Denied
                    else -> PermissionStatus.PermanentlyDenied
                },
                onlyApproximateLocation = granted && !precise
            )
        }
        locationEpoch.update { it + 1 }
    }

    /** Sound is optional: a denied microphone records silent video, not no video. */
    fun onMicrophonePermissionResult(granted: Boolean) {
        _uiState.update { it.copy(microphoneGranted = granted) }
    }

    fun onCameraReady(hasFlashUnit: Boolean) {
        _uiState.update {
            it.copy(
                hasFlashUnit = hasFlashUnit,
                cameraState = if (it.isBusy) it.cameraState else CameraState.Ready
            )
        }
    }

    fun onCameraError() {
        _uiState.update { it.copy(cameraState = CameraState.Error(R.string.camera_unavailable)) }
    }

    fun onFlashModeToggled() {
        val next = _uiState.value.settings.camera.flashMode.next()
        viewModelScope.launch { settingsRepository.setFlashMode(next) }
    }

    fun onCameraFacingToggled() {
        val next = _uiState.value.settings.camera.defaultFacing.toggled()
        viewModelScope.launch { settingsRepository.setDefaultFacing(next) }
    }

    fun onCaptureModeSelected(mode: CaptureMode) {
        if (!_uiState.value.canSwitchMode || mode == _uiState.value.captureMode) return
        viewModelScope.launch { settingsRepository.setCaptureMode(mode) }
    }

    /**
     * Marks a capture as under way.
     *
     * @return the token to hand back with the frame, or null when a capture is
     *   already running or the camera is not ready.
     */
    fun prepareCapture(): PendingCapture? {
        if (!_uiState.value.canCapture) return null
        val startedAt = System.currentTimeMillis()
        _uiState.update { it.copy(cameraState = CameraState.Capturing) }
        return PendingCapture(startedAt)
    }

    /**
     * Stamps and stores the frame the sensor just handed back.
     *
     * The shutter is re-armed here rather than when the save finishes: the sensor
     * is already free, and stamping a multi-megapixel JPEG is work the user has no
     * reason to stand and watch. Saves run on an application scoped job so leaving
     * the screen mid-save still lands the photo in the gallery.
     *
     * @param jpegBytes the raw capture, straight from the CameraX buffer.
     * @param rotationDegrees the clockwise turn CameraX reports for the frame.
     */
    fun onCaptureSucceeded(pending: PendingCapture, jpegBytes: ByteArray, rotationDegrees: Int) {
        _uiState.update {
            it.copy(cameraState = CameraState.Ready, savesInFlight = it.savesInFlight + 1)
        }
        val settings = _uiState.value.settings
        captureScope.launch {
            val result = runCatching { savePhoto(pending, jpegBytes, rotationDegrees, settings) }
                .getOrElse { error -> CaptureSaveResult.Failed(error) }
            _uiState.update {
                it.copy(savesInFlight = (it.savesInFlight - 1).coerceAtLeast(0))
            }
            handleSaveResult(result)
        }
    }

    private suspend fun savePhoto(
        pending: PendingCapture,
        jpegBytes: ByteArray,
        rotationDegrees: Int,
        settings: AppSettings
    ): CaptureSaveResult {
        val location = locationRepository.fixForCapture()
        val captureTime = pending.startedAtMillis.takeIf { it > 0L }
            ?: System.currentTimeMillis()

        val lines = stampTextBuilder.build(
            location = location,
            captureTimeMillis = captureTime,
            config = settings.stamp,
            addressDetail = settings.addressDetail,
            isLocationStale = location?.isStale(System.currentTimeMillis()) == true
        )
        val mapImage = mapForCapture(settings.stamp.showMapThumbnail, location)
        val spec = if (settings.stamp.stampEnabled && lines.isNotEmpty()) {
            StampRenderSpec(
                lines = lines,
                position = settings.stamp.position,
                alignment = settings.stamp.textAlignment,
                fontScale = settings.stamp.fontSize.scale,
                backgroundOpacity = settings.stamp.backgroundOpacity,
                mapImage = mapImage
            )
        } else {
            null
        }

        return photoRepository.saveCapture(
            source = jpegBytes,
            rotationDegrees = rotationDegrees,
            spec = spec,
            location = location,
            addressLine = location?.address?.displayLine,
            captureTimeMillis = captureTime,
            quality = settings.camera.photoQuality,
            saveStamped = settings.storage.effectiveSaveStamped,
            saveOriginal = settings.storage.saveOriginalPhoto
        )
    }

    /**
     * Only the tile the live preview already fetched for this spot. Downloading one
     * here would put the tile servers between the shutter and the saved photo, and
     * a map is a decoration the shot can do without.
     */
    private fun mapForCapture(enabled: Boolean, location: LocationData?): Bitmap? {
        if (!enabled || location == null) return null
        return mapTileProvider.cachedMapFor(location.latitude, location.longitude)
    }

    private fun handleSaveResult(result: CaptureSaveResult) {
        when (result) {
            // A successful capture is silent: the refreshed thumbnail is the only
            // acknowledgement, so the viewfinder is never interrupted.
            is CaptureSaveResult.Saved -> {
                _uiState.update { it.copy(lastPhotoUri = result.photo.uri) }
            }

            is CaptureSaveResult.SavedToCacheOnly -> {
                _uiState.update { it.copy(message = UserMessage(R.string.capture_save_failed)) }
            }

            is CaptureSaveResult.Failed -> {
                _uiState.update {
                    it.copy(message = UserMessage(R.string.capture_processing_failed))
                }
            }
        }
    }

    fun onCaptureFailed() {
        _uiState.update {
            it.copy(
                cameraState = CameraState.Ready,
                message = UserMessage(R.string.capture_failed)
            )
        }
    }

    /**
     * Reserves the destination for a recording that is about to start.
     *
     * The fix used is whatever the live readout already has: a recording must
     * start on the press, never after a round trip to the satellites.
     */
    fun prepareRecording(): VideoOutput? {
        if (!_uiState.value.canRecord) return null
        val startedAt = System.currentTimeMillis()
        recordingStartedAtMillis = startedAt
        _uiState.update {
            it.copy(recordingState = RecordingState.STARTING, recordedMillis = 0L)
        }
        return videoStorage.outputFor(startedAt, _uiState.value.locationStatus.locationOrNull)
    }

    fun onRecordingStarted() {
        _uiState.update { it.copy(recordingState = RecordingState.ACTIVE) }
    }

    fun onRecordingProgress(recordedNanos: Long) {
        _uiState.update { it.copy(recordedMillis = recordedNanos / NANOS_PER_MILLISECOND) }
    }

    fun onRecordingStopRequested() {
        if (_uiState.value.recordingState != RecordingState.ACTIVE) return
        _uiState.update { it.copy(recordingState = RecordingState.STOPPING) }
    }

    /** Publishes the finished recording, or clears up after one that failed. */
    fun onRecordingFinalized(output: VideoOutput, recordedUri: Uri?, failed: Boolean) {
        val startedAt = recordingStartedAtMillis
        _uiState.update { it.copy(recordingState = RecordingState.IDLE, recordedMillis = 0L) }
        captureScope.launch {
            if (failed) {
                videoStorage.discard(output, recordedUri)
                _uiState.update { it.copy(message = UserMessage(R.string.video_failed)) }
                return@launch
            }
            val published = videoStorage.publish(output, recordedUri, startedAt)
            _uiState.update {
                it.copy(
                    lastPhotoUri = published ?: it.lastPhotoUri,
                    message = UserMessage(
                        if (published != null) R.string.video_saved else R.string.video_save_failed
                    )
                )
            }
        }
    }

    /** The recorder refused to start, so nothing was written to clean up. */
    fun onRecordingFailedToStart() {
        _uiState.update {
            it.copy(
                recordingState = RecordingState.IDLE,
                recordedMillis = 0L,
                message = UserMessage(R.string.video_failed)
            )
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** Re-reads permission driven state when the screen comes back to the foreground. */
    fun onScreenResumed(
        cameraGranted: Boolean,
        locationGranted: Boolean,
        preciseLocation: Boolean,
        microphoneGranted: Boolean
    ) {
        val previousLocation = _uiState.value.locationPermission
        _uiState.update {
            it.copy(
                cameraPermission = if (cameraGranted) {
                    PermissionStatus.Granted
                } else {
                    it.cameraPermission
                },
                locationPermission = if (locationGranted) {
                    PermissionStatus.Granted
                } else {
                    it.locationPermission
                },
                onlyApproximateLocation = locationGranted && !preciseLocation,
                microphoneGranted = microphoneGranted
            )
        }
        if (locationGranted && previousLocation != PermissionStatus.Granted) {
            locationEpoch.update { it + 1 }
        }
        refreshLastCapture()
    }

    companion object {
        private const val PREVIEW_TICK_MILLIS = 1_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L

        /** Three decimals, about 110 m, matching the address cache granularity. */
        private const val MAP_CELL_FACTOR = 1000.0

        /** Builds the localised stamp labels and wires up the repositories. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as GeoPinCamApp
                CameraViewModel(
                    settingsRepository = app.container.settingsRepository,
                    locationRepository = app.container.locationRepository,
                    photoRepository = app.container.photoRepository,
                    videoStorage = app.container.videoStorage,
                    mapTileProvider = app.container.mapTileProvider,
                    captureScope = app.container.captureScope,
                    stampLabels = app.stampLabels()
                )
            }
        }
    }
}

/** Reads the stamp label strings out of resources. */
fun Application.stampLabels(): StampLabels = StampLabels(
    appName = getString(R.string.app_name),
    locationPin = getString(R.string.stamp_pin),
    altitudeFormat = getString(R.string.stamp_altitude),
    accuracyFormat = getString(R.string.stamp_accuracy),
    speedFormat = getString(R.string.stamp_speed),
    bearingFormat = getString(R.string.stamp_bearing),
    dateTimeSeparator = getString(R.string.stamp_datetime_separator),
    coordinateLabelFormat = getString(R.string.stamp_coordinates_labelled),
    addressUnavailable = getString(R.string.location_address_unavailable),
    locationUnavailable = getString(R.string.location_unavailable),
    staleLocationNote = getString(R.string.location_stale)
)
