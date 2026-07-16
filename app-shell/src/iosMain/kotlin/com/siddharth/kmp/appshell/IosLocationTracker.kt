package com.siddharth.kmp.appshell

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

/**
 * iOS location via CoreLocation, counterpart to Android's FusedLocationProvider.
 *
 * Configured for continuous background tracking:
 * - requestWhenInUseAuthorization() first, then escalate to requestAlwaysAuthorization() once
 *   WhenInUse is granted (see [locationDelegate]'s `didChangeAuthorization` callback). Requesting
 *   Always eagerly risks the OS silently downgrading the prompt to WhenInUse-only on some iOS
 *   versions if the user hasn't first seen (and accepted) the WhenInUse prompt.
 * - allowsBackgroundLocationUpdates = true: required for continuous background tracking (the
 *   `location` background mode must also be listed in the app's Info.plist).
 * - pausesLocationUpdatesAutomatically = false: prevents CoreLocation from silently halting delivery
 *   when the device is stationary, which would produce false zero-distance periods.
 * - startMonitoringSignificantLocationChanges(): lets the OS relaunch a terminated/killed app when the
 *   device moves ~500 m.
 *
 * The consuming app's Info.plist needs:
 *   UIBackgroundModes: location
 *   NSLocationAlwaysAndWhenInUseUsageDescription / NSLocationWhenInUseUsageDescription: an
 *     app-specific rationale string for the location prompt.
 */
class IosLocationTracker : LocationTracker {
    private val _updates = MutableSharedFlow<GeoPoint>(replay = 1)
    override val updates: Flow<GeoPoint> = _updates.asSharedFlow()

    private val manager = CLLocationManager()
    private var lastKnown: GeoPoint? = null

    private val locationDelegate =
        object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManager(
                manager: CLLocationManager,
                didUpdateLocations: List<*>,
            ) {
                val location = didUpdateLocations.lastOrNull() as? CLLocation ?: return
                val point = location.toGeoPoint()
                lastKnown = point
                _updates.tryEmit(point)
            }

            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                // Escalate WhenInUse -> Always once the user has granted the initial (less scary)
                // prompt — requesting Always upfront risks a silent OS downgrade (the RN lesson).
                if (manager.authorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse) {
                    manager.requestAlwaysAuthorization()
                }
            }
        }

    init {
        manager.delegate = locationDelegate
        manager.desiredAccuracy = kCLLocationAccuracyBest
        manager.allowsBackgroundLocationUpdates = true
        manager.pausesLocationUpdatesAutomatically = false
        manager.startMonitoringSignificantLocationChanges()
    }

    override suspend fun current(): GeoPoint? = lastKnown ?: manager.location?.toGeoPoint()

    override fun start() {
        manager.requestWhenInUseAuthorization()
        manager.startUpdatingLocation()
    }

    override fun stop() {
        manager.stopUpdatingLocation()
        // Significant-change monitoring stays active (it's the relaunch hook — don't stop it).
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun CLLocation.toGeoPoint(): GeoPoint =
        coordinate.useContents {
            GeoPoint(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = horizontalAccuracy.toFloat(),
                timestampMillis = (timestamp.timeIntervalSince1970 * 1000.0).toLong(),
                // speed/course/altitude are top-level CLLocation properties (not on `coordinate`).
                // CLLocation reports negative speed/course when invalid — passed through as-is;
                // GpsFix mapping (IosTrackingController.toGpsFix) guards the negative case.
                speedMetersPerSecond = speed.toFloat(),
                courseDegrees = course,
                altitudeMeters = altitude,
            )
        }
}
