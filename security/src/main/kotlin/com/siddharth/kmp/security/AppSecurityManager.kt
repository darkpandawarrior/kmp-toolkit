package com.siddharth.kmp.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.siddharth.kmp.common.AppLog
import java.lang.ref.WeakReference

private const val TAG = "AppSecurityManager"

/**
 * Central security facade for window/UI-surface hardening. It owns the three screen-facing
 * VAPT defenses, each gated by a [SecurityConfig] flag so it is server/BuildConfig-toggleable:
 *
 *  1. **Screenshot protection** ([applyScreenshotProtection]) — sets
 *     [WindowManager.LayoutParams.FLAG_SECURE] on the Activity window. Blocks screenshots and screen
 *     recording/casting, and blanks the window in the recents (task-switcher) thumbnail. For a
 *     payments app this stops a card number / CVV / OTP leaking through a screenshot or screen share.
 *     (Compose-scoped equivalent already exists as [SecureScreen]; this is the Activity-wide form.)
 *
 *  2. **Tapjacking protection** ([applyTapjackingProtection]) — sets `filterTouchesWhenObscured` on
 *     the decor/root view (recursively), so the framework **drops** touch events that arrive while
 *     another window is drawn on top. This defeats overlay / clickjacking attacks that trick the
 *     user into approving a payment they can't see. [shouldBlockObscuredTouch] is the manual filter
 *     to call from `Activity.dispatchTouchEvent`.
 *
 *  3. **Backgrounding security overlay** ([install]) — registers an
 *     [Application.ActivityLifecycleCallbacks] and tracks a started-Activity count to detect when the
 *     whole app crosses into background. On background it re-asserts `FLAG_SECURE` on the current
 *     Activity so the recents thumbnail is blanked even for screens that don't otherwise set it; on
 *     foreground the flag is cleared again. This is the standard "hide sensitive content from the
 *     recents/screen-share on background" behavior via the `FLAG_SECURE`-toggle strategy (no separate
 *     branded overlay Activity, and — deliberately — no dependency on `lifecycle-process`, so the
 *     module needs no new artifact).
 *
 * ## Functional vs best-effort
 * All three are **functional, real platform behaviors** — `FLAG_SECURE`, `filterTouchesWhenObscured`,
 * and `FLAG_WINDOW_IS_OBSCURED` are enforced by the framework/compositor, not heuristics. The only
 * caveat is coverage: they protect a window/Activity, so [install] must be called from
 * `Application.onCreate` and [applySecurityToActivity] from each Activity's `onCreate` to be
 * effective app-wide.
 *
 * Koin-friendly: constructed as a plain `single { AppSecurityManager(get()) }`, no Hilt annotations.
 */
