// Fixture for scripts/objc-category-guard.py --self-test. NEVER COMPILED: it lives outside every
// module's `src`, so neither Gradle nor detekt (source.setFrom(projectDirectory.dir("src"))) sees
// it. Every line below is a real defect the guard must keep catching. Do not "fix" them.
package fixtures.trip

import platform.AVFAudio.AVAudioSession
import platform.CoreSpotlight.CSSearchableItemAttributeSet
import platform.Foundation.NSData
import platform.PassKit.PKPaymentAuthorizationResult
import platform.PassKit.PKPaymentAuthorizationStatusSuccess // CEnum entry imported bare

fun write(data: NSData, path: String): Boolean =
    data.writeToFile(path, atomically = true) // category member, import missing

fun activate(session: AVAudioSession) {
    session.setActive(true, null) // category member, import missing
}

fun index(attrs: CSSearchableItemAttributeSet, url: String) {
    attrs.title = "a" // category member, import missing
    attrs.contentURL = url // category member, import missing
}

fun approve() = PKPaymentAuthorizationResult(status = PKPaymentAuthorizationStatusSuccess, errors = null)
