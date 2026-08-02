package com.siddharth.kmp.auth

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where an app keeps its auth secrets, and nothing else.
 *
 * Two slots, because both consuming apps independently arrived at the same split:
 *  - [ephemeral] — in memory only, never written to disk, gone on process death. A short-lived
 *    access token belongs here: persisting one buys nothing (it expires anyway) and widens the
 *    at-rest attack surface for no gain.
 *  - [persisted] — written through [settings] under [persistedKey]. A refresh token, or the single
 *    opaque session token of an app with no access/refresh split.
 *
 * An app that has only one long-lived token uses [persisted] alone and never touches [ephemeral].
 *
 * ### What this deliberately does not know
 *
 * There is no HTTP here, no Ktor, no notion of "login", and no opinion about what a token *means*.
 * That is the finding, not an omission: the two apps this was extracted from do not share a token
 * *model*. One rotates an access/refresh pair through Ktor's own `Auth { bearer { } }` plugin, which
 * already dedupes concurrent 401s and retries once; the other holds a non-rotating opaque session
 * token and reacts to 401 through the `TokenProvider`/`UnauthorizedHandler` seam that `:network`
 * already exposes. Both mechanisms are correct for their app and neither is a subset of the other.
 * Unifying *those* would make one app worse to spare the other a file. Only the storage shape was
 * genuinely duplicated, so only the storage shape is here.
 *
 * ### Choosing the backing [Settings]
 *
 * Production: `SecureSettingsFactory().create()` from `:settings` — EncryptedSharedPreferences with
 * an AES-256-GCM MasterKey on Android, Keychain on iOS, an AES-256-GCM properties file with 0600
 * permissions on desktop JVM.
 *
 * Tests: `MapSettings()`.
 *
 * This module depends on `multiplatform-settings` directly rather than on `:settings`, so a consumer
 * that wants a fake is not forced to drag in the encrypted implementation.
 *
 * ```
 * val tokens = TokenStore(SecureSettingsFactory(context).create(), persistedKey = "refresh_token")
 * tokens.setEphemeral(response.accessToken)
 * tokens.setPersisted(response.refreshToken)
 * ```
 *
 * ponytail: not thread-safe beyond what [MutableStateFlow] and [Settings] already give. Both
 * consumers drive this from a single auth path; add locking only if a second writer ever appears.
 */
class TokenStore(
    private val settings: Settings,
    private val persistedKey: String,
) {
    private val _ephemeral = MutableStateFlow<String?>(null)

    /** The in-memory secret, observable so a session gate can react to sign-out without polling. */
    val ephemeral: StateFlow<String?> = _ephemeral.asStateFlow()

    fun setEphemeral(value: String?) {
        _ephemeral.value = value
    }

    /** The stored secret, or null if none has been written (or it was cleared). */
    fun persisted(): String? = settings.getStringOrNull(persistedKey)

    fun setPersisted(value: String) {
        settings.putString(persistedKey, value)
    }

    /**
     * Wipes both slots. Call on logout, and on a refresh the server rejects — leaving a dead refresh
     * token on disk means every later launch retries it and fails the same way.
     */
    fun clear() {
        _ephemeral.value = null
        settings.remove(persistedKey)
    }
}
