@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
// detekt's MatchingDeclarationName recognizes the .android/.ios/.jvm multiplatform file-name
// suffixes this repo's other actuals use, but not .wasmJs; suppressed at the file level (a
// declaration-level @Suppress on the class doesn't reach this rule) to keep the same
// `SecureKeyStore.<platform>.kt` naming every other actual in this module uses.
@file:Suppress("MatchingDeclarationName")

package com.siddharth.kmp.llmchat

/**
 * **Not secure.** `window.sessionStorage` is plaintext, readable by any script running on the same
 * page (including an XSS payload), and survives only the current tab's lifetime — closing the tab
 * or opening a new one loses the key. This exists so a browser demo has *somewhere* to put a
 * pasted key rather than re-prompting on every reload of the same tab; it is not the Keystore/
 * Keychain guarantee the other three platforms give.
 *
 * // ponytail: no encryption-at-rest is possible in a browser without a server-side proxy holding
 * // the real key — that's a different architecture, not a smaller version of this one. A real web
 * // deployment should route cloud-provider calls through a backend that holds the key server-side
 * // instead of shipping it to the client at all; upgrade path is "don't store a key in the browser",
 * // not a better wasmJs actual.
 */
actual class SecureKeyStore {
    actual fun getKey(provider: ProviderId): String? = provider.secureStorageKeyOrNull()?.let(::sessionStorageGetItem)

    actual fun setKey(
        provider: ProviderId,
        apiKey: String?,
    ) {
        val storageKey = provider.secureStorageKeyOrNull() ?: return
        if (apiKey.isNullOrBlank()) {
            sessionStorageRemoveItem(storageKey)
        } else {
            sessionStorageSetItem(storageKey, apiKey)
        }
    }
}

// No dependency added for this: reaching `window.sessionStorage` the usual way means pulling in
// `kotlinx-browser` for three one-line calls — same tradeoff `:network`'s BrowserConnectivityChecker
// already made. Each call is wrapped in JS try/catch: a browser that blocks storage (locked-down
// private mode) degrades to "no key persisted" rather than throwing across the JS/Wasm boundary.

@JsFun("(key) => { try { return window.sessionStorage.getItem(key); } catch (e) { return null; } }")
private external fun sessionStorageGetItem(key: String): String?

@JsFun("(key, value) => { try { window.sessionStorage.setItem(key, value); } catch (e) { /* no-op */ } }")
private external fun sessionStorageSetItem(
    key: String,
    value: String,
)

@JsFun("(key) => { try { window.sessionStorage.removeItem(key); } catch (e) { /* no-op */ } }")
private external fun sessionStorageRemoveItem(key: String)
