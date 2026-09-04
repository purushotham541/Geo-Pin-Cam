package com.letscode.geopincam.utils

import com.letscode.geopincam.domain.model.DateFormatStyle
import com.letscode.geopincam.domain.model.TimeFormatStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class StampDateTimeFormatterTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val locale = Locale.US

    /** 31 August 2026, 14:30:45 in Asia/Kolkata. */
    private val captureMillis = ZonedDateTime.of(2026, 8, 31, 14, 30, 45, 0, zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `default date style matches the product example`() {
        val formatted = StampDateTimeFormatter.formatDate(
            captureMillis,
            DateFormatStyle.DAY_MONTH_YEAR,
            zone,
            locale
        )

        assertEquals("31 Aug 2026", formatted)
    }

    @Test
    fun `every date style renders its own pattern`() {
        assertEquals(
            "31/08/2026",
            StampDateTimeFormatter.formatDate(
                captureMillis,
                DateFormatStyle.NUMERIC_DAY_FIRST,
                zone,
                locale
            )
        )
        assertEquals(
            "08/31/2026",
            StampDateTimeFormatter.formatDate(
                captureMillis,
                DateFormatStyle.NUMERIC_MONTH_FIRST,
                zone,
                locale
            )
        )
        assertEquals(
            "2026-08-31",
            StampDateTimeFormatter.formatDate(captureMillis, DateFormatStyle.ISO, zone, locale)
        )
    }

    @Test
    fun `twelve hour time matches the product example`() {
        val formatted = StampDateTimeFormatter.formatTime(
            captureMillis,
            TimeFormatStyle.HOUR_12,
            zone,
            locale
        )

        assertEquals("02:30 PM", formatted)
    }

    @Test
    fun `twenty four hour time drops the meridiem`() {
        assertEquals(
            "14:30",
            StampDateTimeFormatter.formatTime(captureMillis, TimeFormatStyle.HOUR_24, zone, locale)
        )
        assertEquals(
            "14:30:45",
            StampDateTimeFormatter.formatTime(
                captureMillis,
                TimeFormatStyle.HOUR_24_WITH_SECONDS,
                zone,
                locale
            )
        )
    }

    @Test
    fun `combined line joins date and time with the separator`() {
        val formatted = StampDateTimeFormatter.formatDateTime(
            captureMillis,
            DateFormatStyle.DAY_MONTH_YEAR,
            TimeFormatStyle.HOUR_12,
            " • ",
            zone,
            locale
        )

        assertEquals("31 Aug 2026 • 02:30 PM", formatted)
    }

    @Test
    fun `the same instant renders differently in another time zone`() {
        val formatted = StampDateTimeFormatter.formatTime(
            captureMillis,
            TimeFormatStyle.HOUR_24,
            ZoneId.of("UTC"),
            locale
        )

        assertEquals("09:00", formatted)
    }
}
