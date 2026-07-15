package com.siddharth.kmp.appshell

import kotlinx.coroutines.flow.Flow

// Platform-neutral abstractions for device capabilities that have no single multiplatform library.
// Each interface is implemented per platform; the consuming app wires the impl into whatever DI
// framework it uses (this module has no DI dependency of its own).

/** A platform-neutral geographic sample. */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 0f,
    val timestampMillis: Long = 0L,
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
