package com.siddharth.kmp.auth

import com.siddharth.kmp.common.Hashing

/**
 * Apple's Services ID — the OAuth `client_id` for Sign in with Apple **on the web and on Android**.
 * Not the App ID: iOS uses the App ID capability and needs none of these three values.
 */
const val APPLE_SERVICES_ID: String = "__PROVISION_APPLE_SERVICES_ID__"

/**
 * The `redirect_uri` registered against the Services ID. Must be an **https URL you control that
 * runs code** — see [AppleSiwaServer] for why it cannot be an app link when scopes are requested.
 */
const val APPLE_SIWA_REDIRECT_URI: String = "__PROVISION_APPLE_SIWA_REDIRECT_URI__"

/** Apple Developer Team ID. Server-side only: it is the `iss` of the client secret JWT. */
const val APPLE_TEAM_ID: String = "__PROVISION_APPLE_TEAM_ID__"

/** Key ID of the Sign in with Apple `.p8`. Server-side only: it is the `kid` of the client secret JWT. */
const val APPLE_SIWA_KEY_ID: String = "__PROVISION_APPLE_SIWA_KEY_ID__"

/**
 * The server half of Sign in with Apple on Android, written down because there is no client-only
 * path and pretending otherwise is how this ships broken.
 *
 * **Why a server is unavoidable.** Apple *forces* `response_mode=form_post` whenever `scope` is
 * requested (name/email). form_post means Apple sends an HTTP **POST** with a form body to the
 * `redirect_uri`. An Android App Link cannot receive a POST — the intent system delivers a GET-shaped
 * VIEW intent and the body is dropped. So `redirect_uri` must point at a server endpoint that:
 *
 *  1. accepts `POST` with `application/x-www-form-urlencoded`;
 *  2. reads `code`, `id_token`, `state`, and `user` (first authorization only) from the body;
 *  3. **verifies `state`** against the one it issued — this is the CSRF check, and the client's
 *     second check in [AppleWebFlow.complete] is a belt, not the braces;
 *  4. replies `302` to your app link, e.g.
 *     `https://yourdomain.example/auth/apple/callback?code=…&id_token=…&state=…&user=…`,
 *     percent-encoding every value;
 *  5. ideally exchanges the code server-side and bounces only an opaque session handle, so the
 *     Apple `code` never rides through a URL an installed browser extension could read.
 *
 * Skipping the scope request is the one escape hatch: with no `scope`, Apple allows
 * `response_mode=query` and will 302 straight to an app link, no server. You then never learn the
 * user's name or email — and Apple only ever sends the name **once**, on first authorization, so
 * "we'll ask for it later" does not exist. [AppleSignInConfig] supports both; the empty-scope form
 * is honest about what it gives up.
 *
 * **The client secret.** Token exchange and revocation authenticate with a JWT signed ES256 by the
 * `.p8` key ([APPLE_SIWA_KEY_ID]), `iss` = [APPLE_TEAM_ID], `sub` = [APPLE_SERVICES_ID]. Apple caps
 * its lifetime at 6 months, so **a rotation job is mandatory** — the failure mode is every sign-in
 * breaking at once, six months after a launch nobody remembers. The `.p8` is downloadable exactly
 * once, is a private key, and must never enter this repo; it belongs in a secret manager.
 */
object AppleSiwaServer {
    /** Values the *server* needs. Listed here so `provisioning/provision.sh check` reports them. */
    val requiredValues: List<String> = listOf(APPLE_SERVICES_ID, APPLE_TEAM_ID, APPLE_SIWA_KEY_ID)

    /** True when the three server-side values have all been provisioned. */
    fun provisioned(): Boolean = requiredValues.none { it.isUnprovisioned() }
}

enum class AppleScope(
    internal val wireName: String,
) {
    NAME("name"),
    EMAIL("email"),
}

/**
 * Client-side configuration for Sign in with Apple through the web flow (Android).
 *
 * @param appCallbackUri the URI your server 302s to, which your app declares as a deep link. An
 *   `https://` App Link (with `assetlinks.json` and `android:autoVerify="true"`) is the right choice;
 *   a custom scheme works but any app can register it, which is a real account-takeover vector.
 * @param scopes empty means no server bounce is required — and no name or email, ever. See
 *   [AppleSiwaServer].
 */
