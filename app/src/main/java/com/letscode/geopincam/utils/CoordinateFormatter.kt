package com.letscode.geopincam.utils

import com.letscode.geopincam.domain.model.CoordinateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Formats geographic values for display and for the photo stamp.
 *
 * Pure Kotlin on purpose: every function here is covered by unit tests and must
 * behave identically on the device and on the JVM.
 */
object CoordinateFormatter {

    private const val DECIMAL_PLACES = 4
    private const val PRECISE_DECIMAL_PLACES = 6
    private const val DEFAULT_LABELLED_TEMPLATE = "Lat %1\$s° Long %2\$s°"
    private const val MINUTES_PER_DEGREE = 60.0
    private const val SECONDS_PER_MINUTE = 60.0
    private const val METERS_PER_SECOND_TO_KMH = 3.6f
    private const val DEGREES_IN_CIRCLE = 360.0
    private const val CARDINAL_SECTOR_DEGREES = 45.0

    private val CARDINAL_POINTS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

    /** "17.3850° N, 78.4867° E" or "17°23'06\"N, 78°29'12\"E" depending on [format]. */
    fun format(
        latitude: Double,
        longitude: Double,
        format: CoordinateFormat,
        locale: Locale = Locale.getDefault(),
        labelledTemplate: String = DEFAULT_LABELLED_TEMPLATE
    ): String = when (format) {
        CoordinateFormat.LABELLED ->
            formatLabelled(latitude, longitude, locale, labelledTemplate)
        CoordinateFormat.DECIMAL ->
            "${formatDecimal(latitude, true, locale)}, ${formatDecimal(longitude, false, locale)}"
        CoordinateFormat.DMS ->
            "${formatDms(latitude, true, locale)}, ${formatDms(longitude, false, locale)}"
    }

    /**
     * "Lat 17.594678° Long 78.441647°" - signed, labelled and precise to six
     * decimal places, which is roughly a tenth of a metre.
     *
     * The template comes from string resources so the labels can be localised.
     */
    fun formatLabelled(
        latitude: Double,
        longitude: Double,
        locale: Locale = Locale.getDefault(),
        template: String = DEFAULT_LABELLED_TEMPLATE
    ): String = String.format(
        locale,
        template,
        String.format(locale, "%.${PRECISE_DECIMAL_PLACES}f", latitude),
        String.format(locale, "%.${PRECISE_DECIMAL_PLACES}f", longitude)
    )

    /** "17.3850° N" */
    fun formatDecimal(value: Double, isLatitude: Boolean, locale: Locale = Locale.getDefault()): String {
        val magnitude = String.format(locale, "%.${DECIMAL_PLACES}f", abs(value))
        return "$magnitude° ${hemisphere(value, isLatitude)}"
    }

    /** "17°23'06\"N" */
    fun formatDms(value: Double, isLatitude: Boolean, locale: Locale = Locale.getDefault()): String {
        val absolute = abs(value)
        var degrees = floor(absolute).toInt()
        val minutesTotal = (absolute - degrees) * MINUTES_PER_DEGREE
        var minutes = floor(minutesTotal).toInt()
        var seconds = ((minutesTotal - minutes) * SECONDS_PER_MINUTE).roundToInt()

        // Rounding can push seconds or minutes to 60; carry the overflow upwards.
        if (seconds == SECONDS_PER_MINUTE.toInt()) {
            seconds = 0
            minutes += 1
        }
        if (minutes == MINUTES_PER_DEGREE.toInt()) {
            minutes = 0
            degrees += 1
        }
        val body = String.format(locale, "%d°%02d'%02d\"", degrees, minutes, seconds)
        return body + hemisphere(value, isLatitude)
    }

    private fun hemisphere(value: Double, isLatitude: Boolean): String = when {
        isLatitude && value >= 0 -> "N"
        isLatitude -> "S"
        value >= 0 -> "E"
        else -> "W"
    }

    /** Rounds metres to a whole number, e.g. "5" for an accuracy of 4.7 m. */
    fun formatMeters(meters: Double, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.0f", meters)

    /** Converts m/s to km/h and rounds to one decimal, e.g. "12.4". */
    fun formatSpeedKmh(metersPerSecond: Float, locale: Locale = Locale.getDefault()): String =
        String.format(locale, "%.1f", metersPerSecond * METERS_PER_SECOND_TO_KMH)

    /** "132° SE" - the compass bearing plus its nearest cardinal point. */
    fun formatBearing(degrees: Float, locale: Locale = Locale.getDefault()): String {
        val normalized = ((degrees % DEGREES_IN_CIRCLE) + DEGREES_IN_CIRCLE) % DEGREES_IN_CIRCLE
        return String.format(locale, "%.0f° %s", normalized, cardinalPoint(normalized))
    }

    /** Nearest of the eight compass points for a normalised bearing. */
    fun cardinalPoint(degrees: Double): String {
        val index = ((degrees + CARDINAL_SECTOR_DEGREES / 2) / CARDINAL_SECTOR_DEGREES).toInt()
        return CARDINAL_POINTS[index % CARDINAL_POINTS.size]
    }
}
