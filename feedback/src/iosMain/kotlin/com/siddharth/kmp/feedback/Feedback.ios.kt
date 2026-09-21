@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.siddharth.kmp.feedback

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// ═══════════════════════════════════════════════════════════════════════════════
// Feedback.ios.kt — iOS actual.
//
// Sound:  synthesise a 16-bit PCM WAV in memory, wrap it as NSData, and play through
//         AVAudioPlayer. No bundled assets needed.
// Haptic: UIImpactFeedbackGenerator (light/medium/heavy) + UINotificationFeedbackGenerator
//         (for the "caught lying" double buzz → .warning).
// ═══════════════════════════════════════════════════════════════════════════════

private const val SAMPLE_RATE = 44_100

// ── RIFF/WAVE wire format (mono, 16-bit PCM little-endian). Every value below is fixed by the
// WAV specification, not a tuning choice, so each one is named after the field it fills.
private const val BYTE_MASK = 0xFF
private const val SHORT_MASK = 0xFFFF
private const val BITS_PER_BYTE = 8
private const val BYTES_PER_SAMPLE = 2
private const val BITS_PER_SAMPLE = 16

/** Size of a `u32` field in the header. */
private const val U32_FIELD_BYTES = 4

/** Size of a `u16` field in the header. */
private const val U16_FIELD_BYTES = 2

/** Bytes of RIFF header that follow the size field — what `ChunkSize = 36 + dataSize` counts. */
private const val RIFF_HEADER_TRAILING_BYTES = 36

/** Length of the PCM `fmt ` chunk body, in bytes. */
private const val FMT_CHUNK_BYTES = 16

/** Audio format code 1 = uncompressed PCM. */
private const val WAV_FORMAT_PCM = 1

/** Mono. */
private const val CHANNEL_COUNT = 1

/** Cap on retained AVAudioPlayers, so rapid-fire cues cannot grow the list without bound. */
private const val MAX_RETAINED_PLAYERS = 8

private fun appendLE(
    out: MutableList<Byte>,
    value: Int,
    bytes: Int,
) {
    var v = value
    repeat(bytes) {
        out.add((v and BYTE_MASK).toByte())
        v = v shr BITS_PER_BYTE
    }
}

/** Build a complete mono 16-bit PCM WAV file (header + data) from synthesised samples. */
private fun renderWav(
    build: (i: Int, sr: Double) -> Double,
    durMs: Int,
): ByteArray {
    val n = (SAMPLE_RATE.toLong() * durMs / 1000L).toInt().coerceAtLeast(1)
    val pcm = ArrayList<Byte>(n * 2 + 44)

    val byteRate = SAMPLE_RATE * 2
    val dataSize = n * 2

    // RIFF header
    "RIFF".forEach { pcm.add(it.code.toByte()) }
    appendLE(pcm, RIFF_HEADER_TRAILING_BYTES + dataSize, U32_FIELD_BYTES)
    "WAVE".forEach { pcm.add(it.code.toByte()) }
    // fmt chunk
    "fmt ".forEach { pcm.add(it.code.toByte()) }
    appendLE(pcm, FMT_CHUNK_BYTES, U32_FIELD_BYTES)
    appendLE(pcm, WAV_FORMAT_PCM, U16_FIELD_BYTES)
    appendLE(pcm, CHANNEL_COUNT, U16_FIELD_BYTES)
    appendLE(pcm, SAMPLE_RATE, U32_FIELD_BYTES)
    appendLE(pcm, byteRate, U32_FIELD_BYTES)
    appendLE(pcm, BYTES_PER_SAMPLE, U16_FIELD_BYTES) // block align: 1 channel x 2 bytes
    appendLE(pcm, BITS_PER_SAMPLE, U16_FIELD_BYTES)
    // data chunk
    "data".forEach { pcm.add(it.code.toByte()) }
    appendLE(pcm, dataSize, U32_FIELD_BYTES)

    for (i in 0 until n) {
        val sample = build(i, SAMPLE_RATE.toDouble())
        val s = (sample * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767)
        appendLE(pcm, s and SHORT_MASK, BYTES_PER_SAMPLE)
    }
    return pcm.toByteArray()
}

private fun tone(
    freqHz: Double,
    decay: Double,
    amplitude: Double,
): (Int, Double) -> Double =
    { i, sr ->
        val t = i / sr
        sin(2.0 * PI * freqHz * t) * exp(-decay * t) * amplitude
    }

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

private class IosSoundPlayer : SoundPlayer {
    private val wavs: Map<SoundKey, ByteArray> =
        mapOf(
            SoundKey.Confirm to renderWav(tone(196.0, 26.0, 0.5), 120),
            SoundKey.Reward to renderWav(tone(1320.0, 28.0, 0.35), 90),
            SoundKey.Thud to renderWav(tone(110.0, 14.0, 0.55), 160),
            SoundKey.Success to renderWav(tone(659.25, 8.0, 0.45), 320),
        )

    // Retain players so they are not GC'd mid-playback.
    private val active = mutableListOf<AVAudioPlayer>()

    private var released = false

    init {
        runCatching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryAmbient, null)
            session.setActive(true, null)
        }
    }

    override fun playSound(key: SoundKey) {
        if (released) return
        val data = wavs[key] ?: return
        runCatching {
            val player = AVAudioPlayer(data = data.toNSData(), error = null)
            player.prepareToPlay()
            player.play()
            active.add(player)
            if (active.size > MAX_RETAINED_PLAYERS) active.removeAt(0)
        }
    }

    override fun haptic(pattern: HapticPattern) {
        if (released || pattern == HapticPattern.None) return
        runCatching {
            when (pattern) {
                HapticPattern.Tick ->
                    UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).apply {
                        prepare()
                        impactOccurred()
                    }
                HapticPattern.Thud ->
                    UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).apply {
                        prepare()
                        impactOccurred()
                    }
                HapticPattern.HeavyLong ->
                    UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy).apply {
                        prepare()
                        impactOccurred()
                    }
                HapticPattern.DoubleBuzz ->
                    UINotificationFeedbackGenerator().apply {
                        prepare()
                        notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
                    }
                HapticPattern.None -> Unit
            }
        }
    }

    override fun release() {
        released = true
        active.clear()
    }
}

actual fun defaultSoundPlayer(): SoundPlayer = IosSoundPlayer()
