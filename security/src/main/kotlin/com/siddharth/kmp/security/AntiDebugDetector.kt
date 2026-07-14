package com.siddharth.kmp.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Debug
import com.siddharth.kmp.common.AppLog
import java.io.File

private const val TAG = "AntiDebugDetector"

/**
 * Runtime anti-debugging detector using standard OWASP MASVS anti-tampering techniques.
 *
 * Combines several independent signals so that hooking a single one (attackers commonly replace
 * `Debug.isDebuggerConnected()` with a stub that returns `false`) is not enough to hide a debugger:
 *
 *  - [Debug.isDebuggerConnected] — a JDWP debugger is attached right now.
 *  - [Debug.waitingForDebugger] — the process is paused waiting for a debugger to attach.
 *  - `TracerPid` from `/proc/self/status` — non-zero means *some* process is `ptrace`-ing us
 *    (gdb, lldb, strace, or a native debugger). This is harder to hook than the `Debug.*` calls
 *    because it reads the kernel-maintained proc file directly.
 *  - `FLAG_DEBUGGABLE` on the app — the APK was built debuggable. Not an attack by itself, but on a
 *    release build it means the manifest/BuildConfig was tampered with.
 *
 * ## Functional vs best-effort
 * The `Debug.*` checks and the `TracerPid` parse are **solid and reliable** — TracerPid in
 * particular is a genuine kernel signal. The `FLAG_DEBUGGABLE` check is solid but informational.
 * There is no timing trick here (a timing heuristic tends to be noisy and false-positive-prone on cold
 * starts); a determined attacker on a rooted device can still defeat any in-process check by hooking
 * the proc read itself, so treat this as *raising the bar*, not a guarantee.
 *
 * Every signal is honored by [SecurityConfig.bypassDebugger] at the [SecurityAuditor] aggregation
 * layer, so a debuggable VAPT build can still pass the compromise gate.
 */
object AntiDebugDetector {
    /**
     * Runs every debug check and returns the human-readable signal strings that fired.
     *
     * @param context used only for the [ApplicationInfo.FLAG_DEBUGGABLE] check.
     * @return list of signals; empty when no debug indicator was found.
     */
    fun detect(context: Context): List<String> {
        val signals = mutableListOf<String>()

        if (Debug.isDebuggerConnected()) {
            signals += "debug: Debug.isDebuggerConnected() == true"
        }
        if (Debug.waitingForDebugger()) {
            signals += "debug: process is waiting for a debugger to attach"
        }

        val tracerPid = readTracerPid()
        if (tracerPid > 0) {
            signals += "debug: TracerPid=$tracerPid in /proc/self/status (process is being traced)"
        }

        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable) {
            signals += "debug: app is FLAG_DEBUGGABLE"
        }

        if (signals.isNotEmpty()) {
            AppLog.w("Debug indicators: ${signals.joinToString()}", tag = TAG)
        }
        return signals
    }

    /** Convenience boolean over [detect]. */
    fun isDebuggerAttached(context: Context): Boolean = detect(context).isNotEmpty()

    /**
     * Reads `/proc/self/status` and returns the TracerPid value, or `0` when absent/unreadable.
     * Kept as its own function so the parsing is testable via [parseTracerPid].
     */
    private fun readTracerPid(): Int =
        try {
            val status = File("/proc/self/status")
            if (status.exists() && status.canRead()) {
                parseTracerPid(status.readText())
            } else {
                0
            }
        } catch (e: Exception) {
            // Unreadable proc — treat as "not traced" rather than a false positive.
            0
        }
}

/**
 * Pure parser for the `TracerPid:` line of a `/proc/<pid>/status` file. Returns the traced-by PID,
 * or `0` when the line is missing or malformed. A non-zero result means a debugger/tracer is
 * attached. Extracted as a top-level function so it is JVM-unit-testable with sample proc content.
 */
internal fun parseTracerPid(statusFileContent: String): Int =
    statusFileContent
        .lineSequence()
        .firstOrNull { it.startsWith("TracerPid:") }
        ?.substringAfter(":")
        ?.trim()
        ?.toIntOrNull()
        ?: 0
