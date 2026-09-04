package com.letscode.geopincam

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.letscode.geopincam.data.preferences.SettingsRepository
import com.letscode.geopincam.domain.model.CoordinateFormat
import com.letscode.geopincam.domain.model.DateFormatStyle
import com.letscode.geopincam.domain.model.PhotoQuality
import com.letscode.geopincam.domain.model.StampConfiguration
import com.letscode.geopincam.domain.model.StampPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that preferences round trip through DataStore, which is what makes
 * settings survive an app restart.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = SettingsRepository(context)

    @Test
    fun defaultsAreReturnedBeforeAnythingIsWritten() = runTest {
        val settings = repository.settings.first()

        // Whatever a previous run left behind, the values must be valid members of
        // their enums rather than nulls or crashes.
        assertTrue(settings.stamp.backgroundOpacity in 0f..1f)
        assertTrue(settings.camera.photoQuality.jpegQuality in 1..100)
    }

    @Test
    fun writtenValuesAreReadBackFromANewRepositoryInstance() = runTest {
        repository.setStampPosition(StampPosition.TOP_RIGHT)
        repository.setCoordinateFormat(CoordinateFormat.DMS)
        repository.setPhotoQuality(PhotoQuality.LOW)
        repository.setDateFormat(DateFormatStyle.ISO)
        repository.setShowSpeed(true)

        // A second instance reads the same DataStore file, which is how the app
        // sees the values after a process restart.
        val reopened = SettingsRepository(context).settings.first()

        assertEquals(StampPosition.TOP_RIGHT, reopened.stamp.position)
        assertEquals(CoordinateFormat.DMS, reopened.stamp.coordinateFormat)
        assertEquals(PhotoQuality.LOW, reopened.camera.photoQuality)
        assertEquals(DateFormatStyle.ISO, reopened.stamp.dateFormat)
        assertTrue(reopened.stamp.showSpeed)
    }

    @Test
    fun stampFieldsToggleIndependently() = runTest {
        repository.setShowAddress(false)
        repository.setShowCoordinates(true)

        val settings = repository.settings.first()

        assertFalse(settings.stamp.showAddress)
        assertTrue(settings.stamp.showCoordinates)
    }

    @Test
    fun backgroundOpacityIsClampedToTheSupportedRange() = runTest {
        repository.setBackgroundOpacity(5f)
        assertEquals(
            StampConfiguration.MAX_BACKGROUND_OPACITY,
            repository.settings.first().stamp.backgroundOpacity,
            TOLERANCE
        )

        repository.setBackgroundOpacity(-2f)
        assertEquals(
            StampConfiguration.MIN_BACKGROUND_OPACITY,
            repository.settings.first().stamp.backgroundOpacity,
            TOLERANCE
        )
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
