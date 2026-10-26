package com.example.dinex

/** Last known position used to geotag a new expense. */
data class DinexLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String = "",
) {
    val valid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}

/**
 * Android registers the device location here so any new expense can be tagged
 * without passing a Context through every screen. Null on iOS/web or when the
 * user has not granted location permission.
 */
object DinexLocationProvider {
    var current: (() -> DinexLocation?)? = null
}
