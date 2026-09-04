package com.letscode.geopincam.utils

import com.letscode.geopincam.domain.model.CoordinateFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CoordinateFormatterTest {

    private val locale = Locale.US

    @Test
    fun `decimal format matches the product example`() {
        val formatted = CoordinateFormatter.format(
            latitude = 17.3850,
            longitude = 78.4867,
            format = CoordinateFormat.DECIMAL,
            locale = locale
        )

        assertEquals("17.3850° N, 78.4867° E", formatted)
    }

    @Test
    fun `dms format matches the product example`() {
        val formatted = CoordinateFormatter.format(
            latitude = 17.3850,
            longitude = 78.4867,
            format = CoordinateFormat.DMS,
            locale = locale
        )

        assertEquals("17°23'06\"N, 78°29'12\"E", formatted)
    }

    @Test
    fun `labelled format matches the reference stamp`() {
        val formatted = CoordinateFormatter.format(
            latitude = 17.594678,
            longitude = 78.441647,
            format = CoordinateFormat.LABELLED,
            locale = locale
        )

        assertEquals("Lat 17.594678° Long 78.441647°", formatted)
    }

    @Test
    fun `labelled format keeps the sign for southern and western points`() {
        val formatted = CoordinateFormatter.formatLabelled(-33.868800, -70.669300, locale)

        assertEquals("Lat -33.868800° Long -70.669300°", formatted)
    }

    @Test
    fun `the labelled template can be localised`() {
        val formatted = CoordinateFormatter.formatLabelled(
            latitude = 1.5,
            longitude = 2.5,
            locale = locale,
            template = "B %1\$s / L %2\$s"
        )

        assertEquals("B 1.500000 / L 2.500000", formatted)
    }

    @Test
    fun `southern and western hemispheres use S and W`() {
        assertEquals("33.8688° S", CoordinateFormatter.formatDecimal(-33.8688, true, locale))
        assertEquals("70.6693° W", CoordinateFormatter.formatDecimal(-70.6693, false, locale))
    }

    @Test
    fun `zero is treated as north and east`() {
        assertEquals("0.0000° N", CoordinateFormatter.formatDecimal(0.0, true, locale))
        assertEquals("0.0000° E", CoordinateFormatter.formatDecimal(0.0, false, locale))
    }

    @Test
    fun `dms carries rounded seconds into the next minute`() {
        // 10.999999 degrees rounds to 60 seconds, which must become the next minute.
        val formatted = CoordinateFormatter.formatDms(10.9999999, true, locale)

        assertEquals("11°00'00\"N", formatted)
    }

    @Test
    fun `dms pads minutes and seconds to two digits`() {
        assertEquals("5°01'02\"N", CoordinateFormatter.formatDms(5.017222, true, locale))
    }

    @Test
    fun `metres are rounded to whole numbers`() {
        assertEquals("5", CoordinateFormatter.formatMeters(4.7, locale))
        assertEquals("520", CoordinateFormatter.formatMeters(519.6, locale))
    }

    @Test
    fun `speed is converted from metres per second to kilometres per hour`() {
        assertEquals("36.0", CoordinateFormatter.formatSpeedKmh(10f, locale))
        assertEquals("0.0", CoordinateFormatter.formatSpeedKmh(0f, locale))
    }

    @Test
    fun `bearing is normalised and labelled with a compass point`() {
        assertEquals("0° N", CoordinateFormatter.formatBearing(360f, locale))
        assertEquals("135° SE", CoordinateFormatter.formatBearing(135f, locale))
        assertEquals("270° W", CoordinateFormatter.formatBearing(-90f, locale))
    }

    @Test
    fun `cardinal points snap to the nearest of eight sectors`() {
        assertEquals("N", CoordinateFormatter.cardinalPoint(0.0))
        assertEquals("N", CoordinateFormatter.cardinalPoint(359.0))
        assertEquals("NE", CoordinateFormatter.cardinalPoint(44.0))
        assertEquals("S", CoordinateFormatter.cardinalPoint(180.0))
    }
}
