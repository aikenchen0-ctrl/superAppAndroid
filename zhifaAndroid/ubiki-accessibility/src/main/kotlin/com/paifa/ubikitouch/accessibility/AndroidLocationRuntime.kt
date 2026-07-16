package com.paifa.ubikitouch.accessibility

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.util.Locale

internal fun currentDeviceLocationState(context: Context): DeviceLocationState {
    if (!hasLocationPermission(context)) {
        return DeviceLocationState(permissionDenied = true, error = "需要授权后才能发送真实位置")
    }
    val option = lastKnownLocationOption(context)
    return if (option != null) DeviceLocationState(option = option)
    else DeviceLocationState(error = "还没有可用的手机定位，请点重新定位")
}

internal fun requestCurrentDeviceLocation(context: Context, onResult: (DeviceLocationState) -> Unit) {
    if (!hasLocationPermission(context)) {
        onResult(DeviceLocationState(permissionDenied = true, error = "需要授权后才能发送真实位置"))
        return
    }
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onResult(DeviceLocationState(error = "系统定位服务不可用"))
        return
    }
    val providers = realLocationProviders(locationManager)
    val latest = providers.mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }
    if (latest != null) onResult(DeviceLocationState(option = latest.toAppLocationOption()))
    if (providers.isEmpty()) {
        onResult(if (latest == null) DeviceLocationState(error = "请先打开系统定位开关") else DeviceLocationState(option = latest.toAppLocationOption()))
        return
    }
    val mainHandler = Handler(Looper.getMainLooper())
    var delivered = latest != null
    var listener: LocationListener? = null
    fun finish(state: DeviceLocationState) {
        if (delivered && state.option == null) return
        delivered = true
        listener?.let { runCatching { locationManager.removeUpdates(it) } }
        onResult(state)
    }
    listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = finish(DeviceLocationState(option = location.toAppLocationOption()))
        override fun onProviderDisabled(provider: String) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    providers.firstOrNull { provider -> runCatching { locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper()); true }.getOrDefault(false) }
    mainHandler.postDelayed({ if (!delivered) finish(DeviceLocationState(error = "定位超时，请确认系统定位已开启")) }, LOCATION_REQUEST_TIMEOUT_MS)
}

internal fun hasLocationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun realLocationProviders(locationManager: LocationManager): List<String> = listOf(
    LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER
).filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }

private fun lastKnownLocationOption(context: Context): AppLocationOption? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    return realLocationProviders(manager).mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }?.toAppLocationOption()
}

private fun Location.toAppLocationOption(): AppLocationOption {
    val latitudeText = String.format(Locale.US, "%.6f", latitude)
    val longitudeText = String.format(Locale.US, "%.6f", longitude)
    val accuracyText = if (hasAccuracy()) ", 精度约 ${accuracy.toInt().coerceAtLeast(1)} 米" else ""
    return AppLocationOption("我的当前位置", "$latitudeText, $longitudeText$accuracyText", latitude, longitude, "geo:$latitudeText,$longitudeText?q=$latitudeText,$longitudeText")
}

private const val LOCATION_REQUEST_TIMEOUT_MS = 8_000L
