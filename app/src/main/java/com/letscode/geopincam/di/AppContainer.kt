package com.letscode.geopincam.di

import android.content.Context
import com.letscode.geopincam.data.image.PhotoStampProcessor
import com.letscode.geopincam.data.location.LocationRepository
import com.letscode.geopincam.data.location.ReverseGeocoder
import com.letscode.geopincam.data.map.MapTileProvider
import com.letscode.geopincam.data.preferences.SettingsRepository
import com.letscode.geopincam.data.storage.MediaStoreGateway
import com.letscode.geopincam.data.storage.PhotoRepository
import com.letscode.geopincam.data.storage.VideoStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand written dependency container.
 *
 * The graph is small and entirely process scoped, so a container built in the
 * Application beats pulling in an annotation processor for the same result.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    private val geocoder: ReverseGeocoder by lazy { ReverseGeocoder(appContext) }

    val locationRepository: LocationRepository by lazy {
        LocationRepository(appContext, geocoder)
    }

    val mapTileProvider: MapTileProvider by lazy { MapTileProvider() }

    private val stampProcessor: PhotoStampProcessor by lazy { PhotoStampProcessor() }

    private val mediaStoreGateway: MediaStoreGateway by lazy { MediaStoreGateway(appContext) }

    val photoRepository: PhotoRepository by lazy {
        PhotoRepository(appContext, mediaStoreGateway, stampProcessor)
    }

    val videoStorage: VideoStorage by lazy { VideoStorage(mediaStoreGateway) }

    /**
     * Stamping and saving a capture outlives the screen that started it: the
     * shutter is re-armed the moment the sensor is done, so this work must not be
     * tied to a ViewModel that the user can navigate away from a beat later.
     */
    val captureScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
