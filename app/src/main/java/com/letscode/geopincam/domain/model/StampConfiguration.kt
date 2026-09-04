package com.letscode.geopincam.domain.model

/** Where the stamp block is drawn on the photograph. */
enum class StampPosition(val key: String) {
    TOP_LEFT("top_left"),
    TOP_CENTER("top_center"),
    TOP_RIGHT("top_right"),
    BOTTOM_LEFT("bottom_left"),
    BOTTOM_CENTER("bottom_center"),
    BOTTOM_RIGHT("bottom_right");

    val isTop: Boolean get() = this == TOP_LEFT || this == TOP_CENTER || this == TOP_RIGHT

    companion object {
        fun fromKey(key: String?): StampPosition =
            entries.firstOrNull { it.key == key } ?: BOTTOM_LEFT
    }
}

/** How the text inside the stamp block is aligned. */
enum class StampTextAlignment(val key: String) {
    START("start"),
    CENTER("center"),
    END("end");

    companion object {
        fun fromKey(key: String?): StampTextAlignment =
            entries.firstOrNull { it.key == key } ?: START
    }
}

/**
 * Stamp text size, expressed as a fraction of the photo's shorter edge so the
 * stamp keeps the same relative size on every resolution.
 *
 * Sized to read at arm's length like the stamp in a dedicated GPS camera app,
 * where the geo-tag block is a deliberate part of the shot rather than a footnote.
 */
enum class StampFontSize(val key: String, val scale: Float) {
    SMALL("small", 0.034f),
    MEDIUM("medium", 0.043f),
    LARGE("large", 0.054f),
    EXTRA_LARGE("extra_large", 0.066f);

    companion object {
        fun fromKey(key: String?): StampFontSize =
            entries.firstOrNull { it.key == key } ?: MEDIUM
    }
}

/** How a coordinate pair is written on the stamp. */
enum class CoordinateFormat(val key: String) {
    /** "Lat 17.594678° Long 78.441647°" - labelled and precise to six places. */
    LABELLED("labelled"),

    /** "17.3850° N, 78.4867° E" */
    DECIMAL("decimal"),

    /** "17°23'06\"N, 78°29'12\"E" */
    DMS("dms");

    companion object {
        fun fromKey(key: String?): CoordinateFormat =
            entries.firstOrNull { it.key == key } ?: LABELLED
    }
}

enum class DateFormatStyle(val key: String, val pattern: String) {
    /** "Monday, 31/08/2026" */
    WEEKDAY_FULL("weekday_full", "EEEE, dd/MM/yyyy"),
    DAY_MONTH_YEAR("dd_mmm_yyyy", "dd MMM yyyy"),
    NUMERIC_DAY_FIRST("dd_mm_yyyy", "dd/MM/yyyy"),
    NUMERIC_MONTH_FIRST("mm_dd_yyyy", "MM/dd/yyyy"),
    ISO("iso", "yyyy-MM-dd");

    companion object {
        fun fromKey(key: String?): DateFormatStyle =
            entries.firstOrNull { it.key == key } ?: WEEKDAY_FULL
    }
}

enum class TimeFormatStyle(val key: String, val pattern: String) {
    /** "09:19 AM GMT +05:30" */
    HOUR_12_WITH_ZONE("hour_12_zone", "hh:mm a 'GMT' XXX"),
    HOUR_12("hour_12", "hh:mm a"),
    HOUR_12_WITH_SECONDS("hour_12_seconds", "hh:mm:ss a"),
    HOUR_24("hour_24", "HH:mm"),
    HOUR_24_WITH_SECONDS("hour_24_seconds", "HH:mm:ss"),
    HOUR_24_WITH_ZONE("hour_24_zone", "HH:mm 'GMT' XXX");

    companion object {
        fun fromKey(key: String?): TimeFormatStyle =
            entries.firstOrNull { it.key == key } ?: HOUR_12_WITH_ZONE
    }
}

/**
 * Everything the user can turn on, off or tune about the stamp overlay.
 */
data class StampConfiguration(
    val stampEnabled: Boolean = true,
    val showAddress: Boolean = true,

    /** The exact place line, such as a building or business name plus its street. */
    val showPlaceName: Boolean = true,

    /** The flag emoji beside the country on the headline. */
    val showCountryFlag: Boolean = true,

    /** The map tile drawn to the left of the text panel. */
    val showMapThumbnail: Boolean = true,
    val showCoordinates: Boolean = true,
    val showDate: Boolean = true,
    val showTime: Boolean = true,
    val showAccuracy: Boolean = true,
    val showAltitude: Boolean = true,
    val showSpeed: Boolean = false,
    val showBearing: Boolean = false,
    val showAppName: Boolean = true,
    val position: StampPosition = StampPosition.BOTTOM_LEFT,
    val textAlignment: StampTextAlignment = StampTextAlignment.START,
    val fontSize: StampFontSize = StampFontSize.MEDIUM,
    /** Opacity of the panel behind the text, 0f transparent to 1f opaque. */
    val backgroundOpacity: Float = DEFAULT_BACKGROUND_OPACITY,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.LABELLED,
    val dateFormat: DateFormatStyle = DateFormatStyle.WEEKDAY_FULL,
    val timeFormat: TimeFormatStyle = TimeFormatStyle.HOUR_12_WITH_ZONE
) {
    /** True when every information row has been switched off by the user. */
    val hasNoVisibleFields: Boolean
        get() = !showAddress && !showPlaceName && !showCoordinates && !showDate && !showTime &&
            !showAccuracy && !showAltitude && !showSpeed && !showBearing && !showAppName

    companion object {
        const val DEFAULT_BACKGROUND_OPACITY = 0.55f
        const val MIN_BACKGROUND_OPACITY = 0f
        const val MAX_BACKGROUND_OPACITY = 0.95f
    }
}
