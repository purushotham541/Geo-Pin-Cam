package com.letscode.geopincam.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.letscode.geopincam.R

/** A heading and its body text, as used by the privacy and licence screens. */
data class InfoParagraph(val heading: String?, val body: String)

/**
 * Explains what the app does with the camera, the location and the photos.
 *
 * The wording matches the implementation: coordinates go to the Android geocoder
 * when an address is looked up, and nothing else leaves the device.
 */
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InfoScreen(
        title = stringResource(R.string.privacy_title),
        paragraphs = listOf(
            InfoParagraph(
                stringResource(R.string.privacy_camera_heading),
                stringResource(R.string.privacy_camera_body)
            ),
            InfoParagraph(
                stringResource(R.string.privacy_microphone_heading),
                stringResource(R.string.privacy_microphone_body)
            ),
            InfoParagraph(
                stringResource(R.string.privacy_location_heading),
                stringResource(R.string.privacy_location_body)
            ),
            InfoParagraph(
                stringResource(R.string.privacy_storage_heading),
                stringResource(R.string.privacy_storage_body)
            ),
            InfoParagraph(
                stringResource(R.string.privacy_network_heading),
                stringResource(R.string.privacy_network_body)
            ),
            InfoParagraph(
                stringResource(R.string.privacy_analytics_heading),
                stringResource(R.string.privacy_analytics_body)
            )
        ),
        onBack = onBack,
        modifier = modifier
    )
}

/** Lists the open-source components the app is built from. */
@Composable
fun LicensesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    InfoScreen(
        title = stringResource(R.string.licenses_title),
        paragraphs = listOf(InfoParagraph(null, stringResource(R.string.licenses_body))),
        onBack = onBack,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScreen(
    title: String,
    paragraphs: List<InfoParagraph>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(CONTENT_PADDING)
        ) {
            paragraphs.forEach { paragraph ->
                paragraph.heading?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = HEADING_TOP_PADDING)
                    )
                }
                Text(
                    text = paragraph.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = BODY_TOP_PADDING)
                )
            }
        }
    }
}

private val CONTENT_PADDING = 16.dp
private val HEADING_TOP_PADDING = 20.dp
private val BODY_TOP_PADDING = 6.dp
