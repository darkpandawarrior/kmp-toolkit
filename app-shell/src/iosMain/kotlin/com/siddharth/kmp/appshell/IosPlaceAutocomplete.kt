package com.siddharth.kmp.appshell

import dev.jordond.compass.Place
import dev.jordond.compass.autocomplete.Autocomplete

/**
 * iOS [PlaceAutocomplete] via Compass's mobile autocomplete — under the hood it matches partial
 * input against the same `CLGeocoder` [IosForwardGeocoder] uses, not a separate typeahead service,
 * so it needs no API key. Same context-free construction as [IosForwardGeocoder].
 *
 * Every failure path resolves to an empty list; a matched [Place] with no usable label is skipped
 * rather than surfaced blank.
 */
class IosPlaceAutocomplete : PlaceAutocomplete {
    private val autocomplete = Autocomplete()

    override suspend fun search(query: String): List<GeoPlace> =
        autocomplete
            .search(query)
            .getOrNull()
            ?.mapNotNull { it.toGeoPlaceOrNull() }
            .orEmpty()
}

/** Skips a [Place] with no usable label rather than surfacing a blank one. */
private fun Place.toGeoPlaceOrNull(): GeoPlace? {
    if (isEmpty) return null
    return GeoPlace(label = firstValue, coordinates = GeoCoordinates(coordinates.latitude, coordinates.longitude))
}
