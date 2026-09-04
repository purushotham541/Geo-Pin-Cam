package com.letscode.geopincam.domain.model

/**
 * A human readable address resolved from coordinates by reverse geocoding.
 *
 * Every field is optional because geocoders frequently return partial results,
 * especially in rural areas or when the device is offline.
 */
data class PlaceAddress(
    /** The most specific name the geocoder knows, often a building or business. */
    val featureName: String? = null,
    val premises: String? = null,
    val subThoroughfare: String? = null,
    val thoroughfare: String? = null,
    val subLocality: String? = null,
    val locality: String? = null,
    val subAdminArea: String? = null,
    val adminArea: String? = null,
    val postalCode: String? = null,
    val countryName: String? = null,
    /** ISO 3166-1 alpha-2, used to derive the flag emoji. */
    val countryCode: String? = null,
    val fullAddressLine: String? = null
) {
    /** "Hyderabad, Telangana, India" - the headline the stamp leads with. */
    val displayLine: String
        get() = listOfNotNull(locality ?: subLocality, adminArea, countryName)
            .distinct()
            .joinToString(SEPARATOR)

    /**
     * The exact place, as specific as the geocoder can be:
     * "Canteen, Mlr Institute Of Technology, Hyderabad, Telangana 500043, India".
     *
     * Prefers the geocoder's own composed line, which is what carries premises and
     * business names, and assembles one from the parts when that is missing.
     */
    val placeLine: String?
        get() = fullAddressLine?.takeIf { it.isNotBlank() } ?: assembledPlaceLine()

    /** The regional indicator pair for [countryCode], for example 🇮🇳. */
    val countryFlag: String?
        get() {
            val code = countryCode?.trim()?.uppercase() ?: return null
            if (code.length != COUNTRY_CODE_LENGTH || !code.all { it in 'A'..'Z' }) return null
            return code.map { Character.toChars(REGIONAL_INDICATOR_BASE + (it - 'A')) }
                .joinToString("") { String(it) }
        }

    val hasContent: Boolean
        get() = displayLine.isNotEmpty() || !placeLine.isNullOrBlank()

    private fun assembledPlaceLine(): String? {
        val street = listOfNotNull(subThoroughfare, thoroughfare)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
        val region = listOfNotNull(adminArea, postalCode)
            .joinToString(" ")
            .takeIf { it.isNotBlank() }

        return listOfNotNull(
            specificName(),
            street,
            subLocality,
            locality,
            region,
            countryName
        )
            .distinct()
            .joinToString(SEPARATOR)
            .takeIf { it.isNotEmpty() }
    }

    /**
     * The building or business name, ignoring feature names that are really just
     * a house number or a plus code and would add nothing to the line.
     */
    private fun specificName(): String? {
        val candidate = premises?.trim()?.takeIf { it.isNotEmpty() }
            ?: featureName?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null
        val isJustANumber = candidate == subThoroughfare || candidate.all { !it.isLetter() }
        val isPlusCode = PLUS_CODE.matches(candidate)
        return candidate.takeUnless { isJustANumber || isPlusCode }
    }

    private companion object {
        const val SEPARATOR = ", "
        const val COUNTRY_CODE_LENGTH = 2

        /** First regional indicator symbol, U+1F1E6, which stands for "A". */
        const val REGIONAL_INDICATOR_BASE = 0x1F1E6

        /** Open Location Codes such as "7J9WGQ29+8C" are not useful place names. */
        val PLUS_CODE = Regex("^[23456789CFGHJMPQRVWX]{4,8}\\+[23456789CFGHJMPQRVWX]{2,3}$")
    }
}

/**
 * A single location fix as reported by the Android location APIs.
 *
 * Values that the platform did not supply stay null; the app never invents them
 * (see the accuracy requirement in the product spec).
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val speedMetersPerSecond: Float? = null,
    val bearingDegrees: Float? = null,
    val timestampMillis: Long = 0L,
    val isMock: Boolean = false,
    val address: PlaceAddress? = null
) {
    val city: String? get() = address?.locality
    val state: String? get() = address?.adminArea
    val country: String? get() = address?.countryName

    fun ageMillis(nowMillis: Long): Long = (nowMillis - timestampMillis).coerceAtLeast(0L)

    fun isStale(nowMillis: Long, maxAgeMillis: Long = STALE_AFTER_MILLIS): Boolean =
        timestampMillis <= 0L || ageMillis(nowMillis) > maxAgeMillis

    /**
     * Quality bucket used purely for the on-screen status indicator. It is derived
     * from the accuracy the platform reported and never from a guessed value.
     */
    fun accuracyQuality(): AccuracyQuality = when {
        accuracyMeters == null -> AccuracyQuality.UNKNOWN
        accuracyMeters <= GOOD_ACCURACY_METERS -> AccuracyQuality.GOOD
        accuracyMeters <= FAIR_ACCURACY_METERS -> AccuracyQuality.FAIR
        else -> AccuracyQuality.POOR
    }

    companion object {
        const val STALE_AFTER_MILLIS = 2 * 60 * 1000L
        const val GOOD_ACCURACY_METERS = 15f
        const val FAIR_ACCURACY_METERS = 50f
    }
}

enum class AccuracyQuality { GOOD, FAIR, POOR, UNKNOWN }
