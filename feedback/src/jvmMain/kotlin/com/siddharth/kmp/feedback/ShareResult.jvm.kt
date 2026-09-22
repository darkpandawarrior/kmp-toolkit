package com.siddharth.kmp.feedback

/**
 * Desktop has no system share sheet, so there is nothing to invoke: the JVM actual is a deliberate
 * no-op rather than an unfinished function. A desktop host that wants sharing copies the text to
 * the clipboard itself — that is a host decision, not something this module should guess at.
 */
actual fun shareText(text: String) {
    // Intentionally empty. See the KDoc above.
}
