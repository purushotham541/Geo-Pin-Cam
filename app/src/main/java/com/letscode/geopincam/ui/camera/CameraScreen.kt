package com.letscode.geopincam.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.util.concurrent.ListenableFuture
import com.letscode.geopincam.R
import com.letscode.geopincam.data.storage.VideoOutput
import com.letscode.geopincam.domain.model.CameraFacing
import com.letscode.geopincam.domain.model.CaptureMode
import com.letscode.geopincam.domain.model.FlashMode
import com.letscode.geopincam.domain.model.LocationStatus
import com.letscode.geopincam.domain.model.VideoQuality
import com.letscode.geopincam.ui.components.LocationStatusChip
import com.letscode.geopincam.ui.components.PermissionCard
import com.letscode.geopincam.ui.components.PhotoThumbnail
import com.letscode.geopincam.ui.components.StampPreviewOverlay
import com.letscode.geopincam.ui.theme.CameraOverlayColors
import com.letscode.geopincam.utils.DurationFormatter
import com.letscode.geopincam.utils.PermissionUtils
import com.letscode.geopincam.utils.findActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * The camera screen: live preview, GPS readout, live stamp preview and a shutter
 * that either takes a stamped photo or records a located video, depending on the
 * selected mode.
 *
 * A capture is saved straight to the gallery and the viewfinder stays put, so the
 * next shot never waits on the last one being reviewed. The snackbar and the
 * refreshed thumbnail are the only acknowledgement.
 */
