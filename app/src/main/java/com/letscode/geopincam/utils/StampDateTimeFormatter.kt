package com.letscode.geopincam.utils

import com.letscode.geopincam.domain.model.DateFormatStyle
import com.letscode.geopincam.domain.model.TimeFormatStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats capture timestamps according to the user's date and time preferences.
 *
 * Uses java.time, which is available without desugaring from API 26 (the app's
 * minimum SDK).
 */
object StampDateTimeFormatter {

    /** "31 Aug 2026" */
    fun formatDate(
        epochMillis: Long,
        style: DateFormatStyle,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String = format(epochMillis, style.pattern, zone, locale)

    /** "02:30 PM" */
    fun formatTime(
        epochMillis: Long,
        style: TimeFormatStyle,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String = format(epochMillis, style.pattern, zone, locale)

    /** "31 Aug 2026 - 02:30 PM", the combined line used on the stamp. */
    fun formatDateTime(
        epochMillis: Long,
        dateStyle: DateFormatStyle,
        timeStyle: TimeFormatStyle,
        separator: String,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): String = formatDate(epochMillis, dateStyle, zone, locale) +
        separator +
        formatTime(epochMillis, timeStyle, zone, locale)

    private fun format(
        epochMillis: Long,
        pattern: String,
        zone: ZoneId,
        locale: Locale
    ): String = DateTimeFormatter.ofPattern(pattern, locale)
        .withZone(zone)
        .format(Instant.ofEpochMilli(epochMillis))
}
