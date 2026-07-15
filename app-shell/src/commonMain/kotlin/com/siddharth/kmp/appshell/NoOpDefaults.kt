package com.siddharth.kmp.appshell

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

// Public no-op defaults for every interface this module publishes — so a consuming app's own
// facade (e.g. a `PlatformBindings`-style data class grouping this module's services alongside its
// own) can reuse these instead of re-declaring them, and so commonMain code / tests can run with no
// real platform behind it. Object per interface (no shared base) — each is a one-off, not a hierarchy.

object NoOpLocationTracker : LocationTracker {
    override val updates: Flow<GeoPoint> = emptyFlow()

    override suspend fun current(): GeoPoint? = null

    override fun start() = Unit

    override fun stop() = Unit
}

object NoOpDocumentScanner : DocumentScanner {
    override suspend fun scan(maxPages: Int): List<ByteArray> = emptyList()
}

object NoOpNotificationScheduler : NotificationScheduler {
    override suspend fun ensurePermission(): Boolean = false

    override fun notify(
        id: Int,
        title: String,
        body: String,
    ) = Unit

    override fun cancel(id: Int) = Unit
}

object NoOpPermissionsProvider : PermissionsProvider {
    override suspend fun isGranted(permission: AppPermission): Boolean = false

    override suspend fun request(permission: AppPermission): PermissionResult = PermissionResult.Denied
}

object NoOpAppUpdateManager : AppUpdateManager {
    override suspend fun checkForUpdate(config: UpdateConfig): UpdateAvailability = UpdateAvailability.NotAvailable

    override fun startUpdate(mode: UpdateMode) = Unit

    override suspend fun completeFlexibleUpdate() = Unit
}

object NoOpAppReviewManager : AppReviewManager {
    override suspend fun promptForReview() = Unit
}

object NoOpPushMessaging : PushMessaging {
    override suspend fun currentToken(): String? = null

    override val onTokenRefresh: Flow<String> = emptyFlow()

    override suspend fun subscribeTopic(topic: String) = Unit

    override suspend fun unsubscribeTopic(topic: String) = Unit
}

object NoOpAnalyticsHelper : AnalyticsHelper {
    override fun log(event: AnalyticsEvent) = Unit

    override fun setUserProperty(
        name: String,
        value: String?,
    ) = Unit

    override fun setEnabled(enabled: Boolean) = Unit
}