@Composable
fun CameraScreen(
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = viewModel(factory = CameraViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val snackbarHostState = remember { SnackbarHostState() }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(
            granted = fine || coarse,
            precise = fine,
            canAskAgain = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ?: true
        )
    }

    val microphoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMicrophonePermissionResult(granted)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(
            granted = granted,
            canAskAgain = activity?.shouldShowRequestPermissionRationale(
                Manifest.permission.CAMERA
            ) ?: true
        )
        // Location is only worth asking about once the camera itself is usable.
        if (granted && !PermissionUtils.hasAnyLocationPermission(context)) {
            locationLauncher.launch(PermissionUtils.LOCATION_PERMISSIONS)
        }
    }

    // Permissions can also be granted in system settings, so re-read them every
    // time the screen comes back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onScreenResumed(
            cameraGranted = PermissionUtils.hasCameraPermission(context),
            locationGranted = PermissionUtils.hasAnyLocationPermission(context),
            preciseLocation = PermissionUtils.hasPreciseLocationPermission(context),
            microphoneGranted = PermissionUtils.hasMicrophonePermission(context)
        )
    }

    LaunchedEffect(Unit) {
        if (!PermissionUtils.hasCameraPermission(context)) {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        } else if (!PermissionUtils.hasAnyLocationPermission(context)) {
            locationLauncher.launch(PermissionUtils.LOCATION_PERMISSIONS)
        }
    }

    val message = state.message
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message?.id) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        when (state.cameraPermission) {
            PermissionStatus.Granted -> CameraContent(
                state = state,
                viewModel = viewModel,
                onOpenGallery = onOpenGallery,
                onOpenSettings = onOpenSettings,
                onRequestLocation = {
                    if (state.locationPermission == PermissionStatus.PermanentlyDenied) {
                        context.startActivity(PermissionUtils.appSettingsIntent(context))
                    } else {
                        locationLauncher.launch(PermissionUtils.LOCATION_PERMISSIONS)
                    }
                },
                onEnableLocationServices = {
                    context.startActivity(PermissionUtils.locationSettingsIntent())
                },
                onRequestMicrophone = {
                    if (!PermissionUtils.hasMicrophonePermission(context)) {
                        microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.padding(padding)
            )

            PermissionStatus.PermanentlyDenied -> PermissionCard(
                title = stringResource(R.string.camera_permission_title),
                message = stringResource(R.string.camera_permission_denied_permanently),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = { context.startActivity(PermissionUtils.appSettingsIntent(context)) },
                icon = Icons.Filled.LocationOn,
                modifier = Modifier.padding(padding)
            )

            else -> PermissionCard(
                title = stringResource(R.string.camera_permission_title),
                message = stringResource(R.string.camera_permission_rationale),
                actionLabel = stringResource(R.string.camera_permission_grant),
                onAction = { cameraLauncher.launch(Manifest.permission.CAMERA) },
                icon = Icons.Filled.LocationOn,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun CameraContent(
    state: CameraUiState,
    viewModel: CameraViewModel,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onEnableLocationServices: () -> Unit,
    onRequestMicrophone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    val controller = remember(context) {
        LifecycleCameraController(context).apply {
            // The app does its own post-processing, so the shortest possible
            // shutter lag beats the sensor's extra noise reduction pass.
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    // Held while a recording is in flight so the shutter press can stop it.
    var recording by remember { mutableStateOf<Recording?>(null) }

    // Burns the GPS stamp into recordings; released with the screen.
    val stampEffect = remember { VideoStampEffect() }
    DisposableEffect(Unit) {
        onDispose { stampEffect.release() }
    }
    LaunchedEffect(state.videoStampSpec) {
        stampEffect.spec = state.videoStampSpec
    }

    // Photo and video capture are bound one at a time: enabling both at once is
    // rejected on some devices, and the switch is quick enough to be unnoticed.
    LaunchedEffect(state.captureMode, state.settings.camera.defaultFacing) {
        val selector = when (state.settings.camera.defaultFacing) {
            CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        }
        val video = state.captureMode == CaptureMode.VIDEO
        controller.setEnabledUseCases(
            if (video) CameraController.VIDEO_CAPTURE else CameraController.IMAGE_CAPTURE
        )
        // The overlay only makes sense while the video stream is bound.
        if (video) {
            runCatching { controller.setEffects(setOf(stampEffect.effect)) }
        } else {
            runCatching { controller.clearEffects() }
        }
        try {
            controller.cameraSelector = selector
            controller.bindToLifecycle(lifecycleOwner)
            controller.initializationFuture.awaitCompletion(executor)
            viewModel.onCameraReady(controller.cameraInfo?.hasFlashUnit() == true)
        } catch (error: IllegalArgumentException) {
            viewModel.onCameraError()
        } catch (error: IllegalStateException) {
            viewModel.onCameraError()
        }
    }

    LaunchedEffect(state.settings.camera.videoQuality) {
        controller.videoCaptureQualitySelector =
            qualitySelectorFor(state.settings.camera.videoQuality)
    }

    LaunchedEffect(state.settings.camera.flashMode, state.cameraState) {
        val mode = state.settings.camera.flashMode
        controller.imageCaptureFlashMode = when (mode) {
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            // Torch lights the scene continuously, so the capture flash stays off.
            FlashMode.OFF, FlashMode.TORCH -> ImageCapture.FLASH_MODE_OFF
        }
        runCatching { controller.enableTorch(mode.isTorch) }
    }

    val onRecordToggle: () -> Unit = onRecordToggle@{
        val active = recording
        if (active != null) {
            viewModel.onRecordingStopRequested()
            active.stop()
            return@onRecordToggle
        }
        val output = viewModel.prepareRecording() ?: return@onRecordToggle
        recording = startRecording(
            controller = controller,
            output = output,
            withAudio = state.microphoneGranted,
            executor = executor
        ) { event ->
            when (event) {
                is VideoRecordEvent.Start -> viewModel.onRecordingStarted()
                is VideoRecordEvent.Status ->
                    viewModel.onRecordingProgress(event.recordingStats.recordedDurationNanos)

                is VideoRecordEvent.Finalize -> {
                    recording = null
                    viewModel.onRecordingFinalized(
                        output = output,
                        recordedUri = event.outputResults.outputUri.takeIf { it != Uri.EMPTY },
                        failed = event.hasError()
                    )
                }
            }
        }
        if (recording == null) viewModel.onRecordingFailedToStart()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    // Fill the display rather than letterboxing the sensor's 4:3 or
                    // 16:9 frame: the viewfinder is edge to edge, and a still or
                    // recording still captures the full frame behind the crop.
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    this.controller = controller
                    contentDescription = viewContext.getString(R.string.camera_preview_description)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        val cameraState = state.cameraState
        if (cameraState is CameraState.Error) {
            CameraErrorNotice(cameraState.messageRes)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(EDGE_PADDING)
        ) {
            TopControls(
                state = state,
                onRequestLocation = onRequestLocation,
                onEnableLocationServices = onEnableLocationServices,
                onToggleFlash = viewModel::onFlashModeToggled,
                onSwitchCamera = viewModel::onCameraFacingToggled
            )

            LocationNotice(
                state = state,
                onRequestLocation = onRequestLocation,
                onEnableLocationServices = onEnableLocationServices
            )

            Spacer(modifier = Modifier.weight(1f))

            if (state.isRecording || state.recordingState == RecordingState.STARTING) {
                RecordingIndicator(state.recordedMillis)
                Spacer(modifier = Modifier.height(CONTROL_SPACING))
            } else if (state.showsStampPreview) {
                StampPreviewOverlay(
                    lines = state.stampPreviewLines,
                    position = state.settings.stamp.position,
                    alignment = state.settings.stamp.textAlignment,
                    fontSize = state.settings.stamp.fontSize,
                    backgroundOpacity = state.settings.stamp.backgroundOpacity,
                    modifier = Modifier.padding(bottom = STAMP_BOTTOM_SPACING),
                    mapImage = state.mapPreview
                )
            }

            if (state.recordingState == RecordingState.IDLE) {
                CaptureModeToggle(
                    mode = state.captureMode,
                    enabled = state.canSwitchMode,
                    onSelect = { mode ->
                        if (mode == CaptureMode.VIDEO && !state.microphoneGranted) {
                            onRequestMicrophone()
                        }
                        viewModel.onCaptureModeSelected(mode)
                    },
                    modifier = Modifier.padding(bottom = CONTROL_SPACING)
                )
            }

            BottomControls(
                state = state,
                onOpenGallery = onOpenGallery,
                onOpenSettings = onOpenSettings,
                onShutter = { takePicture(controller, executor, viewModel) },
                onRecordToggle = onRecordToggle
            )
        }

        AnimatedVisibility(
            visible = cameraState is CameraState.Initializing,
            modifier = Modifier.align(Alignment.Center)
        ) {
            StartingIndicator()
        }
    }
}

@Composable
private fun TopControls(
    state: CameraUiState,
    onRequestLocation: () -> Unit,
    onEnableLocationServices: () -> Unit,
    onToggleFlash: () -> Unit,
    onSwitchCamera: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
    ) {
        LocationStatusChip(
            status = state.locationStatus,
            onRequestPermission = onRequestLocation,
            onEnableLocation = onEnableLocationServices,
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Switching the camera or the flash mid-recording would drop the file.
        if (!state.isRecording) {
            if (state.hasFlashUnit) {
                OverlayIconButton(
                    onClick = onToggleFlash,
                    contentDescription = stringResource(state.settings.camera.flashMode.labelRes())
                ) {
                    FlashIcon(state.settings.camera.flashMode)
                }
            }

            OverlayIconButton(
                onClick = onSwitchCamera,
                contentDescription = stringResource(
                    if (state.settings.camera.defaultFacing == CameraFacing.BACK) {
                        R.string.camera_switch_to_front
                    } else {
                        R.string.camera_switch_to_back
                    }
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera_switch),
                    contentDescription = null,
                    tint = CameraOverlayColors.OnOverlay
                )
            }
        }
    }
}

@Composable
private fun FlashIcon(mode: FlashMode) {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(
                if (mode == FlashMode.OFF) R.drawable.ic_flash_off else R.drawable.ic_flash_on
            ),
            contentDescription = null,
            tint = CameraOverlayColors.OnOverlay
        )
        if (mode == FlashMode.AUTO || mode == FlashMode.TORCH) {
            Text(
                text = stringResource(
                    if (mode == FlashMode.AUTO) {
                        R.string.camera_flash_auto_badge
                    } else {
                        R.string.camera_flash_torch_badge
                    }
                ),
                color = CameraOverlayColors.OnOverlay,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = BADGE_OFFSET)
            )
        }
    }
}

/** The Photo / Video selector, a pair of pills above the shutter. */
@Composable
private fun CaptureModeToggle(
    mode: CaptureMode,
    enabled: Boolean,
    onSelect: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(MODE_TOGGLE_CORNER))
            .background(CameraOverlayColors.PanelScrim)
            .padding(MODE_TOGGLE_PADDING),
        horizontalArrangement = Arrangement.spacedBy(MODE_TOGGLE_PADDING)
    ) {
        ModeChip(
            label = stringResource(R.string.camera_mode_photo),
            selected = mode == CaptureMode.PHOTO,
            enabled = enabled,
            contentDescription = stringResource(R.string.camera_switch_to_photo_mode),
            onClick = { onSelect(CaptureMode.PHOTO) }
        )
        ModeChip(
            label = stringResource(R.string.camera_mode_video),
            selected = mode == CaptureMode.VIDEO,
            enabled = enabled,
            contentDescription = stringResource(R.string.camera_switch_to_video_mode),
            onClick = { onSelect(CaptureMode.VIDEO) }
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    Text(
        text = label,
        color = if (selected) {
            CameraOverlayColors.OnSelectedChip
        } else {
            CameraOverlayColors.OnOverlay
        },
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(MODE_TOGGLE_CORNER))
            .background(
                if (selected) CameraOverlayColors.OnOverlay else Color.Transparent
            )
            .clickable(enabled = enabled && !selected, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = MODE_CHIP_PADDING_H, vertical = MODE_CHIP_PADDING_V)
    )
}

/** Red dot and running time shown while a recording is in progress. */
@Composable
private fun RecordingIndicator(recordedMillis: Long) {
    val time = DurationFormatter.clock(recordedMillis)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(INDICATOR_CORNER))
            .background(CameraOverlayColors.Scrim)
            .padding(horizontal = NOTICE_PADDING, vertical = RECORD_TIMER_PADDING_V),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING)
    ) {
        Box(
            modifier = Modifier
                .size(RECORD_DOT_SIZE)
                .clip(CircleShape)
                .background(CameraOverlayColors.RecordAccent)
        )
        Text(
            text = stringResource(R.string.camera_recording_time, time),
            color = CameraOverlayColors.OnOverlay,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun BottomControls(
    state: CameraUiState,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onShutter: () -> Unit,
    onRecordToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        GalleryButton(
            uri = state.lastPhotoUri,
            enabled = !state.isRecording,
            onClick = onOpenGallery
        )

        if (state.captureMode == CaptureMode.VIDEO) {
            RecordButton(
                recording = state.isRecording,
                enabled = state.canRecord,
                onClick = onRecordToggle
            )
        } else {
            ShutterButton(enabled = state.canCapture, onClick = onShutter)
        }

        OverlayIconButton(
            onClick = onOpenSettings,
            contentDescription = stringResource(R.string.camera_open_settings),
            size = SECONDARY_BUTTON_SIZE
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = CameraOverlayColors.OnOverlay
            )
        }
    }
}

