package com.letscode.geopincam.domain.usecase

import com.letscode.geopincam.domain.model.AddressDetail
import com.letscode.geopincam.domain.model.LocationData
import com.letscode.geopincam.domain.model.StampConfiguration
import com.letscode.geopincam.utils.CoordinateFormatter
import com.letscode.geopincam.utils.StampDateTimeFormatter
import java.time.ZoneId
import java.util.Locale

/** Visual weight of a stamp line; the renderer maps these onto text sizes. */
enum class StampLineStyle { TITLE, BODY, FOOTER }

data class StampLine(val text: String, val style: StampLineStyle)

/**
 * Localised strings the stamp needs. Supplied by the Android layer from string
 * resources so that this builder stays free of framework dependencies and can be
 * unit tested directly.
 */
data class StampLabels(
    val appName: String,
    val locationPin: String,
    val altitudeFormat: String,
    val accuracyFormat: String,
    val speedFormat: String,
    val bearingFormat: String,
    val dateTimeSeparator: String,
    val coordinateLabelFormat: String,
    val addressUnavailable: String,
    val locationUnavailable: String,
    val staleLocationNote: String
)

/**
 * Turns a location fix, a capture time and the user's stamp preferences into the
 * ordered list of text lines drawn onto the photograph.
 */
class StampTextBuilder(private val labels: StampLabels) {

    fun build(
        location: LocationData?,
        captureTimeMillis: Long,
        config: StampConfiguration,
        addressDetail: AddressDetail = AddressDetail.SHORT,
        isLocationStale: Boolean = false,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault()
    ): List<StampLine> {
        if (!config.stampEnabled || config.hasNoVisibleFields) return emptyList()

        val lines = mutableListOf<StampLine>()
        val coordinates = location?.let {
            CoordinateFormatter.format(
                latitude = it.latitude,
                longitude = it.longitude,
                format = config.coordinateFormat,
                locale = locale,
                labelledTemplate = labels.coordinateLabelFormat
            )
        }
        var coordinatesShown = false

        val place = location?.address
        if (config.showAddress) {
            val headline = headline(location, config, addressDetail)
            when {
                location == null ->
                    lines += title(labels.locationUnavailable, config)

                headline != null ->
                    lines += title(headline, config)

                // Offline or no match: lead with the coordinates, which are still
                // exact, and note underneath that the address could not be read.
                coordinates != null -> {
                    lines += title(coordinates, config)
                    coordinatesShown = true
                    lines += StampLine(labels.addressUnavailable, StampLineStyle.FOOTER)
                }

                else -> lines += title(labels.addressUnavailable, config)
            }
        }

        // The exact place: premises or business name plus the street and postcode,
        // e.g. "Canteen, Mlr Institute Of Technology, Hyderabad, Telangana 500043".
        if (config.showPlaceName) {
            place?.placeLine
                // The headline may already be this exact line, with a flag appended.
                ?.takeIf { it.isNotBlank() && lastTitleText(lines)?.contains(it) != true }
                ?.let { lines += StampLine(it, StampLineStyle.BODY) }
        }

        if (config.showCoordinates && coordinates != null && !coordinatesShown) {
            lines += StampLine(coordinates, StampLineStyle.BODY)
        }

        if (config.showAltitude) {
            location?.altitudeMeters?.let {
                lines += body(labels.altitudeFormat, CoordinateFormatter.formatMeters(it, locale))
            }
        }

        if (config.showAccuracy) {
            location?.accuracyMeters?.let {
                lines += body(
                    labels.accuracyFormat,
                    CoordinateFormatter.formatMeters(it.toDouble(), locale)
                )
            }
        }

        if (config.showSpeed) {
            location?.speedMetersPerSecond?.let {
                lines += body(labels.speedFormat, CoordinateFormatter.formatSpeedKmh(it, locale))
            }
        }

        if (config.showBearing) {
            location?.bearingDegrees?.let {
                lines += body(labels.bearingFormat, CoordinateFormatter.formatBearing(it, locale))
            }
        }

        dateTimeLine(captureTimeMillis, config, zone, locale)?.let {
            lines += StampLine(it, StampLineStyle.BODY)
        }

        if (isLocationStale && location != null) {
            lines += StampLine(labels.staleLocationNote, StampLineStyle.FOOTER)
        }

        if (config.showAppName) {
            lines += StampLine(labels.appName, StampLineStyle.FOOTER)
        }

        return lines
    }

    /**
     * The headline: "Hyderabad, Telangana, India 🇮🇳", with the flag appended when
     * the country is known and the user has left flags switched on.
     */
    private fun headline(
        location: LocationData?,
        config: StampConfiguration,
        detail: AddressDetail
    ): String? {
        val address = location?.address ?: return null
        if (!address.hasContent) return null
        val short = address.displayLine.takeIf { it.isNotEmpty() }
        val base = when (detail) {
            AddressDetail.FULL -> address.placeLine ?: short
            AddressDetail.SHORT -> short ?: address.placeLine
        } ?: return null
        val flag = address.countryFlag.takeIf { config.showCountryFlag }
        return if (flag != null) "$base $flag" else base
    }

    private fun lastTitleText(lines: List<StampLine>): String? =
        lines.lastOrNull { it.style == StampLineStyle.TITLE }?.text

    /**
     * The pin only leads the headline when no map thumbnail is drawn; with the map
     * present the thumbnail already marks the spot, as in the reference layout.
     */
    private fun title(text: String, config: StampConfiguration) = StampLine(
        if (config.showMapThumbnail) text else "${labels.locationPin} $text",
        StampLineStyle.TITLE
    )

    private fun dateTimeLine(
        captureTimeMillis: Long,
        config: StampConfiguration,
        zone: ZoneId,
        locale: Locale
    ): String? = when {
        config.showDate && config.showTime -> StampDateTimeFormatter.formatDateTime(
            captureTimeMillis,
            config.dateFormat,
            config.timeFormat,
            labels.dateTimeSeparator,
            zone,
            locale
        )
        config.showDate ->
            StampDateTimeFormatter.formatDate(captureTimeMillis, config.dateFormat, zone, locale)
        config.showTime ->
            StampDateTimeFormatter.formatTime(captureTimeMillis, config.timeFormat, zone, locale)
        else -> null
    }

    private fun body(format: String, value: String) =
        StampLine(String.format(format, value), StampLineStyle.BODY)
}
