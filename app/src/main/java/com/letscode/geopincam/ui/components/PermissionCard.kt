package com.letscode.geopincam.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Explains why a permission is needed and offers the one action that can fix it.
 *
 * Used for both the camera and location permissions; when a permission has been
 * permanently denied the button opens the system app settings instead of asking
 * again, because a second request would be a no-op.
 */
@Composable
fun PermissionCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.LocationOn,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(CONTENT_PADDING),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(ICON_SIZE)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TITLE_SPACING)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = TEXT_SPACING)
            )
            Button(
                onClick = onAction,
                modifier = Modifier
                    .padding(top = BUTTON_SPACING)
                    .fillMaxWidth(BUTTON_WIDTH_FRACTION)
            ) {
                Text(actionLabel)
            }
            if (secondaryActionLabel != null && onSecondaryAction != null) {
                TextButton(onClick = onSecondaryAction) {
                    Text(secondaryActionLabel)
                }
            }
        }
    }
}

private val CONTENT_PADDING = 32.dp
private val ICON_SIZE = 56.dp
private val TITLE_SPACING = 20.dp
private val TEXT_SPACING = 12.dp
private val BUTTON_SPACING = 28.dp
private const val BUTTON_WIDTH_FRACTION = 0.8f