@Composable
private fun GalleryButton(uri: Uri?, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.camera_open_gallery)
    Box(
        modifier = Modifier
            .size(SECONDARY_BUTTON_SIZE)
            .clip(RoundedCornerShape(THUMBNAIL_CORNER))
            .background(CameraOverlayColors.PanelScrim)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            // One node, whether the button shows a thumbnail or the fallback icon.
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            PhotoThumbnail(
                uri = uri,
                sizePx = THUMBNAIL_PIXELS,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_photo_library),
                contentDescription = null,
                tint = CameraOverlayColors.OnOverlay
            )
        }
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.camera_shutter)
    Box(
        modifier = Modifier
            .size(SHUTTER_SIZE)
            .clip(CircleShape)
            .background(CameraOverlayColors.OnOverlay.copy(alpha = SHUTTER_RING_ALPHA))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(SHUTTER_INNER_SIZE)
                .clip(CircleShape)
                .background(
                    if (enabled) {
                        CameraOverlayColors.ShutterRing
                    } else {
                        CameraOverlayColors.OnOverlayMuted
                    }
                )
        )
    }
}

/** The video counterpart of the shutter: a red disc that becomes a stop square. */
@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val description = stringResource(
        if (recording) R.string.camera_record_stop else R.string.camera_record_start
    )
    Box(
        modifier = Modifier
            .size(SHUTTER_SIZE)
            .clip(CircleShape)
            .background(CameraOverlayColors.OnOverlay.copy(alpha = SHUTTER_RING_ALPHA))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        val tint = if (enabled) {
            CameraOverlayColors.RecordAccent
        } else {
            CameraOverlayColors.OnOverlayMuted
        }
        if (recording) {
            Box(
                modifier = Modifier
                    .size(RECORD_STOP_SIZE)
                    .clip(RoundedCornerShape(RECORD_STOP_CORNER))
                    .background(tint)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(SHUTTER_INNER_SIZE)
                    .clip(CircleShape)
                    .background(tint)
            )
        }
    }
}

