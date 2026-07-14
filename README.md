<div align="center">

# kmp-toolkit

### A Kotlin Multiplatform toolkit — a family of small, focused, production-grade libraries extracted from real apps.

![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20--Beta1-7F52FF?logo=kotlin&logoColor=white)
![Compose Multiplatform](https://img.shields.io/badge/Compose%20MP-1.12.0--beta01-4285F4?logo=jetpackcompose&logoColor=white)
![Ktor](https://img.shields.io/badge/Ktor-3.5.1-087CFA?logo=ktor&logoColor=white)
![Koin](https://img.shields.io/badge/Koin-4.2.2-2F855A)
![Platforms](https://img.shields.io/badge/platforms-Android%20%7C%20iOS%20%7C%20JVM%20%7C%20Wasm-4285F4)
![Modules](https://img.shields.io/badge/modules-9-0EA5E9)
![License](https://img.shields.io/badge/license-MIT-blue)

[**Portfolio**](https://cv-siddharth.vercel.app/) ·
[HireSignal](https://github.com/darkpandawarrior/HireSignal) ·
[PaymentsLab](https://github.com/darkpandawarrior/PaymentsLab) ·
[Mileway](https://github.com/darkpandawarrior/Mileway) ·
[Kursi](https://github.com/darkpandawarrior/Kursi) ·
[kmp-build-logic](https://github.com/darkpandawarrior/kmp-build-logic)

</div>

---

<details>
<summary><b>Contents</b></summary>

- [What this is](#what-this-is)
- [Modules](#modules)
- [Family architecture](#family-architecture)
- [Install](#install)
- [result](#result)
- [common](#common)
- [mvi-core](#mvi-core)
- [network](#network)
- [security](#security)
- [designsystem](#designsystem)
- [ai](#ai)
- [feedback](#feedback)
- [location](#location)
- [secrets](#secrets)
- [Repository structure](#repository-structure)
- [License](#license)

</details>

## What this is

`kmp-toolkit` is a monorepo consolidation of eight leaf libraries (plus `location`, added after the merge) that started life as separate
repos, each extracted from a production app the moment its logic was needed a second time —
`mvi-core` out of Mileway's `core:ui`, `security` out of PaymentsLab's `core:security`, `network`
and `designsystem` and `ai` out of HireSignal's `core:*` modules, `feedback` out of Kursi. None of
them were designed up front as a "platform" — each is the smallest reusable slice of a real screen,
published once the second consumer showed up.

Every module targets exactly the platforms its own consumers need — no module claims iOS support
it can't back up, no module ships a `wasmJs` target nobody asked for. The one inter-module
dependency in the whole family (`security` → `common`, for `AppLog`) is deliberate: everything else
is standalone by design, so adopting one leaf never drags in the rest.

Gradle convention plugins (the `androidKmpLibrary`, `composeCompiler` etc. plugins every
`build.gradle.kts` here applies) live in a separate repo,
[**kmp-build-logic**](https://github.com/darkpandawarrior/kmp-build-logic) — not part of this
monorepo.

## Modules

| Module | Coordinate | What it is | Platforms | Consumed by |
|---|---|---|---|---|
| [**result**](#result) | `com.siddharth.kmp:result` | `Result<D,E>` + `DataError` — typed, functional error handling | Android · JVM · iOS · Wasm | foundational — no consumers yet, Phase-4 adoption planned across the family |
| [**common**](#common) | `com.siddharth.kmp:common` | `AppLog` (Napier facade) + `DispatcherProvider` + `UiText` + `Formatters` | Android · JVM · iOS · Wasm | HireSignal, PaymentsLab (via `security`) |
| [**mvi-core**](#mvi-core) | `com.siddharth.kmp:mvi-core` | MVI ViewModel runtime — `BaseViewModel` / `StateViewModel` / `EffectEmitter` | Android · JVM · iOS · Wasm | HireSignal, PaymentsLab, Mileway, Kursi |
| [**network**](#network) | `com.siddharth.kmp:network` | Generic Ktor HTTP plumbing — client factory, retry, 401 handling, connectivity | Android · JVM · iOS | HireSignal (`core:network`) |
| [**security**](#security) | `com.siddharth.kmp:security` | Android app-hardening — Keystore, VAPT posture, `FLAG_SECURE` | Android only | PaymentsLab |
| [**designsystem**](#designsystem) | `com.siddharth.kmp:designsystem` | Brand-agnostic Compose Multiplatform primitives — tokens, theme controller, `MarkdownText` | Android · iOS · Wasm | HireSignal (`core:designsystem`) |
| [**ai**](#ai) | `com.siddharth.kmp:ai` | On-device LLM abstraction — ML Kit / MediaPipe / Foundation Models, one seam | Android · JVM · iOS | HireSignal (`core:ai`) |
| [**feedback**](#feedback) | `com.siddharth.kmp:feedback` | Game-feel toolkit — synthesised sound + haptics, four real backends | Android · JVM · iOS · Wasm | Kursi |
| [**location**](#location) | `com.siddharth.kmp:location` | Pure GPS-track math — Kalman smoothing, path simplification, dynamic polling, fix-quality scoring | Android · JVM · iOS · Wasm | Mileway (`feature:tracking`) |
| [**secrets**](#secrets) | *(docs only, not a Gradle module)* | SOPS + age encrypted-secrets vault + alias manifest | — | reference pattern, no dependents |

## Family architecture

```mermaid
graph TD
    NAPIER["Napier<br/>(third-party)"]
    KTOR["Ktor<br/>(third-party)"]
    KOIN["Koin<br/>(third-party)"]
    CMP["Compose Multiplatform<br/>(third-party)"]

    RESULT["result<br/>Result&lt;D,E&gt; + DataError"]
    COMMON["common<br/>AppLog · DispatcherProvider · UiText"]
    MVI["mvi-core<br/>BaseViewModel · StateViewModel"]
    NETWORK["network<br/>createHttpClient · ConnectivityChecker"]
    SECURITY["security<br/>SecureStore · DeviceIntegrity"]
    DESIGNSYSTEM["designsystem<br/>DesignTokens · ThemeController"]
    AI["ai<br/>OnDeviceLlm"]
    FEEDBACK["feedback<br/>SoundPlayer · HapticManager"]
    LOCATION["location<br/>KalmanSmoother · PathSimplifier"]

    NAPIER --> COMMON
    KTOR --> NETWORK
    KOIN --> AI
    KOIN --> SECURITY
    CMP --> DESIGNSYSTEM

    COMMON --> SECURITY
    RESULT -.->|"Phase-4 adoption planned"| NETWORK

    COMMON --> HIRESIGNAL["HireSignal"]
    NETWORK --> HIRESIGNAL
    DESIGNSYSTEM --> HIRESIGNAL
    AI --> HIRESIGNAL
    MVI --> HIRESIGNAL

    COMMON --> PAYMENTSLAB["PaymentsLab"]
    SECURITY --> PAYMENTSLAB
    MVI --> PAYMENTSLAB

    LOCATION --> MILEWAY["Mileway"]

    MVI --> KURSI["Kursi"]
    FEEDBACK --> KURSI

    style SECURITY fill:#3DDC84,color:#000
    style COMMON fill:#F97316,color:#fff
```

`security → common` is the only edge between two `kmp-toolkit` leaves — `security` pulls in
`AppLog` for its detectors' logging and nothing else. Every other module is standalone: dropping one
into an app never drags in a sibling.

## Install

As a monorepo, `kmp-toolkit` collapses what used to be many separate `includeBuild` composite
builds (one `dependencySubstitution` block per leaf repo, each guarding against a `:lib` path
collision) into **one** `includeBuild` with one substitution per module:

```kotlin
// settings.gradle.kts
includeBuild("external/kmp-toolkit") {
    dependencySubstitution {
        substitute(module("com.siddharth.kmp:result")).using(project(":result"))
        substitute(module("com.siddharth.kmp:common")).using(project(":common"))
        substitute(module("com.siddharth.kmp:mvi-core")).using(project(":mvi-core"))
        substitute(module("com.siddharth.kmp:network")).using(project(":network"))
        substitute(module("com.siddharth.kmp:security")).using(project(":security"))
        substitute(module("com.siddharth.kmp:designsystem")).using(project(":designsystem"))
        substitute(module("com.siddharth.kmp:ai")).using(project(":ai"))
        substitute(module("com.siddharth.kmp:feedback")).using(project(":feedback"))
        substitute(module("com.siddharth.kmp:location")).using(project(":location"))
    }
}
```

Module paths are now natural — `:common`, `:network`, `:mvi-core`, and so on. Back when each module
was its own repo, every leaf had to publish a unique `<name>-lib` subproject path (`:common-lib`,
`:result-lib`, `:security-lib`, `:feedbacklib`, …) because Gradle's `dependencySubstitution`
resolves by path only, and two composite builds both exposing a bare `:lib` collided regardless of
`includeBuild` order. In a monorepo every module already has a distinct top-level path, so that
workaround is gone.

Consumers that resolve from Maven instead of building from source pull individual modules the plain
way, same coordinate per module:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.siddharth.kmp:common:1.0.0")
    implementation("com.siddharth.kmp:mvi-core:1.0.0")
    // ...one line per module actually needed
}
```

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/darkpandawarrior/kmp-toolkit")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}
```

---

## result

![result](docs/assets/result-banner.svg)

`kotlin.Result` only carries a `Throwable` in its failure arm, which pushes callers back toward
`try`/`catch` and string-matching on messages. `result` types the failure arm as `E` — usually a
sealed [`DataError`](result/src/commonMain/kotlin/com/siddharth/kmp/result/DataError.kt) — so a
`when` over the error is exhaustive at compile time, and a ViewModel or repository can map, chain,
and react to failures with the same functional operators it already uses for the success path.

```kotlin
import com.siddharth.kmp.result.Result
import com.siddharth.kmp.result.DataError
import com.siddharth.kmp.result.map
import com.siddharth.kmp.result.flatMap
import com.siddharth.kmp.result.onSuccess
import com.siddharth.kmp.result.onFailure

fun fetchUser(id: String): Result<User, DataError> =
    if (id.isBlank()) Result.Failure(DataError.Local.NOT_FOUND) else Result.Success(User(id))

fun fetchProfile(id: String): Result<Profile, DataError> =
    fetchUser(id).flatMap { user -> loadProfile(user) }

fetchProfile("42")
    .map { it.displayName }
    .onSuccess { name -> println("Loaded $name") }
    .onFailure { error ->
        when (error) {
            DataError.Network.NO_INTERNET -> showOfflineBanner()
            DataError.Network.UNAUTHORIZED -> redirectToLogin()
            else -> showGenericError()
        }
    }

// Success-only payload discarded, keeping just success-vs-failure:
val saveResult: EmptyResult<DataError> = fetchProfile("42").asEmpty()
```

`DataError` is a `sealed interface` split into `Network` and `Local` enum arms; an app can extend it
with its own arms (e.g. a `Validation` error) by implementing the interface directly.

| Member | Signature | What it does |
|---|---|---|
| `Result.Success<D>` | `data class Success<out D>(val data: D)` | The success arm |
| `Result.Failure<E>` | `data class Failure<out E>(val error: E)` | The typed failure arm |
| `EmptyResult<E>` | `typealias EmptyResult<E> = Result<Unit, E>` | A result whose success carries no payload |
| `map` | `(D) -> R) -> Result<R, E>` | Transform the success value; failures pass through |
| `flatMap` | `(D) -> Result<R, E>) -> Result<R, E>` | Chain into another `Result`; failures short-circuit |
| `fold` | `(onSuccess, onFailure) -> R` | Collapse both arms into one value |
| `mapError` | `(E) -> F) -> Result<D, F>` | Transform the failure value; successes pass through |
| `onSuccess` / `onFailure` | `(D/E) -> Unit) -> Result<D, E>` | Side-effect, returns receiver for chaining |
| `getOrNull` / `errorOrNull` | `() -> D?` / `() -> E?` | Success or failure value, `null` on the other arm |
| `asEmpty` | `() -> EmptyResult<E>` | Discard the success payload |
| `DataError.Network` | enum | `REQUEST_TIMEOUT`, `TOO_MANY_REQUESTS`, `NO_INTERNET`, `SERVER_ERROR`, `SERIALIZATION`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `UNKNOWN` |
| `DataError.Local` | enum | `DISK_FULL`, `NOT_FOUND`, `UNKNOWN` |

`result` is standalone — zero dependencies on any other `kmp-toolkit` module — which is what makes
it the foundation the rest of the family is meant to build on. No module consumes it yet; it ships
first so the typed-error contract exists before `network` and `security` standardize their failure
arms on it in a later phase, instead of each inventing its own `sealed class` per app.

Pure Kotlin, `commonMain`-only — `Result` and `DataError` carry no coroutine, lifecycle, or platform
dependency, so every target (Android `minSdk 24` / `compileSdk 37`, JVM, iOS, `wasmJs` browser)
compiles from the same source set with nothing to bridge.

## common

![common](docs/assets/common-banner.svg)

Two things get reinvented in almost every KMP app: a logging call site, and a way to swap
`CoroutineDispatcher`s under test. `android.util.Log` doesn't exist in `commonMain`, so logging
either forks per-platform or every module ends up importing a third-party logger directly. And a
`ViewModel` or repository that calls `Dispatchers.IO` at the call site can't have its concurrency
substituted in a test.

`common` closes both gaps with the smallest possible surface: `AppLog`, a message-first facade over
[Napier](https://github.com/AAkira/Napier) so call sites never import Napier directly (the backend
is swappable in one place), and `DispatcherProvider`, an injectable dispatcher set so production
code takes dispatchers as a constructor parameter instead of reaching for a global.

```kotlin
import com.siddharth.kmp.common.AppLog
import com.siddharth.kmp.common.DispatcherProvider
import com.siddharth.kmp.common.StandardDispatchers
import kotlinx.coroutines.withContext

AppLog.d("loaded ${users.size} users", tag = "Repo")
AppLog.w("retrying after timeout", tag = "Repo")
AppLog.e("failed to save", throwable = e, tag = "Repo")

class UserRepository(
    private val dispatchers: DispatcherProvider = StandardDispatchers,
) {
    suspend fun load(): List<User> = withContext(dispatchers.io) {
        // ... blocking / IO work
    }
}

// In a test: substitute a StandardTestDispatcher-backed DispatcherProvider instead of
// StandardDispatchers, so withContext(dispatchers.io) runs on the test scheduler.
```

| Member | Signature | What it does |
|---|---|---|
| `AppLog.d` / `.i` | `(message: String, tag: String? = null)` | Debug / info log via `Napier.d` / `Napier.i` |
| `AppLog.w` / `.e` | `(message: String, throwable: Throwable? = null, tag: String? = null)` | Warn / error log via `Napier.w` / `Napier.e` |
| `DispatcherProvider` | `interface { main; default; io }` | The injectable dispatcher seam |
| `StandardDispatchers` | `object : DispatcherProvider` | `main` → `Dispatchers.Main`, `default`/`io` → `Dispatchers.Default` |

`StandardDispatchers.io` maps to `Dispatchers.Default`, not `Dispatchers.IO` — `Dispatchers.IO` is
JVM/Android-only and doesn't exist in `commonMain` for wasm/native. An Android-specific data layer
can bind a real `Dispatchers.IO`-backed `DispatcherProvider` for blocking IO if it ever needs one;
`StandardDispatchers` stays the multiplatform-safe default.

`common` has no dependency on any other `kmp-toolkit` module — it only pulls in Napier and
kotlinx-coroutines. `security` consumes it transitively for `AppLog`; HireSignal uses it app-wide.
`commonMain`-only implementation across Android (`minSdk 24` / `compileSdk 37`), JVM, iOS, and
`wasmJs` — only Napier's own platform backends differ underneath.

## mvi-core

![mvi-core](docs/assets/mvi-core-banner.svg)

Three small presentation-layer primitives kept getting re-copied between KMP projects (Mileway,
PaymentsLab, Kursi) every time a new screen needed unidirectional state. `mvi-core` pulls them out
once, extracted from Mileway's `core:ui` MVI base.

| Class | Use when | Depends on |
|---|---|---|
| `BaseViewModel<S, E, A>` | Full MVI screen: state + one-shot effects (nav/toast) + config-surviving broadcast + explicit `Action` type | `androidx.lifecycle.ViewModel` |
| `StateViewModel<S>` | Simple screen: just state, no one-shot effects yet | `androidx.lifecycle.ViewModel` |
| `EffectEmitter<E>` | One-shot effect delivery for a presenter that is **not** built on `androidx.lifecycle.ViewModel` | Plain `CoroutineScope`, nothing else |

Start with `StateViewModel`. Move to `BaseViewModel` the moment the screen needs a one-shot
navigation/toast effect. Reach for `EffectEmitter` standalone when the host isn't an
`androidx.lifecycle.ViewModel` at all.

`BaseViewModel<S, E, A>` runs two independent one-way loops out of the same `onAction`: a state loop
the screen reads on every recomposition, and a pair of effect channels with different delivery
guarantees. Names below match `mvi-core/src/commonMain/kotlin/com/siddharth/kmp/mvi/BaseViewModel.kt` exactly.

```mermaid
flowchart TD
    View["View / screen"] -->|"onAction(action: A)"| Reducer["setState { S.() -> S }"]
    Reducer -->|"_state.value = _state.value.reducer()"| StateFlow["state: StateFlow&lt;S&gt;"]
    StateFlow --> View

    View -.->|"triggers via onAction"| EmitEffect["emitEffect(effect: E)"]
    EmitEffect -->|"viewModelScope.launch { _effect.send(e) }"| Channel["_effect: Channel&lt;E&gt;(BUFFERED)"]
    Channel -->|"effect: Flow&lt;E&gt;<br/>consumed exactly once"| View

    View -.->|"triggers via onAction"| EmitBroadcast["emitBroadcast(effect: E)"]
    EmitBroadcast -->|"_broadcast.tryEmit(e)"| SharedFlow["_broadcast: MutableSharedFlow&lt;E&gt;<br/>replay=0, extraBufferCapacity=8, DROP_OLDEST"]
    SharedFlow -->|"broadcast: SharedFlow&lt;E&gt;<br/>survives config change"| View
```

`StateViewModel<S>` is the same reducer loop with the two effect channels removed. `EffectEmitter<E>`
is just the `Channel<E>` branch, taking a caller-supplied `CoroutineScope` instead of
`viewModelScope`.

```kotlin
// module build.gradle.kts
dependencies {
    implementation("com.siddharth.kmp:mvi-core:1.0.0")
}
```

Targets: `androidTarget`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `wasmJs` — the union PaymentsLab
and Kursi need. All three classes live in `commonMain`; `androidx.lifecycle:lifecycle-viewmodel`
publishes for every target in that union (including `wasmJs`), so no intermediate source set split
was needed.

**Technical notes, grounded in the actual source:**

- **State updates aren't CAS-guarded.** `setState` does `_state.value = _state.value.reducer()` — a
  plain `MutableStateFlow` assignment, not the compare-and-swap loop `MutableStateFlow.update { }`
  gives you. Fine under the contract this library assumes (one writer: whatever dispatches into
  `onAction`), but calling `setState` concurrently from two coroutines at once can lose an update —
  a real gap to close with `.update { }` if a screen ever needs multiple concurrent writers.
- **Two effect channels, two different delivery contracts.** `emitEffect` wraps a
  `Channel<E>(Channel.BUFFERED)` exposed as `effect: Flow<E>` via `receiveAsFlow()` — each value goes
  to exactly one collector and never repeats (right for navigation/toast, but unread if the screen
  isn't currently collecting). `emitBroadcast` instead `tryEmit`s into a
  `MutableSharedFlow<E>(replay = 0, extraBufferCapacity = 8, onBufferOverflow = DROP_OLDEST)` — no
  history for new subscribers, but it outlives a configuration-change screen recreation.
- **Coroutine scope is minimal, not incidental.** `viewModelScope` is only touched once, inside
  `emitEffect`, wrapping the suspending `Channel.send` so `onAction` itself doesn't need to be a
  suspend function; `emitBroadcast` needs no scope at all since `tryEmit` never suspends given the
  buffer above.
- **Build system detail:** the Android target is wired through AGP's own
  `com.android.kotlin.multiplatform.library` plugin, not the older `com.android.library` +
  `org.jetbrains.kotlin.multiplatform` combination — there's no explicit `androidTarget()` call.

## network

![network](docs/assets/network-banner.svg)

Every app that talks HTTP over Ktor re-solves the same handful of problems: content negotiation, a
sane retry policy, a request timeout that doesn't kill a long-lived stream, and a single choke point
that reacts to a 401 without looping on the login route itself. None of that is app-specific — it's
the same wiring whether the DTOs are job postings or payment orders.

`network` is that wiring, and nothing else. It ships a configured `HttpClient` factory, the platform
engine bindings (OkHttp / Darwin / CIO), a lenient shared `Json`, and three small seam interfaces
(`BaseUrlProvider`, `TokenProvider`, `UnauthorizedHandler`) so base URL and auth resolve at call
time. It deliberately carries **no** app API surface — endpoints, DTOs, and the typed API layer stay
in the consumer, same as `result` carries no app-specific error types.

```kotlin
import com.siddharth.kmp.network.createHttpClient
import com.siddharth.kmp.network.networkJson
import com.siddharth.kmp.network.BaseUrlProvider
import com.siddharth.kmp.network.TokenProvider
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.call.body

// One shared client per process — content negotiation, INFO logging, retry, 30s timeout, and a
// 401 observer are all wired in.
val client = createHttpClient(onUnauthorized = { session.clearToken() })

class JobsApi(
    private val client: HttpClient,
    private val baseUrl: BaseUrlProvider,
    private val token: TokenProvider,
) {
    suspend fun listings(): List<JobDto> =
        client.get("${baseUrl.baseUrl()}/jobs") {
            token.token()?.let { header("Authorization", "Bearer $it") }
        }.body()
}

// The consumer's own serializer setup reuses the same lenient Json:
val decoded = networkJson.decodeFromString<JobDto>(rawJson)
```

`ConnectivityChecker` gates a `refresh()` before the network call is even made — bind
`AndroidConnectivityChecker(context)` on Android (real `ConnectivityManager` reachability, with
`NET_CAPABILITY_VALIDATED`) or `AlwaysOnlineConnectivityChecker` on jvm/iOS.

| Member | Signature | What it does |
|---|---|---|
| `createHttpClient` | `(engine: HttpClientEngine = httpClientEngine(), onUnauthorized: suspend () -> Unit = {}) -> HttpClient` | Content negotiation, INFO logging, bounded exponential-backoff retry on 5xx/IO failures, 30s request timeout, 401 observer (skips `/auth/` routes) |
| `networkJson` | `Json` | Lenient, forward-compatible: unknown keys ignored, defaults encoded, explicit nulls off |
| `BaseUrlProvider` | `fun interface { suspend fun baseUrl(): String }` | Resolves the server base URL per call |
| `TokenProvider` | `fun interface { suspend fun token(): String? }` | Resolves an optional bearer token per call |
| `UnauthorizedHandler` | `fun interface { suspend fun onUnauthorized() }` | Invoked once per 401 on a non-auth route |
| `ConnectivityChecker` | `interface { fun isOnline(): Boolean }` | Cheap online check to gate a sync/refresh |
| `AndroidConnectivityChecker` | `class(context: Context) : ConnectivityChecker` | Real `ConnectivityManager` reachability (Android) |
| `AlwaysOnlineConnectivityChecker` | `object : ConnectivityChecker` | Naive default (jvm/iOS) — lets the HTTP call fail-and-retry instead of probing |

Platform HTTP engines are wired transparently via `internal expect fun httpClientEngine()`: OkHttp
on Android, Darwin on iOS, CIO on JVM — `createHttpClient()`'s default argument picks the right one.

`network` is standalone — no dependency on any other `kmp-toolkit` module, only Ktor and
kotlinx-serialization. HireSignal's `core:network` module builds on it; the app layers its own typed
API + DTOs on top. Planned: mapping `HttpRequestRetry`/`ResponseException` failures onto `result`'s
`DataError.Network` arms, so a consumer's repository layer gets a typed `Result<D, DataError>`
straight out of the client instead of catching `ResponseException` itself.

Targets: Android (`minSdk 24` / `compileSdk 37`, OkHttp engine), JVM (CIO engine), iOS (Darwin
engine). No `wasmJs` — the OkHttp/Darwin/CIO engine split is JVM/Android/iOS by design; a browser
target would need the Ktor `Js` engine wired in separately.

## security

![security](docs/assets/security-banner.svg)

Payments and other sensitive-data apps on Android need the same handful of defenses every time:
encrypt what's saved, know when the device is compromised, stop a screenshot from leaking a card
number, and keep the reaction to all of that configurable per build (strict in release, lenient for
a VAPT auditor who needs to run on a rooted, hooked device on purpose).

`security` is that toolkit, extracted from a production payments app's `core:security` module. It's
**Android-only by nature, not by accident** — every defense here is a real `android.*` API
(`AndroidKeyStore`, `FLAG_SECURE`, `/proc/self/status`, `TrustManager` introspection), so there's no
`expect`/`actual` layer pretending this could run anywhere else.

Detection and enforcement are deliberately split: a `SecurityAuditor` only ever answers *"what did
we find?"* A `SecurityPolicy` answers *"what should we do about it?"*, driven by a swappable
`SecurityPosture` — so a release build can block on root while a compliance-test build only warns,
without touching a single detector.

**What's in the box:**

- **`SecureStore` / `KeystoreSecureStore`** — key/value secret storage; encrypts every value with
  AES-256-GCM under an `AndroidKeyStore` key that never leaves the TEE/StrongBox, and stores even
  the *keys* as SHA-256 hashes — a filesystem dump of the private prefs file is inert without the
  device's hardware-backed key.
- **`KeystoreCrypto`** — the explicit `AES/GCM/NoPadding` primitive behind the store: a hardware key
  generated once via `KeyGenParameterSpec`, an IV-prefixed `Base64(IV(12) || ciphertext+tag)` wire
  format, and a GCM auth tag that makes `decrypt()` double as a tamper check.
- **`DeviceIntegrity` / `AndroidDeviceIntegrity`** — root, emulator, and debugger posture as a
  `SecurityReport`. The matching rules (`isRootTag`, `isEmulatorBuild`) are pure top-level functions,
  JVM-unit-tested against known fingerprints, no device or Robolectric required.
- **`AntiDebugDetector`** — `Debug.isDebuggerConnected()`, `Debug.waitingForDebugger()`, and a
  `TracerPid` parse from `/proc/self/status` (harder to spoof than the `Debug.*` calls alone).
- **`AntiHookDetector`** — Frida/Xposed/LSPosed heuristics: suspicious thread names, a
  `/proc/self/maps` scan for loaded Frida/substrate libraries, a loopback connect attempt on Frida's
  default ports (`27042`/`27043`), and reflective probes for Xposed/LSPosed marker classes.
- **`AntiSslBypassDetector`** — introspects the platform's default `TrustManager` and
  `HostnameVerifier` for permissive/custom implementations, plus a memory-maps scan for known
  SSL-unpinning native libraries.
- **`SecurityAuditor` / `SecurityAudit`** — the one `suspend fun audit()` a launch sequence calls.
  Composes every detector above, applies `SecurityConfig` VAPT `bypass*` flags at the aggregation
  layer (detection stays fully logged even on a bypassed build — only the gate-feeding boolean is
  cleared).
- **`SecurityPolicy` / `SecurityPosture` / `SecurityDecision`** — a pure `(Audit, Posture) ->
  Decision` fold. Ships `SecurityPosture.strict()` (blocks on root/hook/SSL-bypass/debugger, warns on
  emulator) and `.lenient()` (dev/debug stance).
- **`AppSecurityManager` / `SecureScreen`** — the screen-facing trio: `FLAG_SECURE` (blocks
  screenshots/recording, blanks the recents thumbnail), tapjacking protection via
  `filterTouchesWhenObscured`, and a background-overlay hook re-asserting `FLAG_SECURE`.
  `SecureScreen` is the Compose-scoped equivalent for one composable.
- **`PaymentCertificatePinning`** — an OkHttp `CertificatePinner` config template for payment-gateway
  API hosts, with placeholder SPKI pins and the exact `openssl` one-liner to replace them.
- **`securityModule(config)`** — one Koin module wiring the entire graph.

```kotlin
// Application.onCreate
startKoin {
    androidContext(this@App)
    modules(securityModule(SecurityConfig(/* bypass* flags from BuildConfig for VAPT builds */)))
}

val appSecurityManager: AppSecurityManager by inject()
appSecurityManager.install(this) // once — re-flags FLAG_SECURE on background/foreground

// Activity.onCreate
appSecurityManager.applySecurityToActivity(this) // FLAG_SECURE + tapjacking protection

// Compose — screen-scoped FLAG_SECURE instead of the whole Activity
SecureScreen(enabled = true) {
    CardEntryScreen()
}

// Launch-time posture check, gating card entry on a compromised device
val auditor: SecurityAuditor by inject()
val audit = auditor.audit()
val decision = SecurityPolicy.evaluate(audit, SecurityPosture.strict())
if (decision.shouldBlock) {
    // refuse to load card entry — decision.summary() e.g. "ROOT→BLOCK, EMULATOR→WARN"
}

// Keystore-backed secret storage
val secureStore: SecureStore by inject() // KeystoreSecureStore under the hood
secureStore.putString("kmp_secure_store", token)
val saved = secureStore.getString("kmp_secure_store")
```

| Type | What it does |
|---|---|
| `SecureStore` / `KeystoreSecureStore` | AES-256-GCM key/value secret storage; keys stored as SHA-256 hashes |
| `KeystoreCrypto` | `AES/GCM/NoPadding` primitive — IV-prefixed Base64 wire format |
| `DeviceIntegrity` / `AndroidDeviceIntegrity` | Root, emulator, debugger posture → `SecurityReport` |
| `AntiDebugDetector` | `TracerPid`, `Debug.isDebuggerConnected()`, `FLAG_DEBUGGABLE` |
| `AntiHookDetector` | Frida thread names, `/proc/self/maps`, Frida ports, Xposed/LSPosed marker classes |
| `AntiSslBypassDetector` | Permissive `TrustManager`/`HostnameVerifier` introspection, native SSL-bypass libs |
| `SecurityAuditor` / `SecurityAudit` | One `suspend fun audit()` composing every detector |
| `SecurityPolicy` / `SecurityPosture` / `SecurityDecision` | Pure `(Audit, Posture) -> Decision`; `strict()` / `lenient()`, `ALLOW`/`WARN`/`BLOCK` |
| `AppSecurityManager` | `FLAG_SECURE`, tapjacking, background re-flag overlay |
| `SecureScreen` (Composable) | Screen-scoped `FLAG_SECURE` via `DisposableEffect` |
| `PaymentCertificatePinning` | OkHttp `CertificatePinner` template — placeholder SPKI pins |
| `securityModule(config)` | Koin module wiring the whole graph |

`security` depends on `common` for `AppLog` — its only cross-family dependency — and is consumed
today by PaymentsLab, which extracted it from its own former `core:security` module. Android only —
`compileSdk 37`, `minSdk 24`; deliberate, since every defense wraps a concrete `android.*` API with
no cross-platform equivalent.

## designsystem

![designsystem](docs/assets/designsystem-banner.svg)

A real design system is *branded* — its palette, logo mark and typography are the product. Those
belong in the app, not a shared library. But underneath the brand sits a layer that every Compose
Multiplatform app re-implements identically: a spacing/shape token scale, a dark-mode state holder
that survives process death, and a handful of brand-neutral building blocks.

`designsystem` is exactly that layer, and nothing brand-specific. It carries **no** palette, **no**
logo, **no** app typography — you keep those. It ships the plumbing you'd otherwise copy-paste into
every new app, so your own `AppTheme`/`AppPalette` is the only design code you actually write.

**Spacing/shape tokens** — one scale, no magic numbers scattered across screens:

```kotlin
import com.siddharth.kmp.designsystem.DesignTokens

Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.m)) {
    Card(shape = DesignTokens.Shape.card) { /* ... */ }
}
```

**Theme-mode controller** — dark-first, persists the user's choice through a swappable `ThemeStore`
seam (default is in-memory; bind a DataStore-backed `ThemeStore` in your DI so the choice survives
process death, with no change to callers):

```kotlin
import com.siddharth.kmp.designsystem.ThemeController

val theme = ThemeController(store = myDataStoreThemeStore) // or default InMemoryThemeStore
val isDark by theme.isDark.collectAsState()

MaterialTheme(colorScheme = if (isDark) MyAppDarkColors else MyAppLightColors) {
    // your app — designsystem never dictates the colors
}

// Settings toggle:
Switch(checked = isDark, onCheckedChange = { theme.setDark(it) })
```

**Building-block composables** — `MarkdownText` (a lightweight markdown → Compose renderer, tables
included) and `ComingSoonDialog` (a brand-neutral "not built yet" dialog):

```kotlin
MarkdownText("**Offer:** ₹32L · _remote_\n\n| role | level |\n|---|---|\n| Android | Lead |")
```

| Member | Kind | What it does |
|---|---|---|
| `DesignTokens` | `object` | Brand-agnostic spacing / shape / size scale |
| `ThemeStore` | `interface` | Persistence seam for the dark-mode choice (`darkOverride()` / `setDark()`) |
| `InMemoryThemeStore` | `object : ThemeStore` | Default seam — holds the choice for the process lifetime |
| `ThemeController` | `class` | Dark-first theme-mode state holder exposing `isDark: StateFlow<Boolean>`, `setDark()`, `toggle()` |
| `MarkdownText` | `@Composable` | Lightweight markdown → Compose renderer (headings, emphasis, lists, tables) |
| `ComingSoonDialog` | `@Composable` | Brand-neutral placeholder dialog |

`designsystem` depends only on Compose Multiplatform + coroutines — no other `kmp-toolkit` module.
An app layers its own brand (palette, logo, typography) and its app-coupled components on top; those
stay in the app, same reason `network` keeps your API and DTOs in the app. HireSignal's
`core:designsystem` adds `HireSignalPalette` / `HireSignalTheme` / status-colored components on top,
brand staying in-app.

Targets: Android (`minSdk 24` / `compileSdk 37`), iOS (`iosArm64`, `iosSimulatorArm64`), Wasm
(JS/browser). JVM/desktop isn't a target here (the consuming design systems don't need it); add
`jvm()` if yours does.

## ai

![ai](docs/assets/ai-banner.svg)

Every app that wants on-device inference hits the same wall: the backends are all different (ML Kit
GenAI / Gemini Nano on AICore devices, MediaPipe LLM Inference for broad Gemma coverage, Foundation
Models on iOS 26, nothing at all on desktop), each has its own residency/availability dance, and any
of them can just fail. The domain code that *uses* the model shouldn't know or care which one ran.

`ai` collapses all of that behind one seam: `OnDeviceLlm` — `isAvailable()` + `suspend fun
generate(prompt): String?`. `generate` returns `null` on any miss (no model resident, declined,
failed), so your caller degrades to its own heuristic tier instead of throwing. A
`CompositeOnDeviceLlm` chains backends in preference order and returns the first non-null answer.
Your feature-specific prompt-building and output-parsing stay in your app; this carries only the
plumbing to *run* a prompt.

```kotlin
import com.siddharth.kmp.ai.onDeviceLlmModule
import com.siddharth.kmp.ai.OnDeviceLlm

startKoin { modules(onDeviceLlmModule(), myFeatureModule) }

class JobSummarizer(private val llm: OnDeviceLlm) {
    suspend fun summarize(jd: String): String =
        llm.generate("Summarize this job description in one line:\n$jd")
            ?: heuristicSummary(jd) // graceful fallback — never throws
}
```

Compose your own backend order (e.g. add a remote tier) with `CompositeOnDeviceLlm`:

```kotlin
val llm: OnDeviceLlm = CompositeOnDeviceLlm(listOf(mlKitTier, mediaPipeTier, myRemoteTier))
// isAvailable() is true if ANY backend is; generate() returns the first non-null result.
```

| Member | Signature | What it does |
|---|---|---|
| `OnDeviceLlm` | `interface { fun isAvailable(): Boolean; suspend fun generate(prompt: String): String? }` | The single seam — text in, text out, `null` on any miss |
| `UnavailableOnDeviceLlm` | `object : OnDeviceLlm` | The always-off floor (desktop / pre-AI devices) |
| `CompositeOnDeviceLlm` | `class(backends: List<OnDeviceLlm>)` | Tries backends in order; first non-null wins |
| `onDeviceLlmModule` | `expect fun(): Module` | Per-platform Koin bindings for the right backend(s) |
| `ModelManager` | `interface { fun models(): List<ModelInfo>; fun observe(id): Flow<ModelInfo> }` | On-demand model download/residency status |
| `ModelInfo` / `ModelDownloadState` | data / enum | Model id, size, `ABSENT/DOWNLOADING/READY/FAILED`, progress |

Model files are **downloaded on demand at runtime**, never shipped in the repo.

`ai` is standalone — depends only on coroutines + Koin (and, on Android, the ML Kit GenAI +
MediaPipe SDKs). Your domain "intelligence" layer (prompt templates, output parsing, heuristics)
stays in your app and consumes this seam. HireSignal's `core:ai` builds `JobIntelligence` on top.

| Target | Backend |
|---|---|
| Android | ML Kit GenAI (Gemini Nano) → MediaPipe (Gemma), composed with fallback |
| iOS | Foundation Models seam (`iosArm64`, `iosSimulatorArm64`) |
| JVM / Desktop | `UnavailableOnDeviceLlm` — the heuristic tier upstream always answers |

## feedback

![feedback](docs/assets/feedback-banner.svg)

A card game lives or dies on game feel: a stamp needs a thud, a steal needs a heavier thud, a win
needs a fanfare, and a phone in someone's hand should buzz when a claim gets caught in a lie. None of
that has anything to do with any one screen — it's a cross-cutting concern that every platform
target of a KMP game needs the same small vocabulary for.

`feedback` is that vocabulary: a coarse, intentionally small set of `SoundKey`s and `HapticPattern`s
behind one `SoundPlayer` contract, with a **real, audible** actual on every target — including the
two platforms (JVM desktop, wasmJs web) that don't usually get game-feel treatment. Nothing is a
bundled asset; every sound is a few hundred bytes of synthesised PCM rendered at startup, so there's
no audio pipeline to wire and no `.wav` files to ship.

**What's in the box:**

- **`SoundKey`** — the SFX vocabulary: `Stamp` (rubber-stamp slam), `Coin` (economic actions),
  `Thud` (steals / heavier actions), `Win` (the fanfare). One per *feel*, not per moment.
- **`HapticPattern`** — the device-buzz vocabulary: `None`, `Tick` (light, routine), `Thud` (firm),
  `DoubleBuzz` (a "caught lying" reveal), `HeavyLong` (Coup / elimination / win).
- **`SoundPlayer`** — the `expect` contract: `playSound(key)`, `haptic(pattern)`, `release()`. Every
  method is fire-and-forget and must never throw — a missing audio device, a denied haptic
  permission, or a headless CI box all degrade to a silent no-op by contract, not by accident.
- **`defaultSoundPlayer()`** — the platform factory. Returns the real actual for whichever target is
  compiling.
- **`HapticManager`** — a small facade over `HapticPattern` for callers that think in terms of
  `HapticStyle` (`Light`/`Medium`/`Heavy`) or `HapticNotificationType` (`Success`/`Warning`/`Error`).
- **`ShareResult`** — `expect fun shareGameResult(text: String)`. Real on Android (an `ACTION_SEND`
  chooser Intent); a documented no-op on JVM/iOS/wasmJs today.
- **`NotificationPermissionState`** — a `StateFlow<NotificationPermission>` (`GRANTED` / `DENIED` /
  `NOT_ASKED`) any screen can observe, updated by the host app once it knows the real OS answer.
- **`NotificationChannelManager`** (Android) — creates the `game_invites` / `system`
  `NotificationChannel`s the app needs before posting a notification on API 26+.

```kotlin
// Android — install once so haptics can reach the system Vibrator (sound works without this)
FeedbackAndroid.install(applicationContext)

// Any platform — the one player instance for the app's lifetime
val player: SoundPlayer = defaultSoundPlayer()

// A claim gets stamped onto the felt
player.playSound(SoundKey.Stamp)
player.haptic(HapticPattern.Tick)

// A steal lands
player.playSound(SoundKey.Thud)
player.haptic(HapticPattern.Thud)

// Someone gets caught lying
player.haptic(HapticPattern.DoubleBuzz)

// Game over
player.playSound(SoundKey.Win)
player.haptic(HapticPattern.HeavyLong)
player.release() // idempotent — safe to call more than once

// Or via the style/notification-shaped facade
HapticManager.hapticImpact(HapticStyle.Medium)                  // -> HapticPattern.Thud
HapticManager.hapticNotification(HapticNotificationType.Success) // -> HapticPattern.HeavyLong

// Share a result (Android: real share sheet; other targets: documented no-op)
shareGameResult("Won as Trader — 3 coups survived")

// Observe the permission state a screen needs to react to
NotificationPermissionState.permission.collect { permission -> /* … */ }
```

| Type | What it does |
|---|---|
| `SoundKey` | SFX vocabulary — `Stamp`, `Coin`, `Thud`, `Win` |
| `HapticPattern` | Haptic vocabulary — `None`, `Tick`, `Thud`, `DoubleBuzz`, `HeavyLong` |
| `SoundPlayer` | `expect` contract — `playSound(key)`, `haptic(pattern)`, `release()`; never throws |
| `defaultSoundPlayer()` | Platform factory returning the real actual for the compiling target |
| `HapticManager` | `hapticImpact(HapticStyle)` / `hapticNotification(HapticNotificationType)` facade |
| `shareGameResult(text)` | `expect fun` — real Android share-sheet Intent; no-op elsewhere today |
| `NotificationPermissionState` | `StateFlow<NotificationPermission>` — `GRANTED`/`DENIED`/`NOT_ASKED` |
| `NotificationChannelManager` (Android) | Creates the `game_invites` / `system` notification channels |
| `FeedbackAndroid.install(context)` | Optional Android hook — unlocks the real system `Vibrator` for haptics |

Every target genuinely produces sound or haptics — nothing is a silent stub by default:

| Target | Sound | Haptics |
|---|---|---|
| **JVM** (desktop) | `javax.sound.sampled` — PCM tone synth streamed on a `SourceDataLine` | No-op (no vibration motor) |
| **Android** | Synthesised PCM streamed on a short-lived `AudioTrack` — no bundled assets, no `Context` required | Real `Vibrator`/`VibratorManager` (`VibrationEffect` on API 26+) once `FeedbackAndroid.install()` is called |
| **iOS** | In-memory WAV built from synthesised samples, played via `AVAudioPlayer` | `UIImpactFeedbackGenerator` (light/medium/heavy) + `UINotificationFeedbackGenerator` for `DoubleBuzz` |
| **wasmJs** (web) | Web Audio oscillator beep via a `@JsFun` bridge — degrades silently pre-user-gesture or without `AudioContext` | `navigator.vibrate` where supported; silent no-op on desktop browsers |

`feedback` has no dependency on another `kmp-toolkit` module — `commonMain` only reaches for
`kotlinx-coroutines-core`. It's consumed by Kursi, the family's KMP card game.

## location

![location](docs/assets/location-banner.svg)

Pure, allocation-light GPS-track math — the reusable core of a real offline-first tracking engine,
with **zero** platform or coroutine dependencies (only `kotlin.math`), so it runs identically on
Android, iOS, desktop and the browser. The location *service* (foreground service, sensors, GMS
activity recognition) and the app's `LocationData` model stay in the app; this is just the math.

- **`KalmanSmoother`** — a 2-D constant-velocity Kalman filter over lat/lng with per-fix measurement
  noise derived from GPS accuracy; smooths a jittery fix stream toward the true path. `smooth(lat,
  lng, accuracyMeters, timestampMs)` → smoothed `(lat, lng)`; `reset()` between journeys.
- **`PathSimplifier`** — Douglas–Peucker simplification (`GeoPoint` list) for *rendering only*, with
  named `Epsilon` presets. Never use the simplified path for distance — it drops 5–25% of length.
- **`DynamicIntervalCalculator`** — picks the next GPS polling interval from speed/activity/harsh-accel
  inputs (`IntervalInputs`), trading battery against fix density.
- **`TrackingQualityScorer`** — a *live* fix-quality score (`QualityInputs`) for a tracking
  notification / quality chip, scored as conditions happen rather than post-hoc.

```kotlin
import com.siddharth.kmp.location.KalmanSmoother

val kalman = KalmanSmoother(processNoiseMetersPerSec = 1.0)
locationUpdates.collect { fix ->
    val (lat, lng) = kalman.smooth(fix.lat, fix.lng, fix.accuracyMeters, fix.timeMs)
    // persist / render the smoothed point
}
```

| Member | Kind | What it does |
|---|---|---|
| `KalmanSmoother` | `class` | 2-D constant-velocity Kalman smoothing of a GPS fix stream |
| `PathSimplifier` | `object` | Douglas–Peucker path simplification (render-only) over `GeoPoint` |
| `DynamicIntervalCalculator` | `object` | Battery-vs-density GPS polling interval from `IntervalInputs` |
| `TrackingQualityScorer` | `object` | Live fix-quality score from `QualityInputs` |

`location` depends on no other `kmp-toolkit` module and no third-party library. Consumed by Mileway's
`feature:tracking`.

## secrets

![secrets](docs/assets/secrets-banner.svg)

`secrets` isn't a Gradle module — it's a standalone SOPS+age encrypted-secrets vault and alias
manifest, demonstrating the "vault mode" secret-resolution model:

```
alias -> vault entry -> sops+age decrypt -> materialize.at path
```

It's a self-contained demo/reference vault, not wired into any production app; its relationship to
the rest of `kmp-toolkit` is by pattern, not by `implementation(...)`.

**Layout:**

```
.sops.yaml                          # which files get encrypted, to which age recipient(s)
alias-manifest.yaml                 # logical name -> vault key -> where it lands on disk
vault/
  example.secrets.yaml              # plaintext fixture (fictional values) — the pre-encryption source
  example.secrets.enc.yaml.sample   # illustrative-only: what the encrypted file's shape looks like
test.sh                             # round-trip check (live if sops+age installed, else documents steps)
```

**The flow:** generate an age identity once (`age-keygen -o ~/.config/sops/age/keys.txt`), add the
public `age1...` key as a recipient in `.sops.yaml`, encrypt with `sops -e vault/example.secrets.yaml
> vault/example.secrets.enc.yaml` (commit the encrypted file, never the plaintext once it holds
anything real), and decrypt with `sops -d vault/example.secrets.enc.yaml`. A resolver looks up an
alias in `alias-manifest.yaml`, decrypts the named vault file, reads the `vault_key` path out of the
decrypted YAML, and writes it to the `materialize.at` target on disk.

`./test.sh` round-trips the whole flow: if `sops`/`age-keygen` are installed it generates a
throwaway identity, encrypts, decrypts, and asserts byte-for-byte equality (`brew install sops age`
to run it live); if either tool is missing it prints the manual steps and exits 0 — a missing local
tool isn't a failure of this repo.

**No real secrets or private keys live in this repo** — every value under `vault/` is fictional, and
the age key referenced in `.sops.yaml` is a placeholder string. Never commit a real private age
identity (`*.age`, `key.txt`, `*.agekey`, `keys.txt`, anything under `keys/`), a real
decrypted/materialized secret, or real values in `vault/example.secrets.yaml`.

## Repository structure

```
kmp-toolkit/
├── result/                # Result<D,E> + DataError — Android · JVM · iOS · Wasm
├── common/                 # AppLog + DispatcherProvider — Android · JVM · iOS · Wasm
├── mvi-core/                # BaseViewModel / StateViewModel / EffectEmitter — Android · JVM · iOS · Wasm
├── network/                # createHttpClient + connectivity — Android · JVM · iOS
├── security/                # Keystore, VAPT posture, FLAG_SECURE — Android only
├── designsystem/            # DesignTokens, ThemeController, MarkdownText — Android · iOS · Wasm
├── ai/                      # OnDeviceLlm seam — Android · JVM · iOS
├── feedback/                # SoundPlayer, HapticManager — Android · JVM · iOS · Wasm
├── secrets/                  # SOPS+age vault + alias manifest (docs only, not a Gradle module)
├── docs/
│   └── assets/               # animated per-module banner SVGs
├── gradle/                   # version catalog (libs.versions.toml)
├── gradle.properties
├── gradlew / gradlew.bat
└── README.md                 # this file
```

Gradle convention plugins (`androidKmpLibrary`, `composeCompiler`, etc.) applied across the modules
above live in a separate repo, [kmp-build-logic](https://github.com/darkpandawarrior/kmp-build-logic).

## License

MIT — see [LICENSE](LICENSE). Part of the [kmp-toolkit](https://cv-siddharth.vercel.app/) family;
convention plugins in [kmp-build-logic](https://github.com/darkpandawarrior/kmp-build-logic).
[Portfolio](https://cv-siddharth.vercel.app/).
