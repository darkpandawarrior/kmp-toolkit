# Optional Google services

The base `:ai` and `:designsystem` artifacts no longer pull ML Kit or Google Pay into every Android app.
The existing implementations remain available in optional Android modules.

**Breaking on your next toolkit bump:** an app that skips the steps below still compiles, but on Android
it silently loses Gemini Nano (MediaPipe only) and renders the Google Pay unavailable message.

For Gaddi, PaymentsLab-KMP, Candidai, kmp-app-template, and other Play-enabled consumers:

- Add `com.siddharth.kmp:ai-mlkit:1.0.0` to the GMS flavor and register `mlKitLlmModule()` alongside
  `onDeviceLlmModule()`. The composite retains Gemini Nano first and MediaPipe second.
  `MlKitGenAiOnDeviceLlm` keeps its package, constructor, and behavior.
- Add `com.siddharth.kmp:designsystem-wallet-gms:1.0.0` to the GMS flavor and register
  `googlePayButtonModule()`. `WalletPayButton` keeps its signature and Google's official asset.

FOSS consumers omit both modules. MediaPipe remains available; Google Pay displays an unavailable
message if a caller incorrectly reports the wallet as available without installing its renderer.
Doori demonstrates both registrations in `app/src/gms/.../PlatformServicesKoinEntry.kt`.
