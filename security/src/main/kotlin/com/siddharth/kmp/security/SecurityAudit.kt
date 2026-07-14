package com.siddharth.kmp.security

import android.content.Context
import com.siddharth.kmp.common.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SecurityAudit"

/**
 * The single launch-time security posture call. Composes [DeviceIntegrity] (root + emulator) with
 * the three runtime detectors ([AntiDebugDetector], [AntiHookDetector], [AntiSslBypassDetector]) into
 * one [SecurityAudit], honoring the [SecurityConfig] VAPT bypass flags.
 *
 * [audit] is `suspend`: the detectors do file I/O (reading `/proc/self/status` and `maps`), spawn a
 * `which su` process, and introspect TrustManagers — none belongs on the main thread. Detection (this) is kept
 * separate from *enforcement*: turn a [SecurityAudit] into an action with [SecurityPolicy.evaluate].
 */
interface SecurityAuditor {
    suspend fun audit(): SecurityAudit
}

/**
 * Aggregated result of a full security pass.
 *
 * @property signals concatenated human-readable trail from every layer, for logging / a debug screen.
 * @property isCompromised the gate a caller acts on. `rooted || debuggerAttached || hooked ||
 *   sslBypassSuspected` — emulator alone does NOT compromise (dev/QA run on emulators). Each term is
 *   already suppressed by its [SecurityConfig] bypass flag at construction time, so a VAPT build with
 *   the relevant bypass set will report the raw booleans but still gate as not-compromised.
 */
data class SecurityAudit(
    val rooted: Boolean,
    val emulator: Boolean,
    val debuggerAttached: Boolean,
    val hooked: Boolean,
    val sslBypassSuspected: Boolean,
    val signals: List<String>,
) {
    val isCompromised: Boolean
        get() = rooted || debuggerAttached || hooked || sslBypassSuspected
}

/**
 * Real Android auditor. Runs each layer, applies the bypass flags, and folds everything into one
 * [SecurityAudit]. The bypass flags are applied here (not inside each detector) so detection stays
 * accurate and fully logged even on a VAPT build — only the boolean that feeds [isCompromised] is
 * cleared. Emulator is carried through for reporting but never gates.
 */
class AndroidSecurityAuditor(
    private val context: Context,
    private val config: SecurityConfig,
    private val deviceIntegrity: DeviceIntegrity,
    private val antiDebug: AntiDebugDetector = AntiDebugDetector,
    private val antiHook: AntiHookDetector = AntiHookDetector,
    private val antiSsl: AntiSslBypassDetector = AntiSslBypassDetector,
) : SecurityAuditor {
    override suspend fun audit(): SecurityAudit =
        withContext(Dispatchers.IO) {
            val signals = mutableListOf<String>()

            // Root + emulator (+ debuggable) via the existing DeviceIntegrity heuristics.
            val deviceReport = deviceIntegrity.inspect()
            signals += deviceReport.signals

            val debugSignals = antiDebug.detect(context)
            signals += debugSignals

            val hookSignals = antiHook.detect()
            signals += hookSignals

            val sslSignals = antiSsl.detect()
            signals += sslSignals

            // Raw booleans (report the truth) …
            val rooted = deviceReport.rooted
            val emulator = deviceReport.emulator
            val debuggerAttached = deviceReport.debuggerAttached || debugSignals.isNotEmpty()
            val hooked = hookSignals.isNotEmpty()
            val sslBypassSuspected = sslSignals.isNotEmpty()

            // … then apply the VAPT bypass flags only to the gate-feeding booleans.
            val audit =
                SecurityAudit(
                    rooted = if (config.bypassRoot) false else rooted,
                    emulator = if (config.bypassEmulator) false else emulator,
                    debuggerAttached = if (config.bypassDebugger) false else debuggerAttached,
                    hooked = if (config.bypassHook) false else hooked,
                    sslBypassSuspected = if (config.bypassSsl) false else sslBypassSuspected,
                    signals = signals.toList(),
                )

            if (audit.isCompromised) {
                AppLog.w("Security audit COMPROMISED: ${audit.signals.joinToString()}", tag = TAG)
            } else {
                AppLog.i(
                    "Security audit OK (emulator=$emulator, bypassRoot=${config.bypassRoot}, " +
                        "bypassHook=${config.bypassHook}, bypassSsl=${config.bypassSsl}, " +
                        "bypassDebugger=${config.bypassDebugger})",
                    tag = TAG,
                )
            }
            audit
        }
}
