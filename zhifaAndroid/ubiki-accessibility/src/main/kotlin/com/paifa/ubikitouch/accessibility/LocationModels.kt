package com.paifa.ubikitouch.accessibility

internal data class AppLocationOption(
    val title: String,
    val address: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geoUri: String? = null
)
