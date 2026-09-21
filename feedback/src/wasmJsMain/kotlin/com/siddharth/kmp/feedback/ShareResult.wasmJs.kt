package com.siddharth.kmp.feedback

/**
 * Deliberate no-op. The Web Share API (`navigator.share`) is available only on a secure origin,
 * only from inside a user-gesture handler, and not at all on most desktop browsers — calling it
 * from here would throw on the majority of hosts. Wiring it up properly means routing the call
 * through the host's gesture handler, which is a change to this module's contract, not a fix.
 */
actual fun shareText(text: String) {
    // Intentionally empty. See the KDoc above.
}