@Composable
private fun OverlayIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = CONTROL_BUTTON_SIZE,
    content: @Composable () -> Unit
) {
    val description = contentDescription
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(CameraOverlayColors.PanelScrim)
            // One labelled node per button keeps the screen reader concise.
            .semantics { this.contentDescription = description }
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * Explains a missing or degraded location permission in place, right where the
 * user notices the GPS readout is not what they expected.
 */
@Composable
private fun LocationNotice(
    state: CameraUiState,
    onRequestLocation: () -> Unit,
    onEnableLocationServices: () -> Unit
) {
    data class Notice(val text: String, val actionLabel: String?, val action: (() -> Unit)?)

    val notice = when {
        state.locationPermission == PermissionStatus.PermanentlyDenied -> Notice(
            stringResource(R.string.location_permission_denied_permanently),
            stringResource(R.string.action_open_settings),
            onRequestLocation
        )

        state.locationPermission == PermissionStatus.Denied -> Notice(
            stringResource(R.string.location_permission_rationale),
            stringResource(R.string.location_permission_grant),
            onRequestLocation
        )

        state.locationStatus == LocationStatus.ServicesDisabled -> Notice(
            stringResource(R.string.location_services_disabled),
            stringResource(R.string.location_enable_services),
            onEnableLocationServices
        )

        state.onlyApproximateLocation -> Notice(
            stringResource(R.string.location_permission_approximate),
            stringResource(R.string.location_permission_grant),
            onRequestLocation
        )

        else -> null
    } ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NOTICE_TOP_SPACING)
            .clip(RoundedCornerShape(INDICATOR_CORNER))
            .background(CameraOverlayColors.Scrim)
            .padding(NOTICE_PADDING)
    ) {
        Text(
            text = stringResource(R.string.location_permission_title),
            color = CameraOverlayColors.OnOverlay,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = notice.text,
            color = CameraOverlayColors.OnOverlayMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = BADGE_OFFSET)
        )
        if (notice.actionLabel != null && notice.action != null) {
            TextButton(onClick = notice.action) {
                Text(text = notice.actionLabel, color = CameraOverlayColors.OnOverlay)
            }
        }
    }
}

