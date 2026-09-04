package com.letscode.geopincam.domain

import com.letscode.geopincam.domain.model.AccuracyQuality
import com.letscode.geopincam.domain.model.CameraFacing
import com.letscode.geopincam.domain.model.CoordinateFormat
import com.letscode.geopincam.domain.model.DateFormatStyle
import com.letscode.geopincam.domain.model.FlashMode
import com.letscode.geopincam.domain.model.LocationData
import com.letscode.geopincam.domain.model.PhotoQuality
import com.letscode.geopincam.domain.model.PlaceAddress
import com.letscode.geopincam.domain.model.StampConfiguration
import com.letscode.geopincam.domain.model.StampFontSize
import com.letscode.geopincam.domain.model.StampPosition
import com.letscode.geopincam.domain.model.StampTextAlignment
import com.letscode.geopincam.domain.model.StorageSettings
import com.letscode.geopincam.domain.model.TimeFormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationDataTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `a fresh fix is not stale`() {
        val fix = LocationData(1.0, 2.0, timestampMillis = now - 1_000L)

        assertFalse(fix.isStale(now))
    }

    @Test
    fun `a fix older than the freshness window is stale`() {
        val fix = LocationData(1.0, 2.0, timestampMillis = now - LocationData.STALE_AFTER_MILLIS - 1)

        assertTrue(fix.isStale(now))
    }

    @Test
    fun `a fix with no timestamp counts as stale`() {
        assertTrue(LocationData(1.0, 2.0, timestampMillis = 0L).isStale(now))
    }

    @Test
    fun `age never goes negative for a clock that jumped`() {
        val fix = LocationData(1.0, 2.0, timestampMillis = now + 10_000L)

        assertEquals(0L, fix.ageMillis(now))
    }

    @Test
    fun `accuracy quality follows the reported accuracy`() {
        fun quality(accuracy: Float?) =
            LocationData(1.0, 2.0, accuracyMeters = accuracy).accuracyQuality()

        assertEquals(AccuracyQuality.GOOD, quality(5f))
        assertEquals(AccuracyQuality.FAIR, quality(30f))
        assertEquals(AccuracyQuality.POOR, quality(85f))
        assertEquals(AccuracyQuality.UNKNOWN, quality(null))
    }

    @Test
    fun `address parts are exposed as city state and country`() {
        val fix = LocationData(
            latitude = 17.0,
            longitude = 78.0,
            address = PlaceAddress(
                locality = "Hyderabad",
                adminArea = "Telangana",
                countryName = "India"
            )
        )

        assertEquals("Hyderabad", fix.city)
        assertEquals("Telangana", fix.state)
        assertEquals("India", fix.country)
    }
}

class PlaceAddressTest {

    @Test
    fun `display line joins the parts that are present`() {
        val address = PlaceAddress(
            locality = "Hyderabad",
            adminArea = "Telangana",
            countryName = "India"
        )

        assertEquals("Hyderabad, Telangana, India", address.displayLine)
    }

    @Test
    fun `duplicate parts are not repeated`() {
        val address = PlaceAddress(locality = "Singapore", adminArea = "Singapore")

        assertEquals("Singapore", address.displayLine)
    }

    @Test
    fun `the sub locality stands in when there is no locality`() {
        val address = PlaceAddress(subLocality = "Dundigal", countryName = "India")

        assertEquals("Dundigal, India", address.displayLine)
    }

    @Test
    fun `the country flag is derived from the country code`() {
        assertEquals("🇮🇳", PlaceAddress(countryCode = "IN").countryFlag)
        assertEquals("🇬🇧", PlaceAddress(countryCode = "gb").countryFlag)
    }

    @Test
    fun `a malformed country code yields no flag rather than mojibake`() {
        assertNull(PlaceAddress(countryCode = "").countryFlag)
        assertNull(PlaceAddress(countryCode = "IND").countryFlag)
        assertNull(PlaceAddress(countryCode = "1N").countryFlag)
        assertNull(PlaceAddress().countryFlag)
    }

    @Test
    fun `the place line prefers the composed line from the geocoder`() {
        val address = PlaceAddress(
            locality = "Hyderabad",
            fullAddressLine = "Canteen, Mlr Institute Of Technology, Hyderabad, India"
        )

        assertEquals("Canteen, Mlr Institute Of Technology, Hyderabad, India", address.placeLine)
    }

    @Test
    fun `the place line is assembled from the parts when no composed line exists`() {
        val address = PlaceAddress(
            premises = "Mlr Institute Of Technology",
            subThoroughfare = "12",
            thoroughfare = "Road No 12",
            subLocality = "Dundigal",
            locality = "Hyderabad",
            adminArea = "Telangana",
            postalCode = "500043",
            countryName = "India"
        )

        assertEquals(
            "Mlr Institute Of Technology, 12 Road No 12, Dundigal, Hyderabad, " +
                "Telangana 500043, India",
            address.placeLine
        )
    }

    @Test
    fun `plus codes are not used as the place name`() {
        val address = PlaceAddress(featureName = "7J9WGQ29+8C", locality = "Hyderabad")

        assertEquals("Hyderabad", address.placeLine)
    }

    @Test
    fun `a bare house number is not repeated as the place name`() {
        val address = PlaceAddress(
            featureName = "12",
            subThoroughfare = "12",
            thoroughfare = "Road No 12",
            locality = "Hyderabad"
        )

        assertEquals("12 Road No 12, Hyderabad", address.placeLine)
    }

