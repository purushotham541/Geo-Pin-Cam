package com.letscode.geopincam

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the main navigation paths with the permissions already granted.
 *
 * Requires a connected device or emulator with a camera.
 */
@RunWith(AndroidJUnit4::class)
class NavigationUiTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** The branded splash shows first; wait for it to hand over to the camera. */
    @Before
    fun waitForCamera() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription(shutterLabel())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun cameraScreenShowsItsControls() {
        composeRule.onNodeWithContentDescription(shutterLabel()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(settingsLabel()).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(galleryLabel()).assertIsDisplayed()
    }

    @Test
    fun settingsOpensAndShowsEverySection() {
        composeRule.onNodeWithContentDescription(settingsLabel()).performClick()

        composeRule.onNodeWithText(string(R.string.settings_section_camera)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_section_stamp)).assertIsDisplayed()
    }

    @Test
    fun settingsCanBeDismissedBackToTheCamera() {
        composeRule.onNodeWithContentDescription(settingsLabel()).performClick()
        composeRule.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(string(R.string.action_back)).performClick()

        composeRule.onNodeWithContentDescription(shutterLabel()).assertIsDisplayed()
    }

    @Test
    fun galleryOpensFromTheCameraScreen() {
        composeRule.onNodeWithContentDescription(galleryLabel()).performClick()

        composeRule.onNodeWithText(string(R.string.gallery_title)).assertIsDisplayed()
    }

    @Test
    fun theStampCanBeTurnedOffAndOnAgain() {
        composeRule.onNodeWithContentDescription(settingsLabel()).performClick()

        val label = string(R.string.settings_stamp_enabled)
        composeRule.onNodeWithText(label).performClick()
        composeRule.onNodeWithText(label).performClick()

        composeRule.onNodeWithText(label).assertIsDisplayed()
    }

    private fun string(resId: Int): String = composeRule.activity.getString(resId)

    private fun shutterLabel() = string(R.string.camera_shutter)

    private fun settingsLabel() = string(R.string.camera_open_settings)

    private fun galleryLabel() = string(R.string.camera_open_gallery)
}