data class AppleSignInConfig(
    val appCallbackUri: String,
    val servicesId: String = APPLE_SERVICES_ID,
    val redirectUri: String = APPLE_SIWA_REDIRECT_URI,
    val scopes: Set<AppleScope> = setOf(AppleScope.NAME, AppleScope.EMAIL),
) {
    /** `form_post` whenever a scope is requested — Apple's rule, not a preference. */
    internal val responseMode: String get() = if (scopes.isEmpty()) "query" else "form_post"

    fun availability(): SignInAvailability =
        if (configProblem() == null) SignInAvailability.AVAILABLE else SignInAvailability.NOT_CONFIGURED

    /** Human-readable reason the flag is [SignInAvailability.NOT_CONFIGURED], or null when it is fine. */
    fun configProblem(): String? =
        when {
            servicesId.isUnprovisioned() -> "Apple Services ID is unprovisioned ($APPLE_SERVICES_ID)"
            redirectUri.isUnprovisioned() -> "Apple redirect URI is unprovisioned ($APPLE_SIWA_REDIRECT_URI)"
            !redirectUri.startsWith("https://") -> "Apple rejects a redirect_uri that is not https: $redirectUri"
            appCallbackUri.isBlank() || "://" !in appCallbackUri -> "appCallbackUri is not a URI: $appCallbackUri"
            else -> null
        }
}

/**
 * A started Apple web sign-in. Hold it until the deep link comes back.
 *
 * The browser round trip can outlive the process, so this is [encode]/[decode]-able: stash the
 * string in the same `Settings` the app gives [TokenStore] and reload it in the deep-link handler.
 * Losing it is not fatal but it costs the user the whole flow, and it means the CSRF `state` check
 * cannot run on the client at all.
 */
data class AppleWebPending(
    val authorizeUrl: String,
    val state: String,
    val rawNonce: String,
) {
    /** `state` and `rawNonce` are hex, so `|` is an impossible byte in them. */
    fun encode(): String = "$state|$rawNonce|$authorizeUrl"

    companion object {
        /** `state|rawNonce|authorizeUrl` — three fields, in [encode]'s order. */
        private const val ENCODED_FIELD_COUNT = 3

        fun decode(encoded: String): AppleWebPending? {
            val parts = encoded.split('|', limit = ENCODED_FIELD_COUNT)
            if (parts.size != ENCODED_FIELD_COUNT || parts.any { it.isEmpty() }) return null
            return AppleWebPending(authorizeUrl = parts[2], state = parts[0], rawNonce = parts[1])
        }
    }
}

/**
 * The parts of Sign in with Apple on Android that are pure string work: building the authorize URL,
 * and reading the callback your server bounced back. Platform-free on purpose — this is where the
 * tests are, and it is identical whether the browser is a Custom Tab, an external browser, or a
 * WebView you should not be using for OAuth.
 */
object AppleWebFlow {
    private const val AUTHORIZE_ENDPOINT = "https://appleid.apple.com/auth/authorize"

    /** Apple's own cancel code, sent when the user backs out of the consent page. */
    private const val CANCELLED = "user_cancelled_authorize"

    /**
     * @param rawNonce a fresh, cryptographically random, per-attempt value (see `newRawNonce()` in
     *   the platform source sets). Only its SHA-256 goes to Apple; the raw one goes to your server
     *   so it can prove the token was minted for *this* attempt.
     * @param state a fresh random value, likewise per-attempt. It is the CSRF defence.
     */
    fun begin(
        config: AppleSignInConfig,
        rawNonce: String,
        state: String,
    ): AppleWebPending {
        require(rawNonce.length >= MIN_RANDOM_LENGTH) { "rawNonce is too short to be random" }
        require(state.length >= MIN_RANDOM_LENGTH) { "state is too short to be random" }
        val params =
            buildList {
                add("response_type" to "code id_token")
                add("client_id" to config.servicesId)
                add("redirect_uri" to config.redirectUri)
                add("state" to state)
                add("nonce" to Hashing.sha256Hex(rawNonce))
                add("response_mode" to config.responseMode)
                if (config.scopes.isNotEmpty()) {
                    add("scope" to config.scopes.sortedBy { it.wireName }.joinToString(" ") { it.wireName })
                }
            }
        val url = params.joinToString("&", prefix = "$AUTHORIZE_ENDPOINT?") { (k, v) -> "$k=${v.percentEncode()}" }
        return AppleWebPending(authorizeUrl = url, state = state, rawNonce = rawNonce)
    }

