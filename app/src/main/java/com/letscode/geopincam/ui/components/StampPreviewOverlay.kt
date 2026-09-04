package com.letscode.geopincam.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.letscode.geopincam.domain.model.StampFontSize
import com.letscode.geopincam.domain.model.StampPosition
import com.letscode.geopincam.domain.model.StampTextAlignment
import com.letscode.geopincam.domain.usecase.StampLine
import com.letscode.geopincam.domain.usecase.StampLineStyle
import com.letscode.geopincam.ui.theme.CameraOverlayColors

/**
 * On-screen approximation of the stamp that will be burned into the photo.
 *
 * Shares its text with the renderer through [StampLine], so what the user reads
 * here is exactly what gets drawn; only the sizing is adapted to screen density.
 */
@Composable
fun StampPreviewOverlay(
    lines: List<StampLine>,
    position: StampPosition,
    alignment: StampTextAlignment,
    fontSize: StampFontSize,
    backgroundOpacity: Float,
    modifier: Modifier = Modifier,
    mapImage: Bitmap? = null
) {
    if (lines.isEmpty()) return

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = position.toAlignment()
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = backgroundOpacity),
                    shape = RoundedCornerShape(CORNER_RADIUS)
                )
                .padding(horizontal = PANEL_PADDING_HORIZONTAL, vertical = PANEL_PADDING_VERTICAL)
                // The same information is announced by the location status row.
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.Top
        ) {
            if (mapImage != null) {
                Image(
                    bitmap = mapImage.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(PREVIEW_MAP_SIZE)
                        .clip(RoundedCornerShape(MAP_CORNER_RADIUS))
                )
                Spacer(modifier = Modifier.width(MAP_SPACING))
            }

            Column(
                horizontalAlignment = alignment.toHorizontalAlignment(),
                modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH)
            ) {
                lines.forEach { line ->
                    val baseSize = fontSize.previewSp()
                    Text(
                        text = line.text,
                        color = line.style.color(),
                        fontSize = line.style.scale(baseSize),
                        fontWeight = if (line.style == StampLineStyle.TITLE) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        lineHeight = line.style.scale(baseSize) * LINE_HEIGHT_FACTOR,
                        textAlign = alignment.toTextAlign(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = LocalTextStyle.current
                    )
                }
            }
        }
    }
}

private fun StampPosition.toAlignment(): Alignment = when (this) {
    StampPosition.TOP_LEFT -> Alignment.TopStart
    StampPosition.TOP_CENTER -> Alignment.TopCenter
    StampPosition.TOP_RIGHT -> Alignment.TopEnd
    StampPosition.BOTTOM_LEFT -> Alignment.BottomStart
    StampPosition.BOTTOM_CENTER -> Alignment.BottomCenter
    StampPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
}

private fun StampTextAlignment.toHorizontalAlignment(): Alignment.Horizontal = when (this) {
    StampTextAlignment.START -> Alignment.Start
    StampTextAlignment.CENTER -> Alignment.CenterHorizontally
    StampTextAlignment.END -> Alignment.End
}

private fun StampTextAlignment.toTextAlign(): TextAlign = when (this) {
    StampTextAlignment.START -> TextAlign.Start
    StampTextAlignment.CENTER -> TextAlign.Center
    StampTextAlignment.END -> TextAlign.End
}

/**
 * Approximates the burned-in stamp on the viewfinder, sized so the preview reads
 * at a glance and stays roughly in proportion with the stamp on the saved photo.
 */
private fun StampFontSize.previewSp() = when (this) {
    StampFontSize.SMALL -> 11.sp
    StampFontSize.MEDIUM -> 13.sp
    StampFontSize.LARGE -> 15.sp
    StampFontSize.EXTRA_LARGE -> 17.sp
}

private fun StampLineStyle.scale(base: androidx.compose.ui.unit.TextUnit) = when (this) {
    StampLineStyle.TITLE -> base * TITLE_SCALE
    StampLineStyle.BODY -> base
    StampLineStyle.FOOTER -> base * FOOTER_SCALE
}

private fun StampLineStyle.color(): Color = when (this) {
    StampLineStyle.FOOTER -> CameraOverlayColors.OnOverlayMuted
    else -> CameraOverlayColors.OnOverlay
}

private const val TITLE_SCALE = 1.14f
private const val FOOTER_SCALE = 0.85f
private const val LINE_HEIGHT_FACTOR = 1.2f
private val CORNER_RADIUS = 10.dp
private val MAP_CORNER_RADIUS = 8.dp

/** Small and fixed, so the panel stays out of the way of the shot. */
private val PREVIEW_MAP_SIZE = 46.dp

/** The text wraps past this rather than pushing the panel off the viewfinder. */
private val TEXT_MAX_WIDTH = 230.dp
private val MAP_SPACING = 7.dp
private val PANEL_PADDING_HORIZONTAL = 9.dp
private val PANEL_PADDING_VERTICAL = 7.dp
