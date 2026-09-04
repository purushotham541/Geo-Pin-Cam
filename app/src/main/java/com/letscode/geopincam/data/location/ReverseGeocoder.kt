package com.letscode.geopincam.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.annotation.RequiresApi
import com.letscode.geopincam.domain.model.PlaceAddress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.round

/**
 * Turns coordinates into a human readable address.
 *
 * Reverse geocoding is comparatively expensive and usually needs the network, so
 * results are cached per ~110 m cell and a lookup is only attempted when the
 * device has actually moved out of the cell it was last resolved in. A failure
 * (offline, no geocoder backend, no match) resolves to null and never propagates
 * as an error: the app still stamps coordinates without an address.
 */
class ReverseGeocoder(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val locale: Locale = Locale.getDefault()
) {

    private val appContext = context.applicationContext
    private val cache = LruCache<String, PlaceAddress>(CACHE_ENTRIES)

    /** Cached result for these coordinates, if one was already resolved nearby. */
    fun cached(latitude: Double, longitude: Double): PlaceAddress? = cache.get(cellKey(latitude, longitude))

    /**
     * Returns the address for the given point, using the cache when possible.
     * Returns null when no address could be resolved for any reason.
     */
    suspend fun resolve(latitude: Double, longitude: Double): PlaceAddress? {
        val key = cellKey(latitude, longitude)
        cache.get(key)?.let { return it }

        if (!Geocoder.isPresent()) return null

        val address = try {
            lookup(latitude, longitude)
        } catch (error: IOException) {
            // Offline or the backend service is unreachable.
            Log.d(TAG, "Reverse geocoding unavailable: ${error.message}")
            null
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Invalid coordinates for reverse geocoding", error)
            null
        }

        return address?.toPlaceAddress()?.takeIf { it.hasContent }?.also { cache.put(key, it) }
    }

    private suspend fun lookup(latitude: Double, longitude: Double): Address? {
        val geocoder = Geocoder(appContext, locale)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lookupAsync(geocoder, latitude, longitude)
        } else {
            withContext(ioDispatcher) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, MAX_RESULTS)?.firstOrNull()
            }
        }
    }

    /**
     * API 33 replaced the blocking call with a listener. The listener is invoked
     * on a binder thread and, defensively, only the first callback is honoured.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun lookupAsync(
        geocoder: Geocoder,
        latitude: Double,
        longitude: Double
    ): Address? = suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)
        val listener = object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                if (resumed.compareAndSet(false, true)) {
                    continuation.resume(addresses.firstOrNull())
                }
            }

            override fun onError(errorMessage: String?) {
                Log.d(TAG, "Reverse geocoding failed: $errorMessage")
                if (resumed.compareAndSet(false, true)) continuation.resume(null)
            }
        }
        geocoder.getFromLocation(latitude, longitude, MAX_RESULTS, listener)
    }

    private fun Address.toPlaceAddress(): PlaceAddress = PlaceAddress(
        featureName = featureName.clean(),
        premises = premises.clean(),
        subThoroughfare = subThoroughfare.clean(),
        thoroughfare = thoroughfare.clean(),
        subLocality = subLocality.clean(),
        locality = (locality ?: subAdminArea).clean(),
        subAdminArea = subAdminArea.clean(),
        adminArea = adminArea.clean(),
        postalCode = postalCode.clean(),
        countryName = countryName.clean(),
        countryCode = countryCode.clean(),
        fullAddressLine = fullLine()
    )

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * The geocoder's own composed address. Line 0 is the specific one, carrying
     * premises and business names such as "Canteen, Mlr Institute Of Technology",
     * which is exactly the "exact place" the stamp wants.
     */
    private fun Address.fullLine(): String? {
        if (maxAddressLineIndex < 0) return null
        return (0..maxAddressLineIndex)
            .mapNotNull { getAddressLine(it)?.trim()?.takeIf { line -> line.isNotEmpty() } }
            .distinct()
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
    }

    /** Snaps coordinates to a grid so nearby fixes reuse the same cached address. */
    fun cellKey(latitude: Double, longitude: Double): String {
        val lat = round(latitude * CELL_FACTOR) / CELL_FACTOR
        val lon = round(longitude * CELL_FACTOR) / CELL_FACTOR
        return "$lat,$lon"
    }

    private companion object {
        const val TAG = "ReverseGeocoder"
        const val MAX_RESULTS = 1
        const val CACHE_ENTRIES = 64

        /** 1000 -> three decimal places, roughly a 110 m cell. */
        const val CELL_FACTOR = 1000.0
    }
}
