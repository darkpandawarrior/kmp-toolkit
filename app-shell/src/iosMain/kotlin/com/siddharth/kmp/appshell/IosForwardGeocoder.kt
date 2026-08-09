package com.siddharth.kmp.appshell

import dev.jordond.compass.geocoder.Geocoder

/**
 * iOS [ForwardGeocoder] via Compass's mobile geocoder, itself `CLGeocoder` underneath — the same OS
 * geocoder [IosLocationNameResolver] wraps for the reverse direction. Needs no context, same as
 * [IosLocationNameResolver].
 *
 * Every failure path — no result or any exception — resolves to an empty list, matching
 * [LocationNameResolver.resolve]'s never-throws contract.
 */
class IosForwardGeocoder : ForwardGeocoder {
    private val geocoder = Geocoder()

    override suspend fun forward(address: String): List<GeoCoordinates> =
        geocoder
            .forward(address)
            .getOrNull()
            ?.map { GeoCoordinates(it.latitude, it.longitude) }
            .orEmpty()
}
