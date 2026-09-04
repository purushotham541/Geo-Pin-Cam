package com.letscode.geopincam.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.letscode.geopincam.R
import com.letscode.geopincam.domain.model.AccuracyQuality
import com.letscode.geopincam.domain.model.LocationStatus
import com.letscode.geopincam.ui.theme.CameraOverlayColors
import com.letscode.geopincam.utils.CoordinateFormatter

/**
 * Compact GPS readout for the top of the camera screen.
 *
 * Every state is spelled out in words as well as colour, and the states the user
 * can act on (missing permission, location switched off) are tappable.
 */
@Composable
fun LocationStatusChip(
    status: LocationStatus,
    onRequestPermission: () -> Unit,
    onEnableLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = status.label()
    val action: (() -> Unit)? = when (status) {
        LocationStatus.PermissionRequired -> onRequestPermission
        LocationStatus.ServicesDisabled -> onEnableLocation
        else -> null
    }

    Row(
        modifier = modifier
            .background(CameraOverlayColors.PanelScrim, RoundedCornerShape(CHIP_CORNER))
            .let { base ->
                if (action != null) {
                    base.clickable(role = Role.Button, onClick = action)
                } else {
                    base
                }
            }
            .padding(horizontal = CHIP_PADDING_HORIZONTAL, vertical = CHIP_PADDING_VERTICAL),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DOT_SPACING)
    ) {
        if (status is LocationStatus.Searching) {
            CircularProgressIndicator(
                modifier = Modifier.size(INDICATOR_SIZE),
                strokeWidth = INDICATOR_STROKE,
                color = CameraOverlayColors.OnOverlay
            )
        } else {
            Box(
                modifier = Modifier
                    .size(DOT_SIZE)
                    .background(status.indicatorColor(), CircleShape)
            )
        }

        Text(
            text = label,
            color = CameraOverlayColors.OnOverlay,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LocationStatus.label(): String = when (this) {
    LocationStatus.PermissionRequired -> stringResource(R.string.location_permission_required)
    LocationStatus.ServicesDisabled -> stringResource(R.string.location_services_disabled)
    LocationStatus.Searching -> stringResource(R.string.location_searching)
    LocationStatus.Unavailable -> stringResource(R.string.location_unavailable)
    is LocationStatus.Available -> {
        val accuracy = location.accuracyMeters
        when {
            isStale -> stringResource(R.string.location_stale)
            accuracy != null -> stringResource(
                R.string.location_accuracy_short,
                CoordinateFormatter.formatMeters(accuracy.toDouble())
            )
            else -> stringResource(R.string.location_fix_ready)
        }
    }
}

private fun LocationStatus.indicatorColor(): Color = when (this) {
    is LocationStatus.Available -> if (isStale) {
        CameraOverlayColors.AccuracyUnknown
    } else {
        when (location.accuracyQuality()) {
            AccuracyQuality.GOOD -> CameraOverlayColors.AccuracyGood
            AccuracyQuality.FAIR -> CameraOverlayColors.AccuracyFair
            AccuracyQuality.POOR -> CameraOverlayColors.AccuracyPoor
            AccuracyQuality.UNKNOWN -> CameraOverlayColors.AccuracyUnknown
        }
    }

    else -> CameraOverlayColors.AccuracyPoor
}

private val CHIP_CORNER = 20.dp
private val CHIP_PADDING_HORIZONTAL = 12.dp
private val CHIP_PADDING_VERTICAL = 8.dp
private val DOT_SIZE = 8.dp
private val DOT_SPACING = 8.dp
private val INDICATOR_SIZE = 12.dp
private val INDICATOR_STROKE = 1.5.dp
