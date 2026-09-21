package com.siddharth.kmp.auth

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.siddharth.kmp.common.Hashing

/**
 * Google sign-in through Credential Manager — the only supported path since the legacy
 * `GoogleSignInClient` was deprecated.
 *
 * @param activityContext **must be an Activity**, not the application context. Below Android 14 the
 *   bottom sheet is hosted by the calling Activity, and an application context throws at runtime
 *   rather than at compile time. Keeping the parameter typed as `Context` matches the platform
 *   signature; the requirement is real regardless.
 *
 * ### The nonce
 *
 * Only `sha256(rawNonce)` is sent to Google; the raw value comes back in
 * [SocialIdentity.rawNonce] for the server to compare against the ID token's `nonce` claim. Sending
 * the raw nonce in both places would defeat the point — anyone who intercepts the token would also
 * hold the value that proves it fresh.
 *
 * ### Why the retry exists
 *
 * A filtered request (`filterByAuthorizedAccounts = true`) is the quiet returning-user path, but it
 * throws `NoCredentialException` for every user who has never signed in to this app — i.e. all of
 * them, on day one. [GoogleSignInConfig.signUpFallback] retries once unfiltered so the button works
 * for new users. Skipping that is a "sign-in is broken" bug report that reproduces only on a fresh
 * install.
 */
class AndroidGoogleSignIn(
    private val activityContext: Context,
    private val config: GoogleSignInConfig = GoogleSignInConfig(),
    private val credentialManager: CredentialManager = CredentialManager.create(activityContext),
    private val nonceSource: () -> String = { newRawNonce() },
) : SocialSignIn {
    override val provider: AuthProvider = AuthProvider.GOOGLE

    override fun availability(): SignInAvailability = config.availability()

    override suspend fun signIn(): SignInOutcome {
        config.configProblem()?.let { problem ->
            return SignInOutcome.Unavailable(SignInAvailability.NOT_CONFIGURED, problem)
        }
        val rawNonce = nonceSource()
        val hashedNonce = Hashing.sha256Hex(rawNonce)
        val filtered = attempt(hashedNonce, rawNonce, filterByAuthorizedAccounts = config.filterByAuthorizedAccounts)
        val noAccounts =
            filtered is SignInOutcome.Unavailable &&
                filtered.reason == SignInAvailability.NO_CREDENTIALS_AVAILABLE
        if (!noAccounts || !config.signUpFallback || !config.filterByAuthorizedAccounts) return filtered
        return attempt(hashedNonce, rawNonce, filterByAuthorizedAccounts = false)
    }

    // The cancellation branch deliberately drops the exception: "the user closed the sheet" is not
    // a diagnosable event, and logging it trains people to ignore the log.
    @Suppress("SwallowedException")
    private suspend fun attempt(
        hashedNonce: String,
        rawNonce: String,
        filterByAuthorizedAccounts: Boolean,
    ): SignInOutcome {
        val option =
            GetGoogleIdOption
                .Builder()
                .setServerClientId(config.serverClientId)
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                // Auto-select only ever makes sense on the filtered pass: on the unfiltered sign-up pass
                // it would silently pick an account the user has never used with this app.
                .setAutoSelectEnabled(config.autoSelectEnabled && filterByAuthorizedAccounts)
                .setNonce(hashedNonce)
                .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            toOutcome(credentialManager.getCredential(activityContext, request).credential, rawNonce)
        } catch (cancelled: GetCredentialCancellationException) {
            // User dismissed the sheet. Not an error, and the message is not worth surfacing.
            SignInOutcome.Cancelled
        } catch (none: NoCredentialException) {
            SignInOutcome.Unavailable(
                SignInAvailability.NO_CREDENTIALS_AVAILABLE,
                none.message ?: "no Google account is available on this device",
            )
        } catch (failure: GetCredentialException) {
            // Covers the wrong-client-ID case, which arrives here as a generic failure with a
            // message that does not name the real cause. See GOOGLE_WEB_CLIENT_ID.
            SignInOutcome.Failed(failure.message ?: failure.type, failure)
        }
    }

    private fun toOutcome(
        credential: Credential,
        rawNonce: String,
    ): SignInOutcome {
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return SignInOutcome.Failed("unexpected credential type ${credential.type}")
        }
        return runCatching { GoogleIdTokenCredential.createFrom(credential.data) }.fold(
            onSuccess = { google ->
                SignInOutcome.Success(
                    SocialIdentity(
                        provider = AuthProvider.GOOGLE,
                        idToken = google.idToken,
                        rawNonce = rawNonce,
                        // `id` is the email; the stable subject lives in the ID token and is read
                        // server-side after verification, not trusted from here.
                        email = google.id,
                        displayName = google.displayName,
                    ),
                )
            },
            onFailure = { parseFailure ->
                SignInOutcome.Failed("could not parse the Google ID token credential", parseFailure)
            },
        )
    }
}
