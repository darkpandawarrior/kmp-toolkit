package com.siddharth.kmp.appshell

import android.app.Activity

/*
 * Activity-scoped manager factories. Play-Core-style update/review implementations need the host
 * Activity (not the Application context), so they cannot be plain process-wide singletons. An app
 * with a gms flavor binds the real factory; a noGms/FOSS flavor binds a no-op.
 *
 * The factory interfaces live here (not app-side) so any shared UI layer that resolves managers
 * generically can depend on them without depending on a flavor source set.
 */

/** Creates an Activity-scoped [AppUpdateManager]. */
fun interface AppUpdateManagerFactory {
    fun create(activity: Activity): AppUpdateManager
}

/** Creates an Activity-scoped [AppReviewManager]. */
fun interface AppReviewManagerFactory {
    fun create(activity: Activity): AppReviewManager
}
