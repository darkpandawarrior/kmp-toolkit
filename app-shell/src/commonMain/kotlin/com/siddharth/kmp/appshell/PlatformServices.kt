package com.siddharth.kmp.appshell

import kotlinx.coroutines.flow.Flow

// Platform-neutral abstractions for device capabilities that have no single multiplatform library.
// Each interface is implemented per platform; the consuming app wires the impl into whatever DI
// framework it uses (this module has no DI dependency of its own).

/**
 * A platform-neutral geographic sample.
 *
 * @param speedMetersPerSecond ground speed, or negative when unknown/invalid (matches
 *   `CLLocation.speed`'s convention: -1 = invalid). Android's `Location.speed` is never negative,
 *   so it maps straight through.
 * @param courseDegrees heading in `[0, 360)`, or negative when unknown/invalid (matches
 *   `CLLocation.course`; Android's `Location.bearing` is never negative).
 * @param altitudeMeters altitude above sea level, `0.0` when unknown.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
    val timestampMillis: Long = 0L,
    val speedMetersPerSecond: Float = -1f,
    val courseDegrees: Double = -1.0,
    val altitudeMeters: Double = 0.0,
)

/** Continuous + one-shot location access. Android: FusedLocation; iOS: CoreLocation. */
interface LocationTracker {
    /** Hot stream of location updates while tracking is active. */
    val updates: Flow<GeoPoint>

    /** One-shot best-effort current location, or null if unavailable. */
    suspend fun current(): GeoPoint?

    fun start()

    fun stop()
}

/**
 * Resolves a coordinate to a short, human-readable place name (reverse geocoding).
 *
 * Implementations run off the main thread and must **never** throw: an unresolved coordinate
 * (no geocoder, no network, no match, or an error) resolves to a [PlaceName] whose [PlaceName.name]
 * is `null`, so callers always have the formatted [PlaceName.coordinates] fallback to fall back on.
 *
 * - Android: `Geocoder` (async `GeocodeListener` on API 33+, blocking on `Dispatchers.IO` below).
 * - iOS: `CLGeocoder` (or a coords-only stub where reverse geocoding is unavailable).
 */
interface LocationNameResolver {
    /** Resolve [latitude]/[longitude] to a [PlaceName]; suspends, never throws. */
    suspend fun resolve(
        latitude: Double,
        longitude: Double,
    ): PlaceName
}

/**
 * The outcome of a reverse-geocode lookup.
 *
 * @param name a short place label (e.g. "Koregaon Park, Pune"), or `null` when unresolved.
 * @param coordinates the formatted `lat, lng` fallback line, always present.
 */
data class PlaceName(
    val name: String?,
    val coordinates: String,
) {
    /** The best single line to show: the resolved [name] when available, else the [coordinates]. */
    val displayLabel: String get() = name ?: coordinates

    companion object {
        /** Format a coordinate pair to the canonical `18.5207, 73.8570` fallback string. */
        fun formatCoordinates(
            latitude: Double,
            longitude: Double,
        ): String {
            fun fmt(v: Double): String {
                val scaled = kotlin.math.round(v * 10_000.0) / 10_000.0
                val whole = scaled.toLong()
                val frac = kotlin.math.abs(kotlin.math.round((scaled - whole) * 10_000.0).toLong())
                return "$whole.${frac.toString().padStart(4, '0')}"
            }
            return "${fmt(latitude)}, ${fmt(longitude)}"
        }

        /** Convenience for an unresolved lookup: name `null`, coordinates formatted. */
        fun coordinatesOnly(
            latitude: Double,
            longitude: Double,
        ): PlaceName = PlaceName(name = null, coordinates = formatCoordinates(latitude, longitude))
    }
}

/** Document scanning → captured page images as bytes. Android: ML Kit doc scanner; iOS: VisionKit. */
interface DocumentScanner {
    suspend fun scan(maxPages: Int = 1): List<ByteArray>
}

/** A plain lat/lng pair — the forward-geocoding counterpart of [PlaceName]'s coordinate string. */
data class GeoCoordinates(val latitude: Double, val longitude: Double)

/** A place suggestion: a human-readable [label] plus its resolvable [coordinates]. */
data class GeoPlace(val label: String, val coordinates: GeoCoordinates)

/**
 * Resolves free-text address input to coordinates (forward geocoding — the counterpart to
 * [LocationNameResolver]'s reverse direction). Android/iOS: Compass, itself a thin wrapper over the
 * same OS geocoders [LocationNameResolver] already wraps (`android.location.Geocoder` / `CLGeocoder`).
 *
 * Never throws: an unresolved or failed lookup returns an empty list.
 */
interface ForwardGeocoder {
    suspend fun forward(address: String): List<GeoCoordinates>
}

/**
 * Place-name autocomplete for an in-progress address search. Android/iOS: Compass's mobile
 * autocomplete, which matches partial input against the same OS geocoder [ForwardGeocoder] uses —
 * there is no separate typeahead network call or API key, so availability mirrors [ForwardGeocoder].
 *
 * Never throws: an unresolved or failed lookup returns an empty list.
 */
interface PlaceAutocomplete {
    suspend fun search(query: String): List<GeoPlace>
}

/** Local notifications. Android: NotificationManager + channels; iOS: UNUserNotificationCenter. */
interface NotificationScheduler {
    suspend fun ensurePermission(): Boolean

    fun notify(
        id: Int,
        title: String,
        body: String,
    )

    fun cancel(id: Int)
}

/** Runtime permissions the app requests. */
enum class AppPermission { LOCATION, LOCATION_BACKGROUND, CAMERA, NOTIFICATIONS, ACTIVITY_RECOGNITION, STORAGE }

sealed interface PermissionResult {
    data object Granted : PermissionResult

    data object Denied : PermissionResult

    data object DeniedAlways : PermissionResult
}

/** Runtime permission requests. Android: ActivityResult; iOS: the per-capability authorization APIs. */
interface PermissionsProvider {
    suspend fun isGranted(permission: AppPermission): Boolean

    suspend fun request(permission: AppPermission): PermissionResult
}

/**
 * File selection for import/export flows (e.g. picking a receipt, saving an export). Android/iOS:
 * Calf's file-picker/file-saver, bridged from the host's Compose layer — Calf's launchers are
 * `@Composable` (they wrap `rememberLauncherForActivityResult`/`UIDocumentPickerViewController`),
 * so like [PermissionsProvider.request]'s ActivityResult dialog, the actual pick/save UI can't be
 * driven from this headless module and is bridged in by whoever hosts the Compose UI.
 */
interface FilePicker {
    /** Prompts the user to pick a file; null if cancelled, unavailable, or no host UI has wired the picker. */
    suspend fun pickFile(): ByteArray?

    /** Prompts the user to choose a save location and writes [bytes] there; true on success. */
    suspend fun saveFile(
        fileName: String,
        bytes: ByteArray,
    ): Boolean
}
