package com.paifa.ubikitouch.accessibility

internal data class AppLocationOption(
    val title: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geoUri: String? = null
)

internal data class DeviceLocationState(
    val option: AppLocationOption? = null,
    val loading: Boolean = false,
    val permissionDenied: Boolean = false,
    val error: String? = null
)
