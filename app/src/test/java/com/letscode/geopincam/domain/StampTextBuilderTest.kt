package com.letscode.geopincam.domain

import com.letscode.geopincam.domain.model.AddressDetail
import com.letscode.geopincam.domain.model.CoordinateFormat
import com.letscode.geopincam.domain.model.DateFormatStyle
import com.letscode.geopincam.domain.model.LocationData
import com.letscode.geopincam.domain.model.PlaceAddress
import com.letscode.geopincam.domain.model.StampConfiguration
import com.letscode.geopincam.domain.model.TimeFormatStyle
import com.letscode.geopincam.domain.usecase.StampLabels
import com.letscode.geopincam.domain.usecase.StampLineStyle
import com.letscode.geopincam.domain.usecase.StampTextBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class StampTextBuilderTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val locale = Locale.US

    private val labels = StampLabels(
        appName = "Geo Pin Cam",
        locationPin = "📍",
        altitudeFormat = "Altitude: %s m",
        accuracyFormat = "Accuracy: ±%s m",
        speedFormat = "Speed: %s km/h",
        bearingFormat = "Direction: %s",
        dateTimeSeparator = " • ",
        coordinateLabelFormat = "Lat %1\$s° Long %2\$s°",
        addressUnavailable = "Address unavailable",
        locationUnavailable = "Location unavailable",
        staleLocationNote = "Location is not current"
    )

    private val builder = StampTextBuilder(labels)

    private val captureMillis = ZonedDateTime.of(2026, 8, 31, 14, 30, 0, 0, zone)
        .toInstant()
        .toEpochMilli()

    private val hyderabad = LocationData(
        latitude = 17.3850,
        longitude = 78.4867,
        accuracyMeters = 5f,
        altitudeMeters = 520.0,
        speedMetersPerSecond = 3f,
        bearingDegrees = 135f,
        timestampMillis = captureMillis,
        address = PlaceAddress(
            featureName = "Canteen",
            premises = "Mlr Institute Of Technology",
            subLocality = "Dundigal",
            locality = "Hyderabad",
            adminArea = "Telangana",
            postalCode = "500043",
            countryName = "India",
            countryCode = "IN",
            fullAddressLine = "Canteen, Mlr Institute Of Technology, Hyderabad, " +
                "Telangana 500043, India"
        )
    )

    private fun build(
        location: LocationData? = hyderabad,
        config: StampConfiguration = StampConfiguration(),
        detail: AddressDetail = AddressDetail.SHORT,
        stale: Boolean = false
    ) = builder.build(
        location = location,
        captureTimeMillis = captureMillis,
        config = config,
        addressDetail = detail,
        isLocationStale = stale,
        zone = zone,
        locale = locale
    )

    @Test
    fun `default configuration reads like the reference stamp`() {
        val lines = build().map { it.text }

        assertEquals(
            listOf(
                "Hyderabad, Telangana, India 🇮🇳",
                "Canteen, Mlr Institute Of Technology, Hyderabad, Telangana 500043, India",
                "Lat 17.385000° Long 78.486700°",
                "Altitude: 520 m",
                "Accuracy: ±5 m",
                "Monday, 31/08/2026 • 02:30 PM GMT +05:30",
                "Geo Pin Cam"
            ),
            lines
        )
    }

    @Test
    fun `the classic layout is still available through the settings`() {
        val classic = StampConfiguration(
            showPlaceName = false,
            showCountryFlag = false,
            showMapThumbnail = false,
            coordinateFormat = CoordinateFormat.DECIMAL,
            dateFormat = DateFormatStyle.DAY_MONTH_YEAR,
            timeFormat = TimeFormatStyle.HOUR_12
        )

        assertEquals(
            listOf(
                "📍 Hyderabad, Telangana, India",
                "17.3850° N, 78.4867° E",
                "Altitude: 520 m",
                "Accuracy: ±5 m",
                "31 Aug 2026 • 02:30 PM",
                "Geo Pin Cam"
            ),
            build(config = classic).map { it.text }
        )
    }

    @Test
    fun `the pin leads the headline only when no map is drawn`() {
        val withMap = build().first().text
        val withoutMap = build(config = StampConfiguration(showMapThumbnail = false)).first().text

        assertFalse(withMap.startsWith("📍"))
        assertTrue(withoutMap.startsWith("📍"))
    }

    @Test
    fun `the country flag can be turned off`() {
        val lines = build(config = StampConfiguration(showCountryFlag = false)).map { it.text }

        assertEquals("Hyderabad, Telangana, India", lines.first())
    }

    @Test
    fun `the exact place is not repeated when it is already the headline`() {
        // With FULL detail the headline is the place line, so the body must not
        // print the very same string a second time.
        val lines = build(detail = AddressDetail.FULL).map { it.text }

        assertEquals(1, lines.count { it.contains("Canteen, Mlr Institute Of Technology") })
    }

    @Test
    fun `the exact place is assembled when the geocoder gives no composed line`() {
        val parts = hyderabad.copy(address = hyderabad.address?.copy(fullAddressLine = null))

        val lines = build(location = parts).map { it.text }

        assertTrue(
            lines.any { it.contains("Mlr Institute Of Technology") && it.contains("500043") }
        )
    }

    @Test
    fun `the address is the title line and the app name is the footer`() {
        val lines = build()

        assertEquals(StampLineStyle.TITLE, lines.first().style)
        assertEquals(StampLineStyle.FOOTER, lines.last().style)
    }

    @Test
    fun `disabling the stamp produces no lines`() {
        val lines = build(config = StampConfiguration(stampEnabled = false))

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `turning every field off produces no lines`() {
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

        assertTrue(build(config = config).isEmpty())
    }

    @Test
    fun `each field can be switched off independently`() {
        val lines = build(
            config = StampConfiguration(showAltitude = false, showAccuracy = false)
        ).map { it.text }

        assertFalse(lines.any { it.startsWith("Altitude") })
        assertFalse(lines.any { it.startsWith("Accuracy") })
        assertTrue(lines.any { it.startsWith("Lat 17.385000") })
    }

    @Test
    fun `speed and bearing appear only when enabled`() {
        val config = StampConfiguration(showSpeed = true, showBearing = true)
        val lines = build(config = config).map { it.text }

        assertTrue(lines.contains("Speed: 10.8 km/h"))
        assertTrue(lines.contains("Direction: 135° SE"))
    }

    @Test
    fun `missing address leads with the coordinates and notes the lookup failed`() {
        val lines = build(location = hyderabad.copy(address = null)).map { it.text }

        // Matches the offline behaviour in the specification: the headline carries
        // the coordinates and the note sits directly underneath it.
        assertEquals("Lat 17.385000° Long 78.486700°", lines[0])
        assertEquals("Address unavailable", lines[1])
        // The coordinates are not repeated as their own row.
        assertEquals(1, lines.count { it.contains("17.385000") })
    }

    @Test
    fun `no fix at all still stamps the date and the app name`() {
        val lines = build(location = null).map { it.text }

        assertEquals(
            listOf(
                "Location unavailable",
                "Monday, 31/08/2026 • 02:30 PM GMT +05:30",
                "Geo Pin Cam"
            ),
            lines
        )
    }

    @Test
    fun `full address detail promotes the exact place to the headline`() {
        val lines = build(detail = AddressDetail.FULL).map { it.text }

        assertEquals(
            "Canteen, Mlr Institute Of Technology, Hyderabad, Telangana 500043, India 🇮🇳",
            lines.first()
        )
    }

    @Test
    fun `dms coordinate format is honoured`() {
        val config = StampConfiguration(coordinateFormat = CoordinateFormat.DMS)
        val lines = build(config = config).map { it.text }

        assertTrue(lines.contains("17°23'06\"N, 78°29'12\"E"))
    }

    @Test
    fun `a stale fix is called out above the app name`() {
        val lines = build(stale = true).map { it.text }

        assertEquals("Location is not current", lines[lines.lastIndex - 1])
    }

    @Test
    fun `values the platform did not supply are omitted rather than invented`() {
        val bare = LocationData(
            latitude = 17.3850,
            longitude = 78.4867,
            timestampMillis = captureMillis
        )

        val lines = build(location = bare, config = StampConfiguration(showSpeed = true))
            .map { it.text }

        assertFalse(lines.any { it.startsWith("Altitude") })
        assertFalse(lines.any { it.startsWith("Accuracy") })
        assertFalse(lines.any { it.startsWith("Speed") })
    }

    @Test
    fun `date only and time only configurations render a single row`() {
        val dateOnly = build(config = StampConfiguration(showTime = false)).map { it.text }
        val timeOnly = build(config = StampConfiguration(showDate = false)).map { it.text }

        assertTrue(dateOnly.contains("Monday, 31/08/2026"))
        assertTrue(timeOnly.contains("02:30 PM GMT +05:30"))
    }
}
