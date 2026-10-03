package com.siddharth.kmp.appshell.gms

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.siddharth.kmp.appshell.GeoPoint
import com.siddharth.kmp.appshell.LocationTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.tasks.await

/**
 * Android [LocationTracker] backed by the fused location provider (Google Play Services).
 *
 * Opt-in only: this class and its `play-services-location` dependency live in this separate
 * module, never in app-shell's own androidMain, so a consumer that never depends on this module
 * (a noGms/F-Droid flavor) carries zero Play Services on its classpath. A consumer that wants
 * fused location (a gms flavor) depends on `app-shell-location-gms` and binds this class in
 * place of app-shell's plain `AndroidLocationTracker` — same DI seam, gms-only wiring.
 *
 * Behavior is unchanged from the fused implementation this module was extracted from: same
 * request cadence (4s interval / 2s min / PRIORITY_HIGH_ACCURACY), same permission handling
 * (a revoked permission is swallowed, not crashed).
 *
 * Callers must hold a location permission before calling [start]/[current]; a revoked permission is
 * swallowed (the stream simply stays empty) rather than crashing. Permission requests are the
 * responsibility of `PermissionsProvider`.
 */
class GmsFusedLocationTracker(
    private val context: Context,
) : LocationTracker {
    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val _updates = MutableSharedFlow<GeoPoint>(extraBufferCapacity = 16)
    override val updates: Flow<GeoPoint> = _updates.asSharedFlow()
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun start() {
        if (callback != null) return
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
                .setMinUpdateIntervalMillis(2_000L)
                .build()
        val cb =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let {
                        _updates.tryEmit(
                            GeoPoint(it.latitude, it.longitude, it.accuracy, it.time, it.speed, it.bearing.toDouble(), it.altitude),
                        )
                    }
                }
            }
        callback = cb
        try {
            client.requestLocationUpdates(request, cb, Looper.getMainLooper())
        } catch (_: SecurityException) {
            callback = null
        }
    }

    override fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    @SuppressLint("MissingPermission")
    override suspend fun current(): GeoPoint? =
        try {
            client
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .await()
                ?.let { GeoPoint(it.latitude, it.longitude, it.accuracy, it.time, it.speed, it.bearing.toDouble(), it.altitude) }
        } catch (_: Exception) {
            null
        }
}
