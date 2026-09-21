@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.siddharth.kmp.feedback

// ═══════════════════════════════════════════════════════════════════════════════
// Feedback.wasmJs.kt — wasmJs actual.
//
// Sound:  a tiny Web Audio oscillator-beep via a @JsFun bridge. Browsers gate audio
//         behind a user gesture; the first beep after a click/tap unlocks the rest.
//         If AudioContext is unavailable the JS helper swallows the error → no-op.
// Haptic: navigator.vibrate where supported (mobile web); otherwise a no-op. Desktop
//         browsers have no vibration motor, so this degrades silently.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Plays a short decaying sine beep. All work is inside JS with a try/catch so a missing
 * AudioContext (or autoplay block before the first gesture) never propagates an exception.
 *
 * UnusedParameter is a FALSE POSITIVE on a `js(...)` function: the Kotlin/Wasm compiler splices
 * these parameters into the JS body by name (`osc.frequency.value = freqHz`), and detekt reads the
 * body as an opaque string literal. Renaming or removing any of them breaks the generated JS.
 */
@Suppress("UnusedParameter")
private fun jsBeep(
    freqHz: Double,
    durationMs: Double,
    amplitude: Double,
): Unit =
    js(
        """{
            try {
                var Ctx = window.AudioContext || window.webkitAudioContext;
                if (!Ctx) return;
                if (!window.__kursiAudioCtx) { window.__kursiAudioCtx = new Ctx(); }
                var ctx = window.__kursiAudioCtx;
                if (ctx.state === 'suspended') { ctx.resume(); }
                var osc = ctx.createOscillator();
                var gain = ctx.createGain();
                osc.frequency.value = freqHz;
                osc.type = 'sine';
                var now = ctx.currentTime;
                gain.gain.setValueAtTime(amplitude, now);
                gain.gain.exponentialRampToValueAtTime(0.0001, now + durationMs / 1000.0);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(now);
                osc.stop(now + durationMs / 1000.0);
            } catch (e) { /* no-op: audio unavailable */ }
        }""",
    )

/**
 * Fires navigator.vibrate(ms) where supported. Swallows any error.
 *
 * Same `js(...)` splice as [jsBeep] — `ms` is read inside the JS body, not by Kotlin.
 */
@Suppress("UnusedParameter")
private fun jsVibrate(ms: Int): Unit =
    js(
        """{
            try {
                if (typeof navigator !== 'undefined' && navigator.vibrate) {
                    navigator.vibrate(ms);
                }
            } catch (e) { /* no-op */ }
        }""",
    )

/**
 * One Web Audio cue: pitch, how long the gain ramps down over, and peak gain.
 *
 * The numbers used to sit bare in the `when` below, six calls of three unlabelled literals. As a
 * named table they say which note each cue is, and the peak-gain column becomes comparable across
 * cues at a glance — which is the thing anyone tuning these actually wants to see.
 */
private data class Beep(
    val freqHz: Double,
    val durationMs: Double,
    val amplitude: Double,
)

// Pitches are equal-tempered note frequencies (A4 = 440 Hz). Peak gain stays below 0.5: a browser
// tab's output is not mixed against anything, so anything louder just clips on small speakers.
private val ConfirmBeep = Beep(freqHz = 196.0, durationMs = 120.0, amplitude = 0.4) // G3, firm press
private val RewardBeep = Beep(freqHz = 1320.0, durationMs = 90.0, amplitude = 0.3) // E6, bright clink
private val ThudBeep = Beep(freqHz = 110.0, durationMs = 160.0, amplitude = 0.45) // A2, heavy thud

/** Rising C5-E5-G5 sting (a major triad) for the success fanfare. */
private val SuccessSting =
    listOf(
        Beep(freqHz = 523.25, durationMs = 110.0, amplitude = 0.35), // C5
        Beep(freqHz = 659.25, durationMs = 110.0, amplitude = 0.35), // E5
        Beep(freqHz = 783.99, durationMs = 220.0, amplitude = 0.4), // G5, held
    )

private fun play(beep: Beep) = jsBeep(beep.freqHz, beep.durationMs, beep.amplitude)

private class WasmSoundPlayer : SoundPlayer {
    override fun playSound(key: SoundKey) {
        when (key) {
            SoundKey.Confirm -> play(ConfirmBeep)
            SoundKey.Reward -> play(RewardBeep)
            SoundKey.Thud -> play(ThudBeep)
            // Best-effort; the browser clock staggers these rather than the caller.
            SoundKey.Success -> SuccessSting.forEach(::play)
        }
    }

    override fun haptic(pattern: HapticPattern) {
        val ms =
            when (pattern) {
                HapticPattern.None -> return
                HapticPattern.Tick -> 18
                HapticPattern.Thud -> 45
                HapticPattern.DoubleBuzz -> 90
                HapticPattern.HeavyLong -> 120
            }
        jsVibrate(ms)
    }

    override fun release() {
        // Nothing to free — the shared AudioContext lives on window for reuse.
    }
}

actual fun defaultSoundPlayer(): SoundPlayer = WasmSoundPlayer()
