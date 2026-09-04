package com.letscode.geopincam.ui.theme

import androidx.compose.ui.graphics.Color

// GeoPinCam brand palette: a deep teal that reads as instrument-grade rather than
// playful, with a cool blue support colour for secondary surfaces.

val TealPrimaryLight = Color(0xFF00696E)
val TealOnPrimaryLight = Color(0xFFFFFFFF)
val TealContainerLight = Color(0xFF9CF1F7)
val TealOnContainerLight = Color(0xFF002022)

val SlateSecondaryLight = Color(0xFF4A6365)
val SlateOnSecondaryLight = Color(0xFFFFFFFF)
val SlateContainerLight = Color(0xFFCCE8EA)
val SlateOnContainerLight = Color(0xFF051F21)

val BlueTertiaryLight = Color(0xFF4B607C)
val BlueOnTertiaryLight = Color(0xFFFFFFFF)
val BlueContainerLight = Color(0xFFD3E4FF)
val BlueOnTertiaryContainerLight = Color(0xFF041C35)

val BackgroundLight = Color(0xFFFAFDFC)
val OnBackgroundLight = Color(0xFF191C1C)
val SurfaceVariantLight = Color(0xFFDAE4E5)
val OnSurfaceVariantLight = Color(0xFF3F4949)
val OutlineLight = Color(0xFF6F7979)

val TealPrimaryDark = Color(0xFF4CD9E0)
val TealOnPrimaryDark = Color(0xFF003739)
val TealContainerDark = Color(0xFF004F52)
val TealOnContainerDark = Color(0xFF9CF1F7)

val SlateSecondaryDark = Color(0xFFB0CCCE)
val SlateOnSecondaryDark = Color(0xFF1B3436)
val SlateContainerDark = Color(0xFF324B4D)
val SlateOnContainerDark = Color(0xFFCCE8EA)

val BlueTertiaryDark = Color(0xFFB3C8E8)
val BlueOnTertiaryDark = Color(0xFF1C314B)
val BlueContainerDark = Color(0xFF334863)
val BlueOnTertiaryContainerDark = Color(0xFFD3E4FF)

val BackgroundDark = Color(0xFF191C1C)
val OnBackgroundDark = Color(0xFFE0E3E3)
val SurfaceVariantDark = Color(0xFF3F4949)
val OnSurfaceVariantDark = Color(0xFFBEC8C9)
val OutlineDark = Color(0xFF899393)

/**
 * Colours for controls drawn over the camera preview.
 *
 * The preview is arbitrary imagery rather than a themed surface, so these stay
 * fixed in both themes and rely on scrims for contrast instead of the scheme.
 */
object CameraOverlayColors {
    val Scrim = Color(0xCC000000)
    val PanelScrim = Color(0x99000000)
    val OnOverlay = Color(0xFFFFFFFF)
    val OnOverlayMuted = Color(0xB3FFFFFF)
    val ShutterRing = Color(0xFFFFFFFF)

    /** Reserved for the record button and the live-recording marker. */
    val RecordAccent = Color(0xFFE53935)

    /** Text on the selected mode chip, which is a solid white pill. */
    val OnSelectedChip = Color(0xFF101314)
    val AccuracyGood = Color(0xFF4ADE80)
    val AccuracyFair = Color(0xFFFACC15)
    val AccuracyPoor = Color(0xFFFB923C)
    val AccuracyUnknown = Color(0xFFCBD5E1)
}
