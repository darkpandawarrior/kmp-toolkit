package com.siddharth.kmp.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RAW_NONCE = "0123456789abcdef0123456789abcdef"

/** SHA-256 of [RAW_NONCE], computed independently — also pins :common's pure-Kotlin digest. */
private const val HASHED_NONCE = "3eb1bd439947eb762998e566ccc2e099c791118b2f40579cc4f7da2b5061b7f9"

private const val STATE = "feedfacefeedfacefeedfacefeedface"

/**
 * Sentinel-SHAPED, but not a key in `provisioning/placeholders.json` — so `provision.sh apply` walks
 * straight past it. A test that used a real sentinel would start failing the day someone provisions.
 */
private const val UNPROVISIONED = "__PROVISION_TEST_ONLY__"

private fun configured(scopes: Set<AppleScope> = setOf(AppleScope.NAME, AppleScope.EMAIL)) =
    AppleSignInConfig(
        appCallbackUri = "https://example.test/auth/apple/callback",
        servicesId = "test.example.service",
        redirectUri = "https://example.test/auth/apple/bounce",
        scopes = scopes,
    )

class AppleWebFlowTest {
    @Test
    fun unreplacedSentinelsReportNotConfigured() {
        val config = configured().copy(servicesId = UNPROVISIONED)
        assertEquals(SignInAvailability.NOT_CONFIGURED, config.availability())
        assertTrue(config.configProblem()!!.contains(PROVISION_PREFIX))
        assertEquals(SignInAvailability.NOT_CONFIGURED, configured().copy(redirectUri = "").availability())
    }

    @Test
    fun theUnprovisionedTestIsPrefixAndBlank() {
        assertTrue(UNPROVISIONED.isUnprovisioned())
        assertTrue("".isUnprovisioned())
        assertTrue(!"merchant.real.value".isUnprovisioned())
    }

    @Test
    fun httpRedirectIsRefused() {
        val config = configured().copy(redirectUri = "http://example.test/bounce")
        assertEquals(SignInAvailability.NOT_CONFIGURED, config.availability())
    }

    @Test
    fun fullyProvisionedConfigIsAvailable() {
        assertNull(configured().configProblem())
        assertEquals(SignInAvailability.AVAILABLE, configured().availability())
    }

    @Test
    fun authorizeUrlRequestsFormPostWhenScopesAreAsked() {
        val url = AppleWebFlow.begin(configured(), RAW_NONCE, STATE).authorizeUrl
        val params = AppleWebFlow.parseParams(url)
        assertTrue(url.startsWith("https://appleid.apple.com/auth/authorize?"))
        // Apple FORCES form_post once scope is requested — that is what makes a server mandatory.
        assertEquals("form_post", params["response_mode"])
        assertEquals("email name", params["scope"])
        assertEquals("code id_token", params["response_type"])
        assertEquals("test.example.service", params["client_id"])
        assertEquals("https://example.test/auth/apple/bounce", params["redirect_uri"])
        assertEquals(STATE, params["state"])
        // Only the hash goes to Apple. The raw nonce never leaves for the provider.
        assertEquals(HASHED_NONCE, params["nonce"])
        assertTrue(RAW_NONCE !in url)
    }

    @Test
    fun noScopesMeansNoServerBounceIsRequired() {
        val url = AppleWebFlow.begin(configured(scopes = emptySet()), RAW_NONCE, STATE).authorizeUrl
        val params = AppleWebFlow.parseParams(url)
        assertEquals("query", params["response_mode"])
        assertNull(params["scope"])
    }

    @Test
    fun shortRandomIsRejected() {
        assertFailsWith<IllegalArgumentException> { AppleWebFlow.begin(configured(), "abc", STATE) }
        assertFailsWith<IllegalArgumentException> { AppleWebFlow.begin(configured(), RAW_NONCE, "abc") }
    }

    @Test
    fun callbackCarriesTheCodeAndTheRawNonceHome() {
        val pending = AppleWebFlow.begin(configured(), RAW_NONCE, STATE)
        val outcome =
            AppleWebFlow.complete(
                pending,
                "https://example.test/auth/apple/callback?code=c0de&id_token=jwt.jwt.jwt&state=$STATE" +
                    "&user=%7B%22name%22%3A%7B%22firstName%22%3A%22Ada%22%7D%7D",
            )
        val identity = (outcome as SignInOutcome.Success).identity
        assertEquals("jwt.jwt.jwt", identity.idToken)
        assertEquals("c0de", identity.authorizationCode)
        assertEquals(RAW_NONCE, identity.rawNonce)
        assertEquals("""{"name":{"firstName":"Ada"}}""", identity.rawUserJson)
    }

    @Test
    fun aCallbackFromAnotherAttemptIsRejected() {
        val pending = AppleWebFlow.begin(configured(), RAW_NONCE, STATE)
        val outcome = AppleWebFlow.complete(pending, "https://example.test/cb?id_token=jwt&state=someoneelse")
        assertTrue((outcome as SignInOutcome.Failed).message.contains("state mismatch"))
    }

    @Test
    fun appleCancelIsCancelledNotFailed() {
        val pending = AppleWebFlow.begin(configured(), RAW_NONCE, STATE)
        val outcome = AppleWebFlow.complete(pending, "https://example.test/cb?error=user_cancelled_authorize")
        assertEquals(SignInOutcome.Cancelled, outcome)
    }

    @Test
    fun aCallbackWithoutAnIdTokenFails() {
        val pending = AppleWebFlow.begin(configured(), RAW_NONCE, STATE)
        val outcome = AppleWebFlow.complete(pending, "https://example.test/cb?code=c0de&state=$STATE")
        assertTrue((outcome as SignInOutcome.Failed).message.contains("id_token"))
    }

    @Test
    fun fragmentCallbacksParseTheSameAsQueryOnes() {
        val pending = AppleWebFlow.begin(configured(), RAW_NONCE, STATE)
        val outcome = AppleWebFlow.complete(pending, "myapp://cb#id_token=jwt&state=$STATE")
        assertEquals("jwt", (outcome as SignInOutcome.Success).identity.idToken)
    }

    @Test
    fun pendingSurvivesProcessDeath() {
        val pending = AppleWebFlow.begin(configured(), RAW_NONCE, STATE)
        assertEquals(pending, AppleWebPending.decode(pending.encode()))
        assertNull(AppleWebPending.decode("not-a-pending"))
    }

    @Test
    fun googleConfigRefusesAnUnprovisionedOrWrongClientId() {
        assertEquals(
            SignInAvailability.NOT_CONFIGURED,
            GoogleSignInConfig(serverClientId = UNPROVISIONED).availability(),
        )
        assertEquals(
            SignInAvailability.NOT_CONFIGURED,
            GoogleSignInConfig(serverClientId = "123-abc.example.com").availability(),
        )
        assertEquals(
            SignInAvailability.AVAILABLE,
            GoogleSignInConfig(serverClientId = "123-abc.apps.googleusercontent.com").availability(),
        )
    }
}
