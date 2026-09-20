package com.siddharth.kmp.auth

/**
 * The **WEB** OAuth client ID from the Google Cloud console, not the Android one.
 *
 * This is the single most-reported Credential Manager failure: an Android client ID is accepted by
 * the builder, compiles, and then fails at runtime with a generic `GetCredentialException`. The
 * Android client ID exists only to bind your package + signing SHA-1; the *audience* of the ID token
 * — which is what `setServerClientId` sets — is the web client. A client ID is public by design, so
 * the real value may live in the repo once provisioned.
 */
const val GOOGLE_WEB_CLIENT_ID: String = "__PROVISION_GOOGLE_WEB_CLIENT_ID__"

/**
 * Configuration for `GetGoogleIdOption`.
 *
 * @param filterByAuthorizedAccounts `true` shows only accounts that already signed in to this app —
 *   the returning-user path, and the one that must be tried first so the bottom sheet stays quiet.
 * @param signUpFallback when the filtered request finds nothing (every brand-new user), retry once
 *   with the filter off. Without this, a first-time user gets "no credentials" and the sign-in
 *   button looks broken. The cost of the retry is one extra round trip on a cold account only.
 * @param autoSelectEnabled skips the chooser when exactly one account has been used before. Only
 *   honoured after the user has signed in once, so it cannot surprise a first-time user.
 */
data class GoogleSignInConfig(
    val serverClientId: String = GOOGLE_WEB_CLIENT_ID,
    val filterByAuthorizedAccounts: Boolean = true,
    val signUpFallback: Boolean = true,
    val autoSelectEnabled: Boolean = true,
) {
    fun availability(): SignInAvailability =
        if (configProblem() == null) SignInAvailability.AVAILABLE else SignInAvailability.NOT_CONFIGURED

    fun configProblem(): String? = when {
        serverClientId.isUnprovisioned() -> "Google web client ID is unprovisioned ($GOOGLE_WEB_CLIENT_ID)"
        !serverClientId.endsWith(".apps.googleusercontent.com") ->
            "serverClientId is not a Google client ID: $serverClientId"
        else -> null
    }
}
