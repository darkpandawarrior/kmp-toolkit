package com.siddharth.kmp.appshell

/**
 * iOS document scanning.
 *
 * Unlike the other headless services here, VisionKit's `VNDocumentCameraViewController` is
 * **inherently a UI flow**: it must be *presented modally* from a live `UIViewController` and delivers
 * its pages through a delegate, so it cannot be driven from a background service object with no
 * presentation context (the Android side is the same — ML Kit's document scanner needs an Activity).
 *
 * The correct home for a real implementation is the Compose UI layer (a `ComposeUIViewController` host
 * can present the scanner and pump pages back), mirroring how camera capture and maps typically live in
 * the UI layer rather than as background services. Until that UI hook exists in the consuming app, this
 * returns no pages — a truthful no-op rather than fragile, untestable view-controller-presentation interop.
 */
class IosDocumentScanner : DocumentScanner {
    override suspend fun scan(maxPages: Int): List<ByteArray> = emptyList()
}
