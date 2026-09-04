package com.letscode.geopincam.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.letscode.geopincam.R
import kotlinx.coroutines.delay

/**
 * The branded launch screen: the full artwork fills the display for a short beat
 * after the system splash, then hands over to the camera. A tap skips it so it
 * never gets in the way of someone who just wants to shoot.
 *
 * `ContentScale.Crop` fills every screen shape; the artwork carries enough margin
 * top and bottom that the small crop on tall or short displays takes nothing that
 * matters. The background colour matches the system splash so the handover has no
 * seam.
 */
@Composable
fun BrandSplashScreen(onDone: () -> Unit) {
    val done = remember { Once(onDone) }

    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MILLIS)
        done()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.splash_background))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { done() }
            .semantics { contentDescription = "" }
    ) {
        Image(
            painter = painterResource(R.drawable.splash_screen),
            contentDescription = stringResource(R.string.splash_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Runs the wrapped action at most once, whether the delay or a tap gets there first. */
private class Once(private val action: () -> Unit) {
    private var fired = false
    operator fun invoke() {
        if (fired) return
        fired = true
        action()
    }
}

private const val SPLASH_DURATION_MILLIS = 1_300L
