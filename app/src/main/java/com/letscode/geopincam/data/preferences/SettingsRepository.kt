package com.letscode.geopincam.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.letscode.geopincam.domain.model.AddressDetail
import com.letscode.geopincam.domain.model.AppSettings
import com.letscode.geopincam.domain.model.CameraFacing
import com.letscode.geopincam.domain.model.CameraSettings
import com.letscode.geopincam.domain.model.CaptureMode
import com.letscode.geopincam.domain.model.CoordinateFormat
import com.letscode.geopincam.domain.model.DateFormatStyle
import com.letscode.geopincam.domain.model.FlashMode
import com.letscode.geopincam.domain.model.PhotoQuality
import com.letscode.geopincam.domain.model.StampConfiguration
import com.letscode.geopincam.domain.model.StampFontSize
import com.letscode.geopincam.domain.model.StampPosition
import com.letscode.geopincam.domain.model.StampTextAlignment
import com.letscode.geopincam.domain.model.StorageSettings
import com.letscode.geopincam.domain.model.TimeFormatStyle
import com.letscode.geopincam.domain.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DATA_STORE_NAME = "geopincam_settings"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DATA_STORE_NAME
)

/**
 * Reads and writes every user preference through DataStore.
 *
 * Reads fall back to the defaults declared on the model classes, so a missing or
 * corrupted preferences file degrades to defaults instead of crashing a screen.
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toAppSettings() }

    suspend fun setStampEnabled(enabled: Boolean) = put(Keys.STAMP_ENABLED, enabled)

    suspend fun setShowAddress(show: Boolean) = put(Keys.SHOW_ADDRESS, show)

    suspend fun setShowPlaceName(show: Boolean) = put(Keys.SHOW_PLACE_NAME, show)

    suspend fun setShowCountryFlag(show: Boolean) = put(Keys.SHOW_COUNTRY_FLAG, show)

    suspend fun setShowMapThumbnail(show: Boolean) = put(Keys.SHOW_MAP_THUMBNAIL, show)

    suspend fun setShowCoordinates(show: Boolean) = put(Keys.SHOW_COORDINATES, show)

    suspend fun setShowDate(show: Boolean) = put(Keys.SHOW_DATE, show)

    suspend fun setShowTime(show: Boolean) = put(Keys.SHOW_TIME, show)

    suspend fun setShowAccuracy(show: Boolean) = put(Keys.SHOW_ACCURACY, show)

    suspend fun setShowAltitude(show: Boolean) = put(Keys.SHOW_ALTITUDE, show)

    suspend fun setShowSpeed(show: Boolean) = put(Keys.SHOW_SPEED, show)

    suspend fun setShowBearing(show: Boolean) = put(Keys.SHOW_BEARING, show)

    suspend fun setShowAppName(show: Boolean) = put(Keys.SHOW_APP_NAME, show)

    suspend fun setStampPosition(position: StampPosition) = put(Keys.POSITION, position.key)

    suspend fun setTextAlignment(alignment: StampTextAlignment) =
        put(Keys.TEXT_ALIGNMENT, alignment.key)

    suspend fun setFontSize(fontSize: StampFontSize) = put(Keys.FONT_SIZE, fontSize.key)

    suspend fun setBackgroundOpacity(opacity: Float) = put(
        Keys.BACKGROUND_OPACITY,
        opacity.coerceIn(
            StampConfiguration.MIN_BACKGROUND_OPACITY,
            StampConfiguration.MAX_BACKGROUND_OPACITY
        )
    )

    suspend fun setCoordinateFormat(format: CoordinateFormat) =
        put(Keys.COORDINATE_FORMAT, format.key)

    suspend fun setDateFormat(style: DateFormatStyle) = put(Keys.DATE_FORMAT, style.key)

    suspend fun setTimeFormat(style: TimeFormatStyle) = put(Keys.TIME_FORMAT, style.key)

    suspend fun setAddressDetail(detail: AddressDetail) = put(Keys.ADDRESS_DETAIL, detail.key)

    suspend fun setPhotoQuality(quality: PhotoQuality) = put(Keys.PHOTO_QUALITY, quality.key)

    suspend fun setVideoQuality(quality: VideoQuality) = put(Keys.VIDEO_QUALITY, quality.key)

    /** Doubles as "last used mode", which the camera screen restores on launch. */
    suspend fun setCaptureMode(mode: CaptureMode) = put(Keys.CAPTURE_MODE, mode.key)

    suspend fun setFlashMode(mode: FlashMode) = put(Keys.FLASH_MODE, mode.key)

    /** Doubles as "last selected camera", which the camera screen restores on launch. */
    suspend fun setDefaultFacing(facing: CameraFacing) = put(Keys.CAMERA_FACING, facing.key)

    suspend fun setSaveStampedPhoto(save: Boolean) = put(Keys.SAVE_STAMPED, save)

    suspend fun setSaveOriginalPhoto(save: Boolean) = put(Keys.SAVE_ORIGINAL, save)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun Preferences.toAppSettings(): AppSettings {
        val stampDefaults = StampConfiguration()
        val storageDefaults = StorageSettings()
        return AppSettings(
            stamp = StampConfiguration(
                stampEnabled = this[Keys.STAMP_ENABLED] ?: stampDefaults.stampEnabled,
                showAddress = this[Keys.SHOW_ADDRESS] ?: stampDefaults.showAddress,
                showPlaceName = this[Keys.SHOW_PLACE_NAME] ?: stampDefaults.showPlaceName,
                showCountryFlag = this[Keys.SHOW_COUNTRY_FLAG] ?: stampDefaults.showCountryFlag,
                showMapThumbnail = this[Keys.SHOW_MAP_THUMBNAIL]
                    ?: stampDefaults.showMapThumbnail,
                showCoordinates = this[Keys.SHOW_COORDINATES] ?: stampDefaults.showCoordinates,
                showDate = this[Keys.SHOW_DATE] ?: stampDefaults.showDate,
                showTime = this[Keys.SHOW_TIME] ?: stampDefaults.showTime,
                showAccuracy = this[Keys.SHOW_ACCURACY] ?: stampDefaults.showAccuracy,
                showAltitude = this[Keys.SHOW_ALTITUDE] ?: stampDefaults.showAltitude,
                showSpeed = this[Keys.SHOW_SPEED] ?: stampDefaults.showSpeed,
                showBearing = this[Keys.SHOW_BEARING] ?: stampDefaults.showBearing,
                showAppName = this[Keys.SHOW_APP_NAME] ?: stampDefaults.showAppName,
                position = StampPosition.fromKey(this[Keys.POSITION]),
                textAlignment = StampTextAlignment.fromKey(this[Keys.TEXT_ALIGNMENT]),
                fontSize = StampFontSize.fromKey(this[Keys.FONT_SIZE]),
                backgroundOpacity = this[Keys.BACKGROUND_OPACITY]
                    ?: stampDefaults.backgroundOpacity,
                coordinateFormat = CoordinateFormat.fromKey(this[Keys.COORDINATE_FORMAT]),
                dateFormat = DateFormatStyle.fromKey(this[Keys.DATE_FORMAT]),
                timeFormat = TimeFormatStyle.fromKey(this[Keys.TIME_FORMAT])
            ),
            camera = CameraSettings(
                photoQuality = PhotoQuality.fromKey(this[Keys.PHOTO_QUALITY]),
                videoQuality = VideoQuality.fromKey(this[Keys.VIDEO_QUALITY]),
                flashMode = FlashMode.fromKey(this[Keys.FLASH_MODE]),
                defaultFacing = CameraFacing.fromKey(this[Keys.CAMERA_FACING]),
                captureMode = CaptureMode.fromKey(this[Keys.CAPTURE_MODE])
            ),
            storage = StorageSettings(
                saveStampedPhoto = this[Keys.SAVE_STAMPED] ?: storageDefaults.saveStampedPhoto,
                saveOriginalPhoto = this[Keys.SAVE_ORIGINAL] ?: storageDefaults.saveOriginalPhoto
            ),
            addressDetail = AddressDetail.fromKey(this[Keys.ADDRESS_DETAIL])
        )
    }

    private object Keys {
        val STAMP_ENABLED = booleanPreferencesKey("stamp_enabled")
        val SHOW_ADDRESS = booleanPreferencesKey("show_address")
        val SHOW_PLACE_NAME = booleanPreferencesKey("show_place_name")
        val SHOW_COUNTRY_FLAG = booleanPreferencesKey("show_country_flag")
        val SHOW_MAP_THUMBNAIL = booleanPreferencesKey("show_map_thumbnail")
        val SHOW_COORDINATES = booleanPreferencesKey("show_coordinates")
        val SHOW_DATE = booleanPreferencesKey("show_date")
        val SHOW_TIME = booleanPreferencesKey("show_time")
        val SHOW_ACCURACY = booleanPreferencesKey("show_accuracy")
        val SHOW_ALTITUDE = booleanPreferencesKey("show_altitude")
        val SHOW_SPEED = booleanPreferencesKey("show_speed")
        val SHOW_BEARING = booleanPreferencesKey("show_bearing")
        val SHOW_APP_NAME = booleanPreferencesKey("show_app_name")
        val POSITION = stringPreferencesKey("stamp_position")
        val TEXT_ALIGNMENT = stringPreferencesKey("stamp_text_alignment")
        val FONT_SIZE = stringPreferencesKey("stamp_font_size")
        val BACKGROUND_OPACITY = floatPreferencesKey("stamp_background_opacity")
        val COORDINATE_FORMAT = stringPreferencesKey("coordinate_format")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val ADDRESS_DETAIL = stringPreferencesKey("address_detail")
        val PHOTO_QUALITY = stringPreferencesKey("photo_quality")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val CAPTURE_MODE = stringPreferencesKey("capture_mode")
        val FLASH_MODE = stringPreferencesKey("flash_mode")
        val CAMERA_FACING = stringPreferencesKey("camera_facing")
        val SAVE_STAMPED = booleanPreferencesKey("save_stamped_photo")
        val SAVE_ORIGINAL = booleanPreferencesKey("save_original_photo")
    }
}
