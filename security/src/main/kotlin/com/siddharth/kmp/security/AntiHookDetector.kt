package com.siddharth.kmp.security

import android.os.Process
import com.siddharth.kmp.common.AppLog
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

private const val TAG = "AntiHookDetector"

/**
 * Detects in-process instrumentation / hooking frameworks — **Frida** and **Xposed / LSPosed** —
 * using standard runtime-detection heuristics.
 *
 * Techniques:
 *  - **Frida threads** — Frida's agent spins up characteristically named threads
 *    (`gum-js-loop`, `pool-frida`, `gmain`, …). Enumerating [Thread.getAllStackTraces] and matching
 *    names is cheap and catches an attached frida-agent.
 *  - **Memory maps** — a loaded `frida-agent` / `frida-gadget` / `libgadget` / Xposed / substrate
 *    library shows up in `/proc/self/maps`. See [mapsIndicatesFrida].
 *  - **Frida default ports** — a stock frida-server listens on `27042` (and control port `27043`)
 *    on loopback. A successful TCP connect is a strong signal. Best-effort: the port is trivially
 *    changed with `frida-server -l`.
 *  - **Xposed / LSPosed** — reflectively probing for `de.robv.android.xposed.XposedBridge` and
 *    `org.lsposed.lspd.core.Main`, plus scanning a throwaway stack trace for Xposed frames.
 *
 * ## Functional vs best-effort
 * **All of this is best-effort.** Every check runs inside the very process an attacker controls, so
 * a determined attacker can hook the thread enumeration, the file read, or the socket call to hide.
 * The point is to raise the cost of a trivial `frida -U -f <pkg>` attach and to demonstrate VAPT
 * awareness — not to provide a guarantee. Honored by [SecurityConfig.bypassHook] at aggregation.
 */
object AntiHookDetector {
    /** Frida agent thread-name fragments (lower-cased match). */
    private val SUSPICIOUS_THREAD_NAMES =
        listOf(
            "gum-js-loop",
            "gmain",
            "gdbus",
            "pool-frida",
            "frida-server",
            "frida-agent",
            "linjector",
        )

    /** Xposed / LSPosed marker classes probed via reflection. */
    private val XPOSED_MARKER_CLASSES =
        listOf(
            "de.robv.android.xposed.XposedBridge",
            "de.robv.android.xposed.XC_MethodHook",
            "org.lsposed.lspd.core.Main",
        )

    /** Frida's default server / control ports on loopback. */
    private val FRIDA_PORTS = listOf(27042, 27043)

    /**
     * Runs every hook check and returns the signal strings that fired.
     * @return list of signals; empty when nothing was detected.
     */
    fun detect(): List<String> {
        val signals = mutableListOf<String>()

        detectFridaThread()?.let { signals += it }
        detectFridaInMaps()?.let { signals += it }
        detectFridaPort()?.let { signals += it }
        detectXposed()?.let { signals += it }

        if (signals.isNotEmpty()) {
            AppLog.w("Hook indicators: ${signals.joinToString()}", tag = TAG)
        }
        return signals
    }

    /** Convenience boolean over [detect]. */
    fun isHooked(): Boolean = detect().isNotEmpty()

    private fun detectFridaThread(): String? =
        try {
            Thread
                .getAllStackTraces()
                .keys
                .map { it.name.lowercase() }
                .firstOrNull { name -> SUSPICIOUS_THREAD_NAMES.any { name.contains(it) } }
                ?.let { "hook: suspicious thread name '$it' (Frida agent)" }
        } catch (e: Exception) {
            null
        }

    private fun detectFridaInMaps(): String? =
        try {
            val maps = File("/proc/${Process.myPid()}/maps")
            if (maps.exists() && maps.canRead() && mapsIndicatesFrida(maps.readText())) {
                "hook: frida/xposed/substrate library present in /proc/self/maps"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

    private fun detectFridaPort(): String? {
        for (port in FRIDA_PORTS) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 120)
                    return "hook: Frida default port $port is open on loopback"
                }
            } catch (e: Exception) {
                // Connection refused is the good case — port not listening.
            }
        }
        return null
    }

    private fun detectXposed(): String? {
        // Reflective class probe.
        for (className in XPOSED_MARKER_CLASSES) {
            try {
                Class.forName(className)
                return "hook: Xposed/LSPosed marker class present ($className)"
            } catch (e: ClassNotFoundException) {
                // Good — not present.
            } catch (e: Throwable) {
                // Ignore other loader failures.
            }
        }
        // Stack-trace marker probe.
        return try {
            throw Exception("xposed-probe")
        } catch (e: Exception) {
            e.stackTrace
                .firstOrNull {
                    it.className.contains(
                        "de.robv.android.xposed",
                    ) ||
                        it.className.contains("XposedBridge")
                }?.let { "hook: Xposed frame in stack trace (${it.className})" }
        }
    }
}

/**
 * Pure scan of `/proc/<pid>/maps` content for known hooking-library substrings. Returns `true` when
 * any Frida / Xposed / substrate marker appears. Case-insensitive. Extracted so it can be
 * unit-tested against sample maps content on the JVM without a device.
 */
internal fun mapsIndicatesFrida(mapsContent: String): Boolean {
    val markers =
        listOf(
            "frida",
            "frida-agent",
            "frida-gadget",
            "libgadget",
            "gum-js",
            "linjector",
            "xposed",
            "substrate",
        )
    val lower = mapsContent.lowercase()
    return markers.any { lower.contains(it) }
}
