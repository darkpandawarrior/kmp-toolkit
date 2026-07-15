package com.siddharth.kmp.feedback

// ═══════════════════════════════════════════════════════════════════════════════
// Feedback.kt — the platform-agnostic feedback contract (M3).
//
// A tiny expect/actual layer so any app moment can fire a short SFX + a haptic pulse,
// gated by the caller's own master sound toggle.
//
//  - SoundKey:      WHAT to play (a small vocabulary of feel-based cues).
//  - HapticPattern: HOW the device should buzz.
//  - SoundPlayer:   expect interface — actuals: jvm (javax.sound, real tone synth),
//                   android (SoundPool + Vibrator), ios (AVAudioPlayer + Impact gen),
//                   wasm (Web Audio / safe no-op).
//  - defaultSoundPlayer(): platform factory.
//
// The caller owns the mapping from its own domain moments → SoundKey / HapticPattern;
// this module stays free of any Compose / domain code.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The short SFX vocabulary a caller draws on for UX feedback.
 *
 * Each key is synthesised (jvm/ios tone synth) or sampled (android SoundPool) into a
 * sub-second sound. Keys are intentionally coarse — one per *feel*, not per event —
 * so an app ships a handful of audibly distinct cues and maps its own moments onto them.
 */
enum class SoundKey {
    /** Firm press/slam — a committing action (confirm, claim, submit). */
    Confirm,

    /** Bright clink — a positive/rewarding action (income, credit, reward). */
    Reward,

    /** Heavy thud — a weighty negative action (loss, removal, penalty). */
    Thud,

    /** Triumphant sting — a success fanfare (win, completion, milestone). */
    Success,
}

/**
 * Device haptic pattern. A coarse, feel-based taxonomy the caller maps its own moments onto.
 */
enum class HapticPattern {
    /** No buzz. */
    None,

    /** A single light tick — routine actions, handoffs. */
    Tick,

    /** A single firm thud — weighty/negative actions. */
    Thud,

    /** Two quick buzzes — an alert/reveal. */
    DoubleBuzz,

    /** One long heavy pulse — reserved for the biggest moments. */
    HeavyLong,
}

/**
 * Plays short SFX by [SoundKey] and fires haptics by [HapticPattern].
 *
 * Contract:
 *  - All methods are fire-and-forget and must never throw (a missing audio device,
 *    a denied haptic permission, or a headless CI box must degrade to a silent no-op).
 *  - [playSound] / [haptic] are expected to be called by the caller only when its master
 *    sound toggle is ON; implementations need not re-check a preference.
 *  - [release] frees native resources (SoundPool, audio lines). Idempotent.
 *
 * The jvm actual genuinely emits audio (a tiny javax.sound tone synth) so the desktop
 * build is verifiably audible; the wasm actual uses Web Audio where available and
 * otherwise degrades to a no-op.
 */
interface SoundPlayer {
    /** Play the SFX mapped to [key]. No-op if audio is unavailable. */
    fun playSound(key: SoundKey)

    /** Fire a device haptic. No-op on platforms with no vibrator (desktop/web). */
    fun haptic(pattern: HapticPattern)

    /** Release native resources. Safe to call more than once. */
    fun release()
}

/**
 * Platform factory for the default [SoundPlayer].
 *
 * Implemented per target:
 *  - jvm:     real javax.sound.sampled tone synth (audible on desktop).
 *  - android: SoundPool + Vibrator (needs a Context — see the Android actual).
 *  - ios:     AVAudioPlayer + UIImpactFeedbackGenerator.
 *  - wasm:    Web Audio oscillator, or a no-op if AudioContext is unavailable.
 */
expect fun defaultSoundPlayer(): SoundPlayer
