package com.letscode.geopincam.data.location

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.letscode.geopincam.domain.model.LocationData
import com.letscode.geopincam.domain.model.LocationStatus
import com.letscode.geopincam.utils.PermissionUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single source of truth for the device's position.
 *
 * Exposes a status stream for the live camera overlay and a one-shot lookup used
 * at capture time. Nothing here ever throws at the caller: every failure path
 * turns into a [LocationStatus] the UI knows how to render.
 */
class LocationRepository(
    context: Context,
    private val geocoder: ReverseGeocoder
) {

    private val appContext = context.applicationContext
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    private val _lastFix = MutableStateFlow<LocationData?>(null)

    /** The most recent fix seen in this process, used to seed the capture path. */
    val lastFix: StateFlow<LocationData?> = _lastFix.asStateFlow()

    /** Cell key and time of the last reverse geocoding attempt, to avoid retry storms. */
    private var lastGeocodeCell: String? = null
    private var lastGeocodeAttemptMillis = 0L

    /**
     * Emits the current location status. Re-collect this whenever the location
     * permission changes; the flow itself reacts to the system location toggle.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeStatus(): Flow<LocationStatus> {
        if (!PermissionUtils.hasAnyLocationPermission(appContext)) {
            return flowOf(LocationStatus.PermissionRequired)
        }
        return locationServicesEnabled()
            .flatMapLatest { enabled ->
                if (enabled) fixStream() else flowOf(LocationStatus.ServicesDisabled)
            }
            .distinctUntilChanged()
    }

    /**
     * The fix to stamp a photo with, chosen so the shutter never waits: while the
     * camera screen is open the streamed fix is already current, so it is used as
     * is. Only when there is nothing fresh does this ask the provider, and then on
     * a short budget. Returns null when no position can be obtained, which is a
     * normal, non-fatal outcome.
     *
     * The address is whatever the geocoder has already cached. A network lookup is
     * never awaited here; it is the same address the live preview was showing.
     */
    suspend fun fixForCapture(): LocationData? {
        if (!PermissionUtils.hasAnyLocationPermission(appContext)) return null

        val cached = _lastFix.value
        val best = if (
            cached != null && !cached.isStale(System.currentTimeMillis(), FRESH_ENOUGH_MILLIS)
        ) {
            cached
        } else {
            withTimeoutOrNull(CAPTURE_FIX_TIMEOUT_MILLIS) { requestCurrentLocation() } ?: cached
        }
        return best?.withCachedAddress()
    }

    private fun LocationData.withCachedAddress(): LocationData =
        if (address != null) this else copy(address = geocoder.cached(latitude, longitude))

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrentLocation(): LocationData? = try {
        val request = CurrentLocationRequest.Builder()
            .setPriority(priorityForGrantedPermission())
            .setMaxUpdateAgeMillis(FRESH_ENOUGH_MILLIS)
            .build()
        fusedClient.getCurrentLocation(request, null).await()?.toLocationData()
            ?.also { _lastFix.value = it }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.w(TAG, "Current location request failed", error)
        null
    }

    /** Streams fixes from the fused provider while the returned flow is collected. */
    @SuppressLint("MissingPermission")
    private fun fixStream(): Flow<LocationStatus> = callbackFlow {
        val seed = _lastFix.value
        trySend(
            if (seed != null) {
                LocationStatus.Available(seed, seed.isStale(System.currentTimeMillis()))
            } else {
                LocationStatus.Searching
            }
        )

        // Declared before the callback that calls it, as local functions must be.
        fun maybeResolveAddress(fix: LocationData) {
            val cell = geocoder.cellKey(fix.latitude, fix.longitude)
            val now = SystemClock.elapsedRealtime()
            val sameCellRecently = cell == lastGeocodeCell &&
                now - lastGeocodeAttemptMillis < GEOCODE_RETRY_INTERVAL_MILLIS
            if (fix.address != null || sameCellRecently) return

            lastGeocodeCell = cell
            lastGeocodeAttemptMillis = now
            launch {
                val address = geocoder.resolve(fix.latitude, fix.longitude) ?: return@launch
                val current = _lastFix.value ?: return@launch
                if (geocoder.cellKey(current.latitude, current.longitude) != cell) return@launch
                val updated = current.copy(address = address)
                _lastFix.value = updated
                trySend(LocationStatus.Available(updated))
            }
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val fix = result.lastLocation?.toLocationData() ?: return
                val enriched = fix.copy(address = geocoder.cached(fix.latitude, fix.longitude))
                _lastFix.value = enriched
                trySend(LocationStatus.Available(enriched))
                maybeResolveAddress(enriched)
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable && _lastFix.value == null) {
                    trySend(LocationStatus.Unavailable)
                }
            }
        }

        val request = LocationRequest.Builder(
            priorityForGrantedPermission(),
            UPDATE_INTERVAL_MILLIS
        )
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MILLIS)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, callback, appContext.mainLooper)
        } catch (error: SecurityException) {
            // Permission was revoked between the check and this call.
            trySend(LocationStatus.PermissionRequired)
        }

        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    /** Emits whenever the system location toggle changes, starting with its current value. */
    private fun locationServicesEnabled(): Flow<Boolean> = callbackFlow {
        trySend(PermissionUtils.isLocationEnabled(appContext))

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySendBlocking(PermissionUtils.isLocationEnabled(appContext))
            }
        }
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        // PROVIDERS_CHANGED is a protected system broadcast; the flag is required from API 33.
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )

        awaitClose { runCatching { appContext.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()

    /**
     * Asking for high accuracy when only approximate location was granted makes the
     * platform silently coarsen the result, so match the request to the grant.
     */
    private fun priorityForGrantedPermission(): Int =
        if (PermissionUtils.hasPreciseLocationPermission(appContext)) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

    private fun Location.toLocationData(): LocationData = LocationData(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        altitudeMeters = if (hasAltitude()) altitude else null,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        bearingDegrees = if (hasBearing()) bearing else null,
        timestampMillis = time,
        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isMock
        } else {
            @Suppress("DEPRECATION")
            isFromMockProvider
        }
    )

    private companion object {
        const val TAG = "LocationRepository"
        const val UPDATE_INTERVAL_MILLIS = 2_000L
        const val FASTEST_INTERVAL_MILLIS = 1_000L
        const val FRESH_ENOUGH_MILLIS = 30_000L

        /** Deliberately short: a capture must not stall waiting for the satellites. */
        const val CAPTURE_FIX_TIMEOUT_MILLIS = 1_200L
        const val GEOCODE_RETRY_INTERVAL_MILLIS = 60_000L
    }
}
