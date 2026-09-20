// Fixture for scripts/objc-category-guard.py --self-test. NEVER COMPILED — see TripCases.kt.
// Every line below must produce ZERO findings: the correct import for each of the six seeded
// shapes, plus the escape hatch.
package fixtures.clean

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.setActive
import platform.CoreSpotlight.CSSearchableItemAttributeSet
import platform.CoreSpotlight.contentURL
import platform.CoreSpotlight.title
import platform.Foundation.NSData
import platform.Foundation.writeToFile
import platform.PassKit.PKPaymentAuthorizationResult
import platform.PassKit.PKPaymentAuthorizationStatus

fun write(data: NSData, path: String): Boolean = data.writeToFile(path, atomically = true)

fun activate(session: AVAudioSession) {
    session.setActive(true, null)
}

fun index(attrs: CSSearchableItemAttributeSet, url: String) {
    attrs.title = "a"
    attrs.contentURL = url
}

fun approve() = PKPaymentAuthorizationResult(
    status = PKPaymentAuthorizationStatus.PKPaymentAuthorizationStatusSuccess,
    errors = null,
)

// The escape hatch. In a file that DOES touch CoreSpotlight the guard cannot tell our own
// `keywords` from the framework's, so the marker is how a real false positive gets silenced.
private class Note(val keywords: List<String>)

fun ownKeywords(note: Note): Int =
    note.keywords.size // objc-category-guard:allow — Note's own property, not CoreSpotlight's