    @Test
    fun `an empty address reports that it has no content`() {
        assertFalse(PlaceAddress().hasContent)
        assertTrue(PlaceAddress(countryName = "India").hasContent)
    }
}

class StampConfigurationTest {

    @Test
    fun `the default configuration shows the core fields`() {
        val config = StampConfiguration()

        assertTrue(config.stampEnabled)
        assertTrue(config.showAddress)
        assertTrue(config.showPlaceName)
        assertTrue(config.showMapThumbnail)
        assertTrue(config.showCoordinates)
        assertFalse(config.hasNoVisibleFields)
    }

    @Test
    fun `hasNoVisibleFields is true only when every field is off`() {
        val config = StampConfiguration(
            showAddress = false,
            showPlaceName = false,
            showCoordinates = false,
            showDate = false,
            showTime = false,
            showAccuracy = false,
            showAltitude = false,
            showSpeed = false,
            showBearing = false,
            showAppName = false
        )

        assertTrue(config.hasNoVisibleFields)
    }

    @Test
    fun `top positions are recognised`() {
        assertTrue(StampPosition.TOP_LEFT.isTop)
        assertTrue(StampPosition.TOP_CENTER.isTop)
        assertTrue(StampPosition.TOP_RIGHT.isTop)
        assertFalse(StampPosition.BOTTOM_LEFT.isTop)
    }

    @Test
    fun `every enum round trips through its persistence key`() {
        StampPosition.entries.forEach { assertEquals(it, StampPosition.fromKey(it.key)) }
        StampTextAlignment.entries.forEach { assertEquals(it, StampTextAlignment.fromKey(it.key)) }
        StampFontSize.entries.forEach { assertEquals(it, StampFontSize.fromKey(it.key)) }
        CoordinateFormat.entries.forEach { assertEquals(it, CoordinateFormat.fromKey(it.key)) }
        DateFormatStyle.entries.forEach { assertEquals(it, DateFormatStyle.fromKey(it.key)) }
        TimeFormatStyle.entries.forEach { assertEquals(it, TimeFormatStyle.fromKey(it.key)) }
        PhotoQuality.entries.forEach { assertEquals(it, PhotoQuality.fromKey(it.key)) }
        FlashMode.entries.forEach { assertEquals(it, FlashMode.fromKey(it.key)) }
        CameraFacing.entries.forEach { assertEquals(it, CameraFacing.fromKey(it.key)) }
    }

    @Test
    fun `an unknown key falls back to the default rather than throwing`() {
        assertEquals(StampPosition.BOTTOM_LEFT, StampPosition.fromKey("nonsense"))
        assertEquals(StampPosition.BOTTOM_LEFT, StampPosition.fromKey(null))
        assertEquals(PhotoQuality.HIGH, PhotoQuality.fromKey(null))
        assertEquals(CoordinateFormat.LABELLED, CoordinateFormat.fromKey(""))
        assertEquals(DateFormatStyle.WEEKDAY_FULL, DateFormatStyle.fromKey("nonsense"))
        assertEquals(TimeFormatStyle.HOUR_12_WITH_ZONE, TimeFormatStyle.fromKey(null))
    }

    @Test
    fun `font sizes grow monotonically`() {
        val scales = StampFontSize.entries.map { it.scale }

        assertEquals(scales.sorted(), scales)
    }

    @Test
    fun `photo quality presets map to sensible jpeg quality`() {
        assertTrue(PhotoQuality.HIGH.jpegQuality > PhotoQuality.MEDIUM.jpegQuality)
        assertTrue(PhotoQuality.MEDIUM.jpegQuality > PhotoQuality.LOW.jpegQuality)
        assertTrue(PhotoQuality.LOW.jpegQuality in 1..100)
    }

    @Test
    fun `flash cycles through every mode and back to off`() {
        assertEquals(FlashMode.AUTO, FlashMode.OFF.next())
        assertEquals(FlashMode.ON, FlashMode.AUTO.next())
        assertEquals(FlashMode.TORCH, FlashMode.ON.next())
        assertEquals(FlashMode.OFF, FlashMode.TORCH.next())
    }

    @Test
    fun `only the torch mode holds the lamp on`() {
        assertTrue(FlashMode.TORCH.isTorch)
        assertFalse(FlashMode.ON.isTorch)
        assertFalse(FlashMode.AUTO.isTorch)
        assertFalse(FlashMode.OFF.isTorch)
    }

    @Test
    fun `camera facing toggles both ways`() {
        assertEquals(CameraFacing.FRONT, CameraFacing.BACK.toggled())
        assertEquals(CameraFacing.BACK, CameraFacing.FRONT.toggled())
    }
}

class StorageSettingsTest {

    @Test
    fun `the stamped copy is saved by default`() {
        assertTrue(StorageSettings().effectiveSaveStamped)
    }

    @Test
    fun `turning the stamped copy off only takes effect once originals are kept`() {
        // Without an original there would be nothing left to save, so the stamped
        // copy is written regardless and the shot is never silently discarded.
        assertTrue(
            StorageSettings(saveStampedPhoto = false, saveOriginalPhoto = false)
                .effectiveSaveStamped
        )
        assertFalse(
            StorageSettings(saveStampedPhoto = false, saveOriginalPhoto = true)
                .effectiveSaveStamped
        )
    }
}
