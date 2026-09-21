package com.siddharth.kmp.auth

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Sign in with Apple on Android. There is **no native SDK** — this is Apple's OAuth web flow, and it
 * needs a server. [AppleSiwaServer] documents exactly which server, and why no client-only path
 * exists once you ask for name and email.
 *
 * Deliberately **not** a [SocialSignIn]: that interface promises a `suspend fun` that resumes with a
 * result, and this flow leaves the app for a browser and returns through a deep link into a possibly
 * brand-new process. A `suspend` wrapper around it would be a coroutine that sometimes never resumes
 * — a hang with no error, which is worse than an API that admits it has two halves.
 *
 * ```
 * // 1. sign-in button
 * when (val start = appleSignIn.begin()) {
 *     is AppleWebStart.Launched -> settings.putString("apple_pending", start.pending.encode())
 *     is AppleWebStart.Unavailable -> hideAppleButton(start.reason)   // never draw a dead button
 * }
 *
 * // 2. the Activity your server's 302 lands in (android:autoVerify App Link)
 * val pending = settings.getStringOrNull("apple_pending")?.let(AppleWebPending::decode)
 * val outcome = pending?.let { appleSignIn.complete(it, intent.data.toString()) }
 * settings.remove("apple_pending")   // single use: a replayed state is a state to reject
 * ```
 *
 * The app link that receives the bounce must be declared in the **host app's** manifest with
 * `android:autoVerify="true"` and a matching `assetlinks.json` on the domain. Without verification
 * Android shows a disambiguation dialog, and any other installed app may claim the same URL.
 *
 * ponytail: opens the user's browser with `ACTION_VIEW` rather than a Custom Tab. A Custom Tab looks
 * better and shares the browser's Apple session, but costs an `androidx.browser` dependency this
 * module does not otherwise need. Swap it in the host app if the polish is worth the artifact — and
 * never swap in a `WebView`: an embedded WebView can read the user's Apple password.
 */
class AndroidAppleSignIn(
    private val context: Context,
    private val config: AppleSignInConfig,
    private val nonceSource: () -> String = { newRawNonce() },
    private val stateSource: () -> String = { newRawNonce() },
) {
    val provider: AuthProvider = AuthProvider.APPLE

    /**
     * Provisioning only. Whether a browser exists cannot be answered honestly ahead of time —
     * package-visibility on API 30+ makes `resolveActivity` return null for a browser that is in fact
     * installed — so that failure is reported by [begin] instead of guessed at here.
     */
    fun availability(): SignInAvailability = config.availability()

    /** Builds the authorize URL, hands it to a browser, and returns what must survive until the callback. */
    fun begin(): AppleWebStart {
        config.configProblem()?.let { problem ->
            return AppleWebStart.Unavailable(SignInAvailability.NOT_CONFIGURED, problem)
        }
        val pending = AppleWebFlow.begin(config, rawNonce = nonceSource(), state = stateSource())
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pending.authorizeUrl)).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            AppleWebStart.Launched(pending)
        } catch (notFound: ActivityNotFoundException) {
            AppleWebStart.Unavailable(
                SignInAvailability.UNSUPPORTED_DEVICE,
                notFound.message ?: "no browser available to open the Apple authorize page",
            )
        }
    }

    /** Reads the deep link your server bounced back. Pure; see [AppleWebFlow.complete]. */
    fun complete(pending: AppleWebPending, callbackUrl: String): SignInOutcome =
        AppleWebFlow.complete(pending, callbackUrl)
}

/** Result of [AndroidAppleSignIn.begin] — either the browser is up, or the reason it is not. */
sealed interface AppleWebStart {
    data class Launched(val pending: AppleWebPending) : AppleWebStart

    data class Unavailable(val reason: SignInAvailability, val detail: String) : AppleWebStart
}