/** Shown while CameraX is still binding, so the screen is never blank and silent. */
@Composable
private fun StartingIndicator() {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(INDICATOR_CORNER))
            .background(CameraOverlayColors.Scrim)
            .padding(INDICATOR_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = CameraOverlayColors.OnOverlay)
        Spacer(modifier = Modifier.height(INDICATOR_SPACING))
        Text(
            text = stringResource(R.string.camera_starting),
            color = CameraOverlayColors.OnOverlay,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CameraErrorNotice(messageRes: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(messageRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(EDGE_PADDING)
        )
    }
}

private fun takePicture(
    controller: LifecycleCameraController,
    executor: Executor,
    viewModel: CameraViewModel
) {
    val pending = viewModel.prepareCapture() ?: return
    // The frame is taken in memory: keeping the sensor JPEG off the disk until it
    // is stamped saves a write and a read back before the photo reaches the
    // gallery, and the shutter re-arms the moment this callback returns.
    controller.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val rotationDegrees = image.imageInfo.rotationDegrees
                val bytes = try {
                    image.toJpegBytes()
                } finally {
                    image.close()
                }
                viewModel.onCaptureSucceeded(pending, bytes, rotationDegrees)
            }

            override fun onError(exception: ImageCaptureException) {
                viewModel.onCaptureFailed()
            }
        }
    )
}