    /**
     * Reads the deep link your server sent back. Accepts the parameters in the query or the
     * fragment, because which one you get depends on how the bounce was written.
     *
     * A state mismatch is [SignInOutcome.Failed], never a success: it means the callback did not come
     * from the attempt this app started, and the only safe move is to throw the whole thing away.
     */
    fun complete(
        pending: AppleWebPending,
        callbackUrl: String,
    ): SignInOutcome {
        val params = parseParams(callbackUrl)
        params["error"]?.let { error ->
            return if (error == CANCELLED) SignInOutcome.Cancelled else SignInOutcome.Failed("Apple returned error=$error")
        }
        if (params["state"] != pending.state) {
            return SignInOutcome.Failed("state mismatch — callback does not belong to this sign-in attempt")
        }
        val idToken =
            params["id_token"]
                ?: return SignInOutcome.Failed("callback carried no id_token; check the server bounce forwards it")
        return SignInOutcome.Success(
            SocialIdentity(
                provider = AuthProvider.APPLE,
                idToken = idToken,
                rawNonce = pending.rawNonce,
                authorizationCode = params["code"],
                rawUserJson = params["user"],
            ),
        )
    }

    /** Splits `?a=1&b=2` or `#a=1&b=2`, percent-decoding both halves of each pair. */
    internal fun parseParams(url: String): Map<String, String> {
        val start = url.indexOfFirst { it == '?' || it == '#' }
        if (start < 0 || start == url.lastIndex) return emptyMap()
        return url
            .substring(start + 1)
            .split('&')
            .mapNotNull { pair ->
                val eq = pair.indexOf('=')
                if (eq <= 0) null else pair.substring(0, eq).percentDecode() to pair.substring(eq + 1).percentDecode()
            }.toMap()
    }
}

private const val MIN_RANDOM_LENGTH = 16

/** The RFC 3986 unreserved set: ALPHA / DIGIT / "-" / "." / "_" / "~". */
private fun Char.isRfc3986Unreserved(): Boolean = this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this in "-._~"

/** RFC 3986 unreserved set; everything else is escaped. `+` is NOT a space here, only in decode. */
private fun String.percentEncode(): String =
    encodeToByteArray().joinToString("") { byte ->
        val v = byte.toInt() and BYTE_MASK
        val c = v.toChar()
        // Explicit ASCII ranges, not isLetterOrDigit(): that is true for 'e9' -> 'e' too, and a
        // locale-aware predicate has no business deciding what is legal in a URL.
        if (c.isRfc3986Unreserved()) {
            c.toString()
        } else {
            "%" + v.toString(HEX_RADIX).uppercase().padStart(2, '0')
        }
    }

/** A percent escape is three characters: `%` plus two hex digits. */
private const val PERCENT_ESCAPE_LENGTH = 3

private fun String.percentDecode(): String {
    if ('%' !in this && '+' !in this) return this
    val out = ArrayList<Byte>(length)
    var i = 0
    while (i < length) {
        val c = this[i]
        when {
            c == '%' && i + 2 < length -> {
                val hex = substring(i + 1, i + PERCENT_ESCAPE_LENGTH).toIntOrNull(HEX_RADIX)
                if (hex == null) {
                    out.add(c.code.toByte())
                    i++
                } else {
                    out.add(hex.toByte())
                    i += PERCENT_ESCAPE_LENGTH
                }
            }
            c == '+' -> {
                out.add(' '.code.toByte())
                i++
            }
            else -> {
                // Non-ASCII in a URL is already illegal, but re-encode rather than lose it.
                c.toString().encodeToByteArray().forEach(out::add)
                i++
            }
        }
    }
    return out.toByteArray().decodeToString()
}
