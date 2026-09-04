package com.letscode.geopincam.ui.gallery

import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letscode.geopincam.R
import com.letscode.geopincam.domain.model.CapturedPhoto
import com.letscode.geopincam.domain.model.DateFormatStyle
import com.letscode.geopincam.domain.model.TimeFormatStyle
import com.letscode.geopincam.ui.components.PhotoThumbnail
import com.letscode.geopincam.utils.DurationFormatter
import com.letscode.geopincam.utils.ShareUtils
import com.letscode.geopincam.utils.StampDateTimeFormatter

/** Grid of every photo taken with Geo Pin Cam. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val photoShareTitle = stringResource(R.string.photo_share_title)
    val videoShareTitle = stringResource(R.string.video_share_title)

    // A photo may have been deleted on the detail screen while this list was in
    // the back stack, so re-read the media store on every resume.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(state.deleteConsentRequest) {
        state.deleteConsentRequest?.let { sender ->
            consentLauncher.launch(sender.toRequest())
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gallery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading -> CenteredContent {
                    CircularProgressIndicator()
                }

                state.loadFailed -> CenteredMessage(
                    title = stringResource(R.string.gallery_load_failed),
                    message = null,
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = viewModel::refresh
                )

                state.isEmpty -> CenteredMessage(
                    title = stringResource(R.string.gallery_empty_title),
                    message = stringResource(R.string.gallery_empty_message)
                )

                else -> PhotoGrid(
                    photos = state.photos,
                    onOpen = { photo ->
                        if (photo.isVideo) {
                            if (!ShareUtils.playVideo(context, photo.uri)) {
                                viewModel.onVideoPlayFailed()
                            }
                        } else {
                            onOpenPhoto(photo.id)
                        }
                    },
                    onLongPress = viewModel::onPhotoLongPressed
                )
            }
        }
    }

    val selected = state.selectedPhoto
    if (selected != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = viewModel::onSelectionDismissed,
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                SheetAction(
                    icon = Icons.Filled.Share,
                    label = stringResource(R.string.action_share)
                ) {
                    val shared = ShareUtils.sharePhoto(
                        context = context,
                        uri = selected.uri,
                        chooserTitle = if (selected.isVideo) videoShareTitle else photoShareTitle,
                        mimeType = selected.mimeType
                    )
                    if (!shared) viewModel.onShareFailed()
                    viewModel.onSelectionDismissed()
                }
                SheetAction(
                    icon = Icons.Filled.Delete,
                    label = stringResource(R.string.action_delete)
                ) {
                    viewModel.deletePhoto(selected.uri)
                }
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<CapturedPhoto>,
    onOpen: (CapturedPhoto) -> Unit,
    onLongPress: (CapturedPhoto) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CELL_MIN_SIZE),
        contentPadding = PaddingValues(GRID_PADDING),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items = photos, key = { it.id }) { photo ->
            val takenAt = StampDateTimeFormatter.formatDateTime(
                photo.dateTakenMillis,
                DateFormatStyle.DAY_MONTH_YEAR,
                TimeFormatStyle.HOUR_12,
                ", "
            )
            val duration = DurationFormatter.clock(photo.durationMillis)
            val description = if (photo.isVideo) {
                stringResource(R.string.gallery_video_description, takenAt, duration)
            } else {
                stringResource(R.string.gallery_photo_description, takenAt)
            }
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .combinedClickable(
                        onClick = { onOpen(photo) },
                        onLongClick = { onLongPress(photo) }
                    )
            ) {
                PhotoThumbnail(
                    uri = photo.uri,
                    sizePx = THUMBNAIL_PIXELS,
                    isVideo = photo.isVideo,
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize()
                )
                if (photo.isVideo) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.gallery_play_video),
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(VIDEO_BADGE_SIZE)
                            .clip(MaterialTheme.shapes.small)
                            .background(VIDEO_BADGE_SCRIM)
                    )
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(DURATION_PADDING)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(VIDEO_BADGE_SCRIM)
                            .padding(horizontal = DURATION_TEXT_PADDING_H, vertical = DURATION_TEXT_PADDING_V)
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SHEET_PADDING)
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Text(
            text = label,
            modifier = Modifier
                .padding(start = SHEET_ICON_SPACING)
                .weight(1f),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    message: String?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    CenteredContent {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(MESSAGE_PADDING)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = MESSAGE_SPACING)
                )
            }
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

private fun IntentSender.toRequest(): IntentSenderRequest =
    IntentSenderRequest.Builder(this).build()

private val CELL_MIN_SIZE = 110.dp
private val GRID_PADDING = 4.dp
private val GRID_SPACING = 4.dp
private val VIDEO_BADGE_SIZE = 36.dp
private val DURATION_PADDING = 4.dp
private val DURATION_TEXT_PADDING_H = 4.dp
private val DURATION_TEXT_PADDING_V = 1.dp
private val VIDEO_BADGE_SCRIM = Color(0x66000000)
private val SHEET_PADDING = 12.dp
private val SHEET_ICON_SPACING = 16.dp
private val MESSAGE_PADDING = 32.dp
private val MESSAGE_SPACING = 8.dp
private const val THUMBNAIL_PIXELS = 300
