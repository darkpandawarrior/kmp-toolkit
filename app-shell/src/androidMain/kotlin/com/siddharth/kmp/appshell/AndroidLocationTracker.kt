package com.siddharth.kmp.appshell

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val MIN_UPDATE_INTERVAL_MILLIS = 2_000L
private const val MIN_UPDATE_DISTANCE_METERS = 0f

/**
 * Default Android [LocationTracker], backed by the platform's own [LocationManager] — no Google
 * Play Services dependency. This is what every app-shell consumer gets unconditionally (including
 * a noGms/F-Droid classpath); a consumer that wants the fused provider instead supplies its own
 * `LocationTracker` binding from the `app-shell-location-gms` module, gms-flavor only. See that
 * module's `GmsFusedLocationTracker` kdoc.
 */
class AndroidLocationTracker(
    private val context: Context,
) : LocationTracker {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _updates = MutableSharedFlow<GeoPoint>(extraBufferCapacity = 16)
    override val updates: Flow<GeoPoint> = _updates.asSharedFlow()
    private var listener: LocationListener? = null

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun bestProvider(): String? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (listener != null || !hasPermission()) return
        val provider = bestProvider() ?: return
        val l =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    _updates.tryEmit(location.toGeoPoint())
                }

                override fun onStatusChanged(
                    provider: String?,
                    status: Int,
                    extras: Bundle?,
                ) = Unit

                override fun onProviderEnabled(provider: String) = Unit

                override fun onProviderDisabled(provider: String) = Unit
            }
        listener = l
        try {
            manager.requestLocationUpdates(
                provider,
                MIN_UPDATE_INTERVAL_MILLIS,
                MIN_UPDATE_DISTANCE_METERS,
                l,
                Looper.getMainLooper(),
            )
        } catch (_: SecurityException) {
            listener = null
        }
    }

    override fun stop() {
        listener?.let { manager.removeUpdates(it) }
        listener = null
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(): GeoPoint? {
        if (!hasPermission()) return null
        val provider = bestProvider() ?: return manager.lastKnownGoodLocation()?.toGeoPoint()
        return suspendCancellableCoroutine { cont ->
            var resumed = false
            val l =
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (resumed) return
                        resumed = true
                        manager.removeUpdates(this)
                        cont.resume(location.toGeoPoint())
                    }

                    override fun onStatusChanged(
                        provider: String?,
                        status: Int,
                        extras: Bundle?,
                    ) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }
            try {
                manager.requestSingleUpdate(provider, l, Looper.getMainLooper())
            } catch (_: SecurityException) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation { manager.removeUpdates(l) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun LocationManager.lastKnownGoodLocation(): Location? =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { runCatching { getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude, accuracy, time, speed, bearing.toDouble(), altitude)
}
