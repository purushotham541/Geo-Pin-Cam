package com.letscode.geopincam.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.letscode.geopincam.GeoPinCamApp
import com.letscode.geopincam.data.preferences.SettingsRepository
import com.letscode.geopincam.domain.model.AddressDetail
import com.letscode.geopincam.domain.model.AppSettings
import com.letscode.geopincam.domain.model.CameraFacing
import com.letscode.geopincam.domain.model.CoordinateFormat
import com.letscode.geopincam.domain.model.DateFormatStyle
import com.letscode.geopincam.domain.model.FlashMode
import com.letscode.geopincam.domain.model.PhotoQuality
import com.letscode.geopincam.domain.model.StampFontSize
import com.letscode.geopincam.domain.model.StampPosition
import com.letscode.geopincam.domain.model.StampTextAlignment
import com.letscode.geopincam.domain.model.TimeFormatStyle
import com.letscode.geopincam.domain.model.VideoQuality
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes the persisted settings and forwards every change to DataStore.
 *
 * Each setter is a one-liner on purpose: the screen stays declarative and the
 * repository remains the only place that knows about storage.
 */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AppSettings()
    )

    fun setPhotoQuality(quality: PhotoQuality) = update { setPhotoQuality(quality) }

    fun setVideoQuality(quality: VideoQuality) = update { setVideoQuality(quality) }

    fun setFlashMode(mode: FlashMode) = update { setFlashMode(mode) }

    fun setDefaultFacing(facing: CameraFacing) = update { setDefaultFacing(facing) }

    fun setStampEnabled(enabled: Boolean) = update { setStampEnabled(enabled) }

    fun setStampPosition(position: StampPosition) = update { setStampPosition(position) }

    fun setFontSize(size: StampFontSize) = update { setFontSize(size) }

    fun setTextAlignment(alignment: StampTextAlignment) = update { setTextAlignment(alignment) }

    fun setBackgroundOpacity(opacity: Float) = update { setBackgroundOpacity(opacity) }

    fun setShowAddress(show: Boolean) = update { setShowAddress(show) }

    fun setShowPlaceName(show: Boolean) = update { setShowPlaceName(show) }

    fun setShowCountryFlag(show: Boolean) = update { setShowCountryFlag(show) }

    fun setShowMapThumbnail(show: Boolean) = update { setShowMapThumbnail(show) }

    fun setShowCoordinates(show: Boolean) = update { setShowCoordinates(show) }

    fun setShowDate(show: Boolean) = update { setShowDate(show) }

    fun setShowTime(show: Boolean) = update { setShowTime(show) }

    fun setShowAccuracy(show: Boolean) = update { setShowAccuracy(show) }

    fun setShowAltitude(show: Boolean) = update { setShowAltitude(show) }

    fun setShowSpeed(show: Boolean) = update { setShowSpeed(show) }

    fun setShowBearing(show: Boolean) = update { setShowBearing(show) }

    fun setShowAppName(show: Boolean) = update { setShowAppName(show) }

    fun setCoordinateFormat(format: CoordinateFormat) = update { setCoordinateFormat(format) }

    fun setAddressDetail(detail: AddressDetail) = update { setAddressDetail(detail) }

    fun setDateFormat(style: DateFormatStyle) = update { setDateFormat(style) }

    fun setTimeFormat(style: TimeFormatStyle) = update { setTimeFormat(style) }

    fun setSaveStampedPhoto(save: Boolean) = update { setSaveStampedPhoto(save) }

    fun setSaveOriginalPhoto(save: Boolean) = update { setSaveOriginalPhoto(save) }

    private fun update(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.block() }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as GeoPinCamApp
                SettingsViewModel(app.container.settingsRepository)
            }
        }
    }
}
