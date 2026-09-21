package com.siddharth.kmp.auth

import com.siddharth.kmp.common.Hashing
import kotlinx.cinterop.BetaInteropApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume

/** `ASAuthorizationError.canceled`. Hard-coded rather than imported so the binding name cannot drift. */
private const val AS_AUTHORIZATION_ERROR_CANCELED = 1001L

/**
 * Sign in with Apple on iOS, natively — no Services ID, no redirect URI, no server bounce. The App
 * ID's *Sign In with Apple* capability and the matching entitlement are the whole configuration,
 * which is why none of the `__PROVISION_APPLE_*__` values appear on this side. (Your **server** still
 * needs them to exchange or revoke the code; see [AppleSiwaServer].)
 *
 * @param anchor the window to present over. Required, not defaulted: every way of finding the key
 *   window from a library (`UIApplication.keyWindow`, `.windows`) is deprecated and wrong in a
 *   multi-scene app. The host has the window; the host passes it.
 *
 * ### The weak-delegate trap
 *
 * `ASAuthorizationController` holds `delegate` and `presentationContextProvider` **weakly**. A
 * delegate allocated inside a `suspend fun` has no other owner, so ARC frees it the moment the
 * function suspends — the sheet stays on screen, no callback ever fires, and the coroutine hangs
 * forever with no error to log. [inFlight] and [inFlightController] are the strong references that
 * stop that, and they are cleared only once the flow has actually ended. This is the same hazard as
 * `PKPaymentAuthorizationController`; it is a property of the delegate pattern, not of one API.
 */
class IosAppleSignIn(
    private val anchor: () -> UIWindow,
    private val nonceSource: () -> String = { newRawNonce() },
) : SocialSignIn {
    override val provider: AuthProvider = AuthProvider.APPLE

    // LOAD-BEARING. Do not inline, do not make local, do not "clean up" as unused: these are the
    // only strong references to objects UIKit holds weakly. See the class KDoc.
    private var inFlight: AppleSignInDelegate? = null
    private var inFlightController: ASAuthorizationController? = null

    /**
     * Always [SignInAvailability.AVAILABLE] on a supported deployment target. A missing entitlement
     * cannot be detected before presenting — it surfaces as an error from the sheet, and comes back
     * as [SignInOutcome.Failed]. Nothing here is provisioning-gated, so there is no
     * [SignInAvailability.NOT_CONFIGURED] state to reach.
     */
    override fun availability(): SignInAvailability = SignInAvailability.AVAILABLE

    override suspend fun signIn(): SignInOutcome = withContext(Dispatchers.Main) {
        // performRequests() presents UI, so it is main-thread-only. Forced here rather than
        // documented, because the failure off-main is a rare crash rather than a clear error.
        suspendCancellableCoroutine { continuation ->
            val rawNonce = nonceSource()
            val request = ASAuthorizationAppleIDProvider().createRequest().apply {
                requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
                // Apple stores the SHA-256; the raw value travels home for the server to compare.
                nonce = Hashing.sha256Hex(rawNonce)
            }
            val delegate = AppleSignInDelegate(rawNonce, anchor) { outcome ->
                release()
                if (continuation.isActive) continuation.resume(outcome)
            }
            val controller = ASAuthorizationController(authorizationRequests = listOf(request))
            controller.delegate = delegate
            controller.presentationContextProvider = delegate
            inFlight = delegate
            inFlightController = controller
            continuation.invokeOnCancellation { release() }
            controller.performRequests()
        }
    }

    private fun release() {
        inFlight = null
        inFlightController = null
    }
}

private class AppleSignInDelegate(
    private val rawNonce: String,
    private val anchor: () -> UIWindow,
    private val onResult: (SignInOutcome) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization,
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        if (credential == null) {
            onResult(SignInOutcome.Failed("Apple returned a credential that is not an Apple ID credential"))
            return
        }
        val idToken = credential.identityToken?.utf8()
        if (idToken == null) {
            onResult(SignInOutcome.Failed("Apple returned no identity token"))
            return
        }
        onResult(
            SignInOutcome.Success(
                SocialIdentity(
                    provider = AuthProvider.APPLE,
                    idToken = idToken,
                    rawNonce = rawNonce,
                    authorizationCode = credential.authorizationCode?.utf8(),
                    // Stable per (app, Apple ID). Survives email relay changes; the email does not.
                    userId = credential.user,
                    email = credential.email,
                    // Populated on the FIRST authorization only — Apple never sends the name again.
                    displayName = credential.fullName?.let { name ->
                        listOfNotNull(name.givenName, name.familyName).joinToString(" ").ifBlank { null }
                    },
                ),
            ),
        )
    }

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithError: NSError,
    ) {
        onResult(
            if (didCompleteWithError.code == AS_AUTHORIZATION_ERROR_CANCELED) {
                SignInOutcome.Cancelled
            } else {
                SignInOutcome.Failed(didCompleteWithError.localizedDescription)
            },
        )
    }

    override fun presentationAnchorForAuthorizationController(
        controller: ASAuthorizationController,
    ): ASPresentationAnchor = anchor()
}

@OptIn(BetaInteropApi::class)
private fun NSData.utf8(): String? = NSString.create(data = this, encoding = NSUTF8StringEncoding)?.toString()
