// EXPERIMENTAL — Apple Foundation Models. kmp-toolkit is a library repo (no `:shared`/iosApp of
// its own), so this file can't be compiled here against a real KMP-exported framework — it ships
// as SOURCE for a consuming app to drop into its own Xcode target, the same shape Mileway shipped
// its own `FoundationModelsTextGenerator.swift`/`FoundationModelsDocumentAnalyzer.swift`. Written-
// correct, syntax-checked with (no framework import resolved, since none exists in this repo):
//   xcrun swiftc -parse FoundationModelsBridge.swift
// and, once copied into a consuming app's Xcode project (see README.md in this directory):
//   xcodebuild -project <App>.xcodeproj -scheme <App> \
//     -destination 'generic/platform=iOS Simulator' build
//
// Conforms to `NativeLlm` (Kotlin, `:ai`'s `com.siddharth.kmp.ai.NativeLlm` — exported as an ObjC
// protocol from your app's `:shared`-equivalent framework once you `export(project(":...:ai"))`
// there, same as Mileway's `shared/build.gradle.kts` already does for `core:ai`/`feature:agent`).
// `generate` is a Kotlin `suspend fun`, which classic ObjC-based Kotlin/Native interop exports as a
// completion-handler method — see Mileway's `FoundationModelsTextGenerator.swift` for the same
// shape applied to a simpler (non-streaming) seam.
//
// Unlike that whole-response bridge, THIS one streams: `LanguageModelSession.streamResponse(to:)`
// returns an AsyncSequence of CUMULATIVE partial responses (each element is the whole reply so far,
// not just the new suffix), diffed against the last-seen text and threaded through
// `NativeLlmStreamCallback.onPartial` as new suffixes arrive, so a UI can render tokens as Apple's
// on-device model produces them instead of waiting for the whole reply.
//
// Replace `import YourApp` below with your own framework's baseName (Mileway's is `import Mileway`)
// — see README.md.

import Foundation
import FoundationModels
import YourApp

final class FoundationModelsBridge: NSObject, NativeLlm {
    func isAvailable() -> Bool {
        guard #available(iOS 26.0, *) else { return false }
        return SystemLanguageModel.default.availability == .available
    }

    func generate(prompt: String, completionHandler: @escaping (String?, Error?) -> Void) {
        guard #available(iOS 26.0, *), isAvailable() else {
            completionHandler(nil, nil)
            return
        }
        Task {
            do {
                // ponytail: a fresh session per call — no cross-turn context/history threaded
                // through this bridge yet, same simplification Mileway's own Foundation Models
                // bridges made. Upgrade to one retained LanguageModelSession (carrying call
                // history) if multi-turn on-device context turns out to matter for your app.
                let session = LanguageModelSession()
                let response = try await session.respond(to: prompt)
                completionHandler(response.content, nil)
            } catch {
                // NativeLlm.generate never throws (see its KDoc) — degrade to nil rather than
                // propagating a Swift error across the interop boundary; the Kotlin side
                // (FoundationModelsOnDeviceLlm) turns nil into a typed AiFailure.EmptyReply.
                completionHandler(nil, nil)
            }
        }
    }

    func generateStream(prompt: String, callback: NativeLlmStreamCallback) -> NativeLlmCancelHandle {
        guard #available(iOS 26.0, *), isAvailable() else {
            callback.onComplete()
            return CancelToken(task: nil)
        }
        let task = Task {
            do {
                let session = LanguageModelSession()
                var lastText = ""
                for try await partial in session.streamResponse(to: prompt) {
                    // Checked on every element rather than relying solely on `for try await`'s
                    // cooperative cancellation propagating through the AsyncSequence, since
                    // streamResponse isn't documented to check cancellation itself.
                    if Task.isCancelled { break }
                    let full = partial.content
                    if full.count > lastText.count {
                        callback.onPartial(String(full.dropFirst(lastText.count)))
                        lastText = full
                    }
                }
                if !Task.isCancelled {
                    callback.onComplete()
                }
            } catch {
                callback.onError()
            }
        }
        return CancelToken(task: task)
    }
}

/// Cancelling the underlying `Task` is what actually stops `streamResponse`'s AsyncSequence
/// iteration (via the `Task.isCancelled` check in the loop above) — the model itself stops being
/// asked for more output, not just this bridge stopping listening.
private final class CancelToken: NativeLlmCancelHandle {
    private let task: Task<Void, Never>?

    init(task: Task<Void, Never>?) {
        self.task = task
    }

    func cancel() {
        task?.cancel()
    }
}
