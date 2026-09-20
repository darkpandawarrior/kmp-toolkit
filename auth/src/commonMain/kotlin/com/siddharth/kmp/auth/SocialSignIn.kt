package com.siddharth.kmp.auth

/**
 * Third-party sign-in for the toolkit: the shared vocabulary, and the store rules attached to it.
 *
 * ### Compliance — App Store Guideline 4.8 and 5.1.1(v)
 *
 * **4.8 (Login Services).** An app that offers *any* third-party or social login — Google, Facebook,
 * a partner SSO — must also offer an equivalent login service that limits data collection to name
 * and email, lets the user keep the email private, and does not track them for advertising without
 * consent. Sign in with Apple satisfies 4.8. So shipping [AuthProvider.GOOGLE] without also shipping
 * [AuthProvider.APPLE] is a rejection, not a roadmap item. That is why this module implements Apple
 * on **both** platforms, including the ugly Android web flow — the Android side is not the one that
 * gets reviewed, but the two platforms must offer the same accounts or the same user cannot sign in
 * on their second device.
 *
 * **5.1.1(v) (Account Deletion).** An app that supports account *creation* must let the user start
 * account *deletion* from inside the app. A "contact support" link does not satisfy it. For an app
 * using Sign in with Apple, deletion must also revoke the Apple grant server-side
 * (`POST https://appleid.apple.com/auth/revoke`, authenticated with the same client secret JWT as
 * the token exchange) — otherwise the account is gone but Apple still lists the app under the user's
 * Apple ID, which is itself a 5.1.1(v) rejection. Revocation needs the refresh token, so the server
 * has to have stored it at sign-in. Decide that before launch, not at the first rejection.
 *
 * Neither rule can be satisfied by client code alone, which is the honest reason both appear here:
 * the seam below is only the part of the problem that fits in a library.
 */
interface SocialSignIn {
    val provider: AuthProvider

    /** Why the button may not be drawn. [SignInAvailability.AVAILABLE] is the only green light. */
    fun availability(): SignInAvailability

    /**
     * Presents the provider's UI and suspends until the user finishes, cancels, or it fails.
     *
     * Only implemented where the platform owns the whole round trip in one process. Sign in with
     * Apple on Android does not qualify (it leaves for a browser and comes back through a deep
     * link, possibly after process death) — see `AndroidAppleSignIn`, which is deliberately a
     * two-call API instead of a `suspend fun` that would silently never resume.
     */
    suspend fun signIn(): SignInOutcome
}

enum class AuthProvider { GOOGLE, APPLE }

/**
 * Reason-carrying capability flag. Four states, not a Boolean, because the three failures want
 * three different UIs: hide the button, offer "add an account", say "not on this device".
 *
 * [NOT_CONFIGURED] is the one that catches build mistakes: it is what every flag returns while an
 * unreplaced `__PROVISION_*__` sentinel is still in the source (see `provisioning/`). The app then
 * refuses to draw a working-looking sign-in button instead of drawing one that fails at the tap.
 */
enum class SignInAvailability {
    AVAILABLE,

    /** Provider is usable, but this device/user has nothing to sign in with yet (no Google account). */
    NO_CREDENTIALS_AVAILABLE,

    /** OS or hardware cannot do it at all — no browser, OS too old, provider SDK missing. */
    UNSUPPORTED_DEVICE,

    /** A provisioning value is missing or is still a sentinel. A build problem, not a user problem. */
    NOT_CONFIGURED,
}

/**
 * What a provider hands back. Everything here is **untrusted client input** until a server verifies
 * it — a debugger can hand your app any [idToken] it likes.
 *
 * The server must, at minimum: verify the JWT signature against the provider's JWKS, check `iss`
 * and `aud`, check `exp`, and check that the `nonce` claim equals SHA-256([rawNonce]) — which is
 * why [rawNonce] travels back with the identity instead of being discarded. Skipping the nonce
 * check leaves the replay window that the nonce exists to close.
 */
data class SocialIdentity(
    val provider: AuthProvider,
    /** The OIDC ID token (a JWT). Send to your server; never trust its claims on the client. */
    val idToken: String,
    /** The *unhashed* nonce. The server compares SHA-256 of this to the token's `nonce` claim. */
    val rawNonce: String,
    /** Apple only: one-time code your server exchanges for an access/refresh token pair. */
    val authorizationCode: String? = null,
    /** Apple's stable `sub` for this app. Google's is inside [idToken] and is read server-side. */
    val userId: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    /**
     * Apple's `user` field, verbatim JSON, present **only on the very first authorization**. Apple
     * never sends the name again — not on re-sign-in, not on token refresh. Persist it server-side
     * on first sight or it is gone for that Apple ID forever (short of the user revoking the grant
     * in Settings and starting over).
     */
    val rawUserJson: String? = null,
)

sealed interface SignInOutcome {
    data class Success(val identity: SocialIdentity) : SignInOutcome

    /** User dismissed the sheet. Not an error — never show a toast for this. */
    data object Cancelled : SignInOutcome

    /** The capability flag said no. [detail] is for the log, [reason] is for the UI decision. */
    data class Unavailable(val reason: SignInAvailability, val detail: String) : SignInOutcome

    data class Failed(val message: String, val cause: Throwable? = null) : SignInOutcome
}

/** Prefix of every unreplaced provisioning sentinel — see `provisioning/placeholders.json`. */
internal const val PROVISION_PREFIX = "__PROVISION_"

/** True while a value is blank or still the literal sentinel the repo ships. */
internal fun String.isUnprovisioned(): Boolean = isBlank() || startsWith(PROVISION_PREFIX)

internal const val HEX_RADIX = 16
internal const val BYTE_MASK = 0xFF

/**
 * Lowercase hex of raw bytes. Deliberately not the stdlib's `ByteArray.toHexString()`, which is
 * still behind `@ExperimentalStdlibApi` and would push an opt-in onto every consumer.
 */
internal fun ByteArray.toLowerHex(): String =
    joinToString("") { byte -> (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(2, '0') }