class AppSecurityManager(
    private val config: SecurityConfig,
) {
    /** Tracks the current resumed Activity so the background hook can re-flag its window. */
    @Volatile
    private var currentActivity: WeakReference<Activity> = WeakReference(null)

    /** Number of started Activities. 0 => app is in background; the >0/==0 edges are the transitions. */
    private var startedActivities = 0

    // ---------------------------------------------------------------------------------------------
    // Screenshot protection (FLAG_SECURE)
    // ---------------------------------------------------------------------------------------------

    /**
     * Applies [WindowManager.LayoutParams.FLAG_SECURE] to [activity]'s window when
     * [SecurityConfig.screenshotProtectionEnabled] is on. Call from `Activity.onCreate`.
     */
    fun applyScreenshotProtection(activity: Activity) {
        if (!config.screenshotProtectionEnabled) {
            AppLog.d("Screenshot protection disabled by config for ${activity.javaClass.simpleName}", tag = TAG)
            return
        }
        runCatching {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
            AppLog.d("FLAG_SECURE applied to ${activity.javaClass.simpleName}", tag = TAG)
        }.onFailure { AppLog.e("Failed to apply FLAG_SECURE", it, tag = TAG) }
    }

    // ---------------------------------------------------------------------------------------------
    // Tapjacking protection (filterTouchesWhenObscured)
    // ---------------------------------------------------------------------------------------------

    /**
     * Recursively sets `filterTouchesWhenObscured = true` on [activity]'s decor view and all child
     * views, so obscured touches are dropped by the framework. Call from `Activity.onCreate` after
     * `setContentView`.
     */
    fun applyTapjackingProtection(activity: Activity) {
        if (!config.tapjackingProtectionEnabled) {
            AppLog.d("Tapjacking protection disabled by config for ${activity.javaClass.simpleName}", tag = TAG)
            return
        }
        runCatching {
            applyFilterTouchesRecursively(activity.window.decorView)
            AppLog.d("Tapjacking protection applied to ${activity.javaClass.simpleName}", tag = TAG)
        }.onFailure { AppLog.e("Failed to apply tapjacking protection", it, tag = TAG) }
    }

    private fun applyFilterTouchesRecursively(view: View) {
        view.filterTouchesWhenObscured = true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyFilterTouchesRecursively(view.getChildAt(i))
            }
        }
    }

    /**
     * Manual obscured-touch filter for `Activity.dispatchTouchEvent`: returns `true` when the event
     * is flagged [MotionEvent.FLAG_WINDOW_IS_OBSCURED] (or partially obscured) and tapjacking
     * protection is on, meaning the caller should swallow the event.
     */
    fun shouldBlockObscuredTouch(event: MotionEvent): Boolean {
        if (!config.tapjackingProtectionEnabled) return false
        val obscured =
            (event.flags and MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0 ||
                (event.flags and MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0
        if (obscured) {
            AppLog.w("Blocked obscured touch event — possible tapjacking attempt", tag = TAG)
        }
        return obscured
    }

    /** Convenience: apply both screenshot + tapjacking protection to [activity]. */
    fun applySecurityToActivity(activity: Activity) {
        applyScreenshotProtection(activity)
        applyTapjackingProtection(activity)
    }

    // ---------------------------------------------------------------------------------------------
    // Backgrounding security overlay (FLAG_SECURE toggle on app background)
    // ---------------------------------------------------------------------------------------------

    /**
     * Installs process-wide background protection by registering an
     * [Application.ActivityLifecycleCallbacks]. It tracks the current resumed Activity and a
     * started-Activity count; when the count drops to 0 (app backgrounded) it re-asserts
     * `FLAG_SECURE` on that Activity, and when it rises from 0 (app foregrounded) it clears the flag.
     *
     * Call once from `Application.onCreate`. No-op (beyond tracking) when
     * [SecurityConfig.securityOverlayEnabled] is false.
     */
    fun install(application: Application) {
        application.registerActivityLifecycleCallbacks(activityTracker)
        AppLog.d("AppSecurityManager installed (overlay=${config.securityOverlayEnabled})", tag = TAG)
    }

    private val activityTracker =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                val wasBackground = startedActivities == 0
                startedActivities++
                if (wasBackground) onAppForegrounded()
            }

            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                if (startedActivities == 0) onAppBackgrounded()
            }

            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?,
            ) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle,
            ) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        }

    private fun onAppBackgrounded() {
        if (!config.securityOverlayEnabled) return
        currentActivity.get()?.let { activity ->
            runCatching {
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
                AppLog.d("Security overlay ON (FLAG_SECURE) for background", tag = TAG)
            }.onFailure { AppLog.e("Failed to enable background overlay", it, tag = TAG) }
        }
    }

    private fun onAppForegrounded() {
        if (!config.securityOverlayEnabled) return
        currentActivity.get()?.let { activity ->
            runCatching {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                AppLog.d("Security overlay OFF (FLAG_SECURE cleared) for foreground", tag = TAG)
            }.onFailure { AppLog.e("Failed to clear background overlay", it, tag = TAG) }
        }
    }
}
