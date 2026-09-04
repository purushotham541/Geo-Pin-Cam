package com.letscode.geopincam.domain.model

/**
 * Explicit UI state for the location subsystem. Every branch has a matching
 * message on the camera screen so the user always knows what is happening.
 */
sealed interface LocationStatus {
    /** Location permission has not been granted yet. */
    data object PermissionRequired : LocationStatus

    /** Permission granted but the system location toggle is off. */
    data object ServicesDisabled : LocationStatus

    /** Waiting for the first fix. */
    data object Searching : LocationStatus

    /** A fix is available. [isStale] marks fixes older than the freshness window. */
    data class Available(val location: LocationData, val isStale: Boolean = false) : LocationStatus

    /** Permission and services are fine, but no fix could be obtained. */
    data object Unavailable : LocationStatus

    val locationOrNull: LocationData?
        get() = (this as? Available)?.location
}
