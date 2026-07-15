package com.siddharth.kmp.appshell

import kotlinx.coroutines.flow.Flow

/*
 * Cross-cutting device/store capabilities: in-app update, in-app review, push messaging, analytics.
 *
 * Same contract as PlatformServices.kt: every interface is platform-neutral and implemented per
 * platform. Each impl SHOULD degrade to a no-op when its backing key/service is absent, never crash
 * (see [NoOpAppUpdateManager] and friends in NoOpDefaults.kt for the reference no-op set).
 */

// ─────────────────────────── In-app update ───────────────────────────

/** How an available update should be applied. */
enum class UpdateMode { FORCED, FLEXIBLE }

/** Gate config for the in-app update flow (mirrors a remote splash/config API). */
data class UpdateConfig(
    val enabled: Boolean = false,
    val mode: UpdateMode = UpdateMode.FLEXIBLE,
    val minSupportedVersionCode: Long = 0L,
    val staleDays: Int = 0,
    val priority: Int = 0,
)

/** Result of querying the store for a newer build. */
sealed interface UpdateAvailability {
    data object NotAvailable : UpdateAvailability

    data object InProgress : UpdateAvailability

    data object Downloaded : UpdateAvailability

    data class Available(
        val availableVersionCode: Long,
        val mode: UpdateMode,
        val priority: Int = 0,
    ) : UpdateAvailability
}

/**
 * In-app update. Android: Play-Core AppUpdateManager (gms) / no-op (noGms). iOS: iTunes Lookup API
 * version compare. Typically Activity/UIViewController-scoped on Android via [AppUpdateManagerFactory]
 * — the gms-specific impl is app-side, not part of this module (see [AppUpdateManagerFactory] doc).
 */
interface AppUpdateManager {
    suspend fun checkForUpdate(config: UpdateConfig): UpdateAvailability

    fun startUpdate(mode: UpdateMode)

    suspend fun completeFlexibleUpdate()
}

// ─────────────────────────── In-app review ───────────────────────────

/** In-app review prompt. Android: Play review (gms) / store intent (noGms). iOS: SKStoreReviewController. */
interface AppReviewManager {
    /** Launch the platform review flow if the host allows it; silently no-ops otherwise. */
    suspend fun promptForReview()
}

// ─────────────────────────── Push messaging ───────────────────────────

/** Push token + topic surface. Android: FCM (gms) / no-op (noGms). iOS: APNs + Firebase. */
interface PushMessaging {
    suspend fun currentToken(): String?

    val onTokenRefresh: Flow<String>

    suspend fun subscribeTopic(topic: String)

    suspend fun unsubscribeTopic(topic: String)
}

// ─────────────────────────── Analytics ───────────────────────────

/**
 * A single analytics event. Param keys/values self-clamp to Firebase limits (≤ 40-char name/key,
 * ≤ 100-char value, ≤ 25 params) so impls never reject an event.
 */
data class AnalyticsEvent(
    val type: String,
    val params: Map<String, String> = emptyMap(),
) {
    /** Firebase-safe event name (≤ 40 chars). */
    val safeType: String get() = type.take(MAX_NAME)

    /** Firebase-safe params: ≤ 25 entries, keys ≤ 40 chars, values ≤ 100 chars. */
    val safeParams: Map<String, String>
        get() =
            params.entries
                .take(MAX_PARAMS)
                .associate { (key, value) -> key.take(MAX_NAME) to value.take(MAX_VALUE) }

    companion object {
        const val MAX_NAME = 40
        const val MAX_PARAMS = 25
        const val MAX_VALUE = 100
    }
}

/** Analytics sink. gms: FirebaseAnalytics (app-side); noGms + iOS + desktop: [LoggingAnalyticsHelper]. */
interface AnalyticsHelper {
    fun log(event: AnalyticsEvent)

    fun setUserProperty(
        name: String,
        value: String?,
    )

    /** Kill switch: when disabled, [log]/[setUserProperty] drop silently. Defaults to enabled. */
    fun setEnabled(enabled: Boolean)
}
