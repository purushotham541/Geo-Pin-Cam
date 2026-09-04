package com.letscode.geopincam.ui.photo

import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.letscode.geopincam.R
import com.letscode.geopincam.domain.model.PhotoDetails
import com.letscode.geopincam.ui.components.PhotoPreview
import com.letscode.geopincam.utils.PhotoImageLoader
import com.letscode.geopincam.utils.ShareUtils

/**
 * Full screen view of one photograph.
 *
 * The location, coordinates, altitude, accuracy and time are already burned into
 * the image by the stamp, so there is no text panel here repeating them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoDetailViewModel = viewModel(factory = PhotoDetailViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val shareTitle = stringResource(R.string.photo_share_title)

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteConsentResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(state.deleteConsentRequest) {
        state.deleteConsentRequest?.let { consentLauncher.launch(it.toRequest()) }
    }

    // A rotated photo keeps its URI, so the cached thumbnail would otherwise still
    // show the old orientation in the gallery.
    LaunchedEffect(state.imageRevision) {
        if (state.imageRevision > 0) PhotoImageLoader.clearCache()
    }

    LaunchedEffect(state.isDeleted, state.notFound) {
        if (state.isDeleted || state.notFound) onBack()
    }

    val message = state.message
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message?.id) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    val photo = state.details?.photo
    val actionsEnabled = photo != null && !state.isRotating

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // The photo is the content; the four edit actions need the room.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                // Edit actions live up here; the bottom bar is for sending the photo on.
                actions = {
                    IconButton(
                        onClick = { viewModel.onRotate(-QUARTER_TURN) },
                        enabled = actionsEnabled
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_rotate_left),
                            contentDescription = stringResource(R.string.photo_rotate_left)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.onRotate(QUARTER_TURN) },
                        enabled = actionsEnabled
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_rotate_right),
                            contentDescription = stringResource(R.string.photo_rotate_right)
                        )
                    }
                    IconButton(
                        onClick = viewModel::onExportCopy,
                        enabled = actionsEnabled
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_save_copy),
                            contentDescription = stringResource(R.string.photo_save_copy)
                        )
                    }
                    IconButton(
                        onClick = viewModel::onDeleteRequested,
                        enabled = actionsEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            )
        },
        bottomBar = {
            PhotoShareBar(
                enabled = actionsEnabled,
                onShare = {
                    if (photo != null && !ShareUtils.sharePhoto(context, photo.uri, shareTitle)) {
                        viewModel.onShareFailed()
                    }
                }
            )
        }
    ) { padding ->
        val details = state.details
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            details != null -> DetailContent(
                details = details,
                imageRevision = state.imageRevision,
                modifier = Modifier.padding(padding)
            )
        }
    }

    if (state.isRotating) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                tonalElevation = ROTATING_ELEVATION
            ) {
                Row(
                    modifier = Modifier.padding(CARD_PADDING),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(PROGRESS_SIZE))
                    Text(
                        text = stringResource(R.string.photo_rotating),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = ROW_SPACING)
                    )
                }
            }
        }
    }

    if (state.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismissed,
            title = { Text(stringResource(R.string.photo_delete_title)) },
            text = { Text(stringResource(R.string.photo_delete_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDeleteConfirmed) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismissed) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DetailContent(
    details: PhotoDetails,
    imageRevision: Int,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(SECTION_SPACING),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = constraints.maxWidth
        val heightPx = if (constraints.hasBoundedHeight) {
            constraints.maxHeight
        } else {
            widthPx
        }
        // ContentScale.Fit letterboxes the photo within the free space, so every
        // aspect ratio shows whole without a scroll.
        PhotoPreview(
            uri = details.photo.uri,
            maxWidthPx = widthPx,
            maxHeightPx = heightPx,
            contentDescription = stringResource(R.string.capture_preview_description),
            modifier = Modifier.fillMaxSize(),
            revision = imageRevision
        )
    }
}

/** The one thing that belongs at thumb height: sending the photo on. */
@Composable
private fun PhotoShareBar(
    enabled: Boolean,
    onShare: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = ACTION_BAR_PADDING, vertical = ACTION_BAR_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onShare,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Filled.Share, contentDescription = null)
                Text(
                    text = stringResource(R.string.action_share),
                    modifier = Modifier.padding(start = SHARE_ICON_SPACING)
                )
            }
        }
    }
}

private fun IntentSender.toRequest(): IntentSenderRequest =
    IntentSenderRequest.Builder(this).build()

private const val QUARTER_TURN = 90
private val SECTION_SPACING = 8.dp
private val CARD_PADDING = 16.dp
private val ROW_SPACING = 12.dp
private val ACTION_BAR_PADDING = 8.dp
private val PROGRESS_SIZE = 24.dp
private val ROTATING_ELEVATION = 6.dp
private val SHARE_ICON_SPACING = 8.dp
