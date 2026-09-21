package com.siddharth.kmp.feedback

/**
 * Shares [text] through the platform share sheet.
 *
 * Implemented on Android (ACTION_SEND chooser) and iOS (UIActivityViewController). JVM and wasm are
 * no-ops: neither platform has a share sheet to call, so there is nothing to implement and nothing
 * being hidden. There is no capability flag for text because the only platforms where this does
 * nothing are the two where nothing could be done — contrast `canShareImage`, which reports a gap
 * that *is* host-app-fixable.
 */
expect fun shareText(text: String)
