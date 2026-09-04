package com.letscode.geopincam.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** Small helpers around runtime permissions and the system location toggle. */
object PermissionUtils {

    val LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasCameraPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.CAMERA)

    /** Sound is optional for video; a denial just records silently. */
    fun hasMicrophonePermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.RECORD_AUDIO)

    /** True when either precise or approximate location was granted. */
    fun hasAnyLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** True only when the user granted precise location. */
    fun hasPreciseLocationPermission(context: Context): Boolean =
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)

    /** True when the user allowed location but limited it to an approximate fix. */
    fun hasOnlyApproximateLocation(context: Context): Boolean =
        hasAnyLocationPermission(context) && !hasPreciseLocationPermission(context)

    /** Whether the device-wide location toggle is currently on. */
    fun isLocationEnabled(context: Context): Boolean {
        val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
        return manager != null && LocationManagerCompat.isLocationEnabled(manager)
    }

    /** Intent to this app's detail page, used when a permission is permanently denied. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Intent to the system location settings screen. */
    fun locationSettingsIntent(): Intent =
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

/** Walks up the context chain to the hosting Activity, if there is one. */
fun android.content.Context.findActivity(): android.app.Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}