/** Copies the single JPEG plane of [this] into a byte array. */
private fun ImageProxy.toJpegBytes(): ByteArray {
    val buffer = planes[0].buffer
    buffer.rewind()
    return ByteArray(buffer.remaining()).also { buffer.get(it) }
}

/**
 * Starts a recording into [output], returning the handle to stop it, or null when
 * the recorder refused to start.
 */
@SuppressLint("MissingPermission")
private fun startRecording(
    controller: LifecycleCameraController,
    output: VideoOutput,
    withAudio: Boolean,
    executor: Executor,
    listener: (VideoRecordEvent) -> Unit
): Recording? = try {
    val audioConfig = AudioConfig.create(withAudio)
    val consumer = Consumer<VideoRecordEvent> { event -> listener(event) }
    when (output) {
        is VideoOutput.Gallery ->
            controller.startRecording(output.options, audioConfig, executor, consumer)

        is VideoOutput.Legacy ->
            controller.startRecording(output.options, audioConfig, executor, consumer)
    }
} catch (error: RuntimeException) {
    // IllegalStateException (use case not ready), SecurityException (mic), etc.
    null
}

private fun qualitySelectorFor(quality: VideoQuality): QualitySelector {
    val target = when (quality) {
        VideoQuality.UHD -> Quality.UHD
        VideoQuality.FULL_HD -> Quality.FHD
        VideoQuality.HD -> Quality.HD
    }
    return QualitySelector.from(target, FallbackStrategy.lowerQualityOrHigherThan(target))
}

/** Suspends until a CameraX initialisation future completes. */
private suspend fun ListenableFuture<Void>.awaitCompletion(executor: Executor) {
    if (isDone) return
    suspendCancellableCoroutine { continuation ->
        addListener({ continuation.resume(Unit) }, executor)
    }
}

private fun FlashMode.labelRes(): Int = when (this) {
    FlashMode.OFF -> R.string.camera_flash_off
    FlashMode.ON -> R.string.camera_flash_on
    FlashMode.AUTO -> R.string.camera_flash_auto
    FlashMode.TORCH -> R.string.camera_flash_torch
}

private val EDGE_PADDING = 12.dp
private val CONTROL_SPACING = 8.dp
private val CONTROL_BUTTON_SIZE = 44.dp
private val SECONDARY_BUTTON_SIZE = 50.dp
private val SHUTTER_SIZE = 68.dp
private val SHUTTER_INNER_SIZE = 55.dp
private val RECORD_STOP_SIZE = 26.dp
private val RECORD_STOP_CORNER = 6.dp
private val RECORD_DOT_SIZE = 12.dp
private val THUMBNAIL_CORNER = 12.dp
private val STAMP_BOTTOM_SPACING = 10.dp
private val INDICATOR_CORNER = 16.dp
private val INDICATOR_PADDING = 24.dp
private val INDICATOR_SPACING = 12.dp
private val BADGE_OFFSET = 2.dp
private val NOTICE_TOP_SPACING = 8.dp
private val NOTICE_PADDING = 12.dp
private val RECORD_TIMER_PADDING_V = 8.dp
private val MODE_TOGGLE_CORNER = 16.dp
private val MODE_TOGGLE_PADDING = 3.dp
private val MODE_CHIP_PADDING_H = 14.dp
private val MODE_CHIP_PADDING_V = 4.dp
private const val SHUTTER_RING_ALPHA = 0.35f
private const val THUMBNAIL_PIXELS = 200
