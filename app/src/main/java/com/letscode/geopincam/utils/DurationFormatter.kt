package com.letscode.geopincam.utils

import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/** Formats a running or finished recording length as a clock readout. */
object DurationFormatter {

    /** "0:07", "1:23" or "1:02:03" once the recording passes an hour. */
    fun clock(millis: Long, locale: Locale = Locale.getDefault()): String {
        val totalSeconds = millis.milliseconds.inWholeSeconds.coerceAtLeast(0)
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(locale, "%d:%02d", minutes, seconds)
        }
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
}
