package com.siddharth.kmp.appshell

import dev.jordond.compass.geocoder.Geocoder

/**
 * Android [ForwardGeocoder] via Compass's mobile geocoder, itself `android.location.Geocoder`
 * underneath — the same OS geocoder [AndroidLocationNameResolver] wraps for the reverse direction.
 *
 * Compass resolves its Android application [android.content.Context] internally via an AndroidX
 * Startup initializer (`ContextProvider`, in `compass-tools-android`), so unlike
 * [AndroidLocationNameResolver] this needs no [android.content.Context] constructor param.
 *
 * Every failure path — no geocoder backend, no result, or any exception — resolves to an empty
 * list, matching [LocationNameResolver.resolve]'s never-throws contract.
 */
class AndroidForwardGeocoder : ForwardGeocoder {
    private val geocoder = Geocoder()

    override suspend fun forward(address: String): List<GeoCoordinates> =
        geocoder
            .forward(address)
            .getOrNull()
            ?.map { GeoCoordinates(it.latitude, it.longitude) }
            .orEmpty()
}
