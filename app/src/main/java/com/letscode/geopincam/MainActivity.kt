package com.letscode.geopincam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.letscode.geopincam.ui.navigation.GeoPinCamNavHost
import com.letscode.geopincam.ui.theme.GeoPinCamTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Single activity host for Geo Pin Cam.
 *
 * Draws edge to edge so the camera preview can fill the display, and lets the
 * system handle rotation by recreating the activity; CameraX rebinds itself and
 * the stamp is rendered from the photo's own dimensions, so both orientations
 * produce a correctly oriented result.
 */
class MainActivity : ComponentActivity() {

    /** Flipped once the stored settings are loaded, which releases the splash. */
    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the splash for the single read that decides how the first frame
        // looks, rather than for an arbitrary delay.
        splashScreen.setKeepOnScreenCondition { !isReady }
        lifecycleScope.launch {
            runCatching { applicationContainer().settingsRepository.settings.first() }
            isReady = true
        }

        enableEdgeToEdge()
        setContent {
            GeoPinCamTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GeoPinCamNavHost()
                }
            }
        }
    }

    private fun applicationContainer() = (application as GeoPinCamApp).container
}
