# iOS native bridge for `ai`'s on-device LLM seam

`:ai` has no `platform.FoundationModels.*` Kotlin/Native cinterop binding — Apple's Foundation
Models framework is Swift-macro-driven (`@Generable`, `LanguageModelSession.streamResponse`) with
no ObjC-compatible surface Kotlin/Native can import. So the real Foundation Models backend is a
Swift class your app writes and registers at startup, not something `:ai` can ship compiled.

This directory ships that Swift class as **source**, not as a compiled artifact — `kmp-toolkit` is
a library repo with no `:shared`/iosApp of its own to compile it against. Copy it into your app.

## What's here

- `FoundationModelsBridge.swift` — conforms to `NativeLlm` (`com.siddharth.kmp.ai.NativeLlm`,
  `:ai`'s commonMain interface), wrapping `LanguageModelSession` with real per-token streaming via
  `streamResponse(to:)`.

## Registering it (3 steps)

1. **Export `:ai` into your app's iOS framework.** In your `shared`-equivalent module's
   `build.gradle.kts`, alongside whatever else you already export:
   ```kotlin
   iosTarget.binaries.framework {
       baseName = "YourApp" // whatever your Xcode project already links against
       export(project(":ai")) // or "com.siddharth.kmp:ai" if you consume it as a published artifact
   }
   ```
   (Mileway's `shared/build.gradle.kts` already does this for `core:ai`/`feature:agent` — same
   pattern, one more `export(...)` line.)

2. **Copy `FoundationModelsBridge.swift`** into your Xcode project (e.g. `iosApp/iosApp/ai/`), and
   change its `import YourApp` line to your framework's actual `baseName` from step 1.

3. **Register it at startup**, once, before anything calls `onDeviceLlmModule()` — typically your
   `AppDelegate`:
   ```swift
   import YourApp

   @main
   class AppDelegate: UIResponder, UIApplicationDelegate {
       func application(_ application: UIApplication,
                         didFinishLaunchingWithOptions launchOptions: [...]) -> Bool {
           FoundationModelsBridgeKt.doFoundationModelsBridge().seam.generator = FoundationModelsBridge()
           // ... your existing Koin startKoin { modules(onDeviceLlmModule(), ...) } ...
           return true
       }
   }
   ```
   Kotlin/Native's ObjC export names a top-level `object`'s companion accessor per its own
   mangling rules for the module you built — check the generated `.h` for the exact symbol
   (Xcode's autocomplete on `FoundationModelsBridgeKt.` finds it fastest). Until this line runs,
   `FoundationModelsOnDeviceLlm.isAvailable()` reports `false` and the on-device chain falls
   through to whatever's next (MediaPipe's own bridge once it exists, or your app's heuristic
   fallback) — the same degrade this class already had as a hard stub, now driven by whether a
   bridge was actually registered instead of being permanently hardcoded.

## Verifying without a consumer app

This repo can't build an iOS app, so there's no `xcodebuild` target to run the Swift file through.
What it CAN check:
```bash
xcrun swiftc -parse ai/ios-bridge/FoundationModelsBridge.swift
```
Syntax-only (no framework resolution, since `YourApp`/`NativeLlm` don't exist as a real module
here) — catches a typo or malformed Swift, not a real type-check. Once copied into your app, build
it for real:
```bash
xcodebuild -project <App>.xcodeproj -scheme <App> -destination 'generic/platform=iOS Simulator' build
```

The Kotlin side (`InjectableNativeLlm`, `FoundationModelsOnDeviceLlm`'s delegate-or-degrade logic)
IS fully tested here with a fake `NativeLlm` — see `ai/src/commonTest/kotlin/com/siddharth/kmp/ai/InjectableNativeLlmTest.kt`.
Nothing about this bridge needs a real device or model download to unit-test; only
`LanguageModelSession` itself (Apple's framework, on real Apple Intelligence hardware) is outside
what this repo can verify.
