package com.siddharth.kmp.security

/*
 * Enforcement, kept deliberately separate from detection. [SecurityAuditor] answers *"what did we
 * find?"* ([SecurityAudit]); [SecurityPolicy] answers *"what should we do about it?"* ([SecurityDecision]),
 * driven by a configurable [SecurityPosture]. Hard-coding the reaction inside the detectors is the
 * common naive approach; this split lets the app choose its stance per build (strict in release,
 * lenient in debug) without ever
 * touching detection code, and makes the decision a pure, exhaustively testable function.
 */

/** One thing the audit can flag. */
enum class Threat { ROOT, EMULATOR, DEBUGGER, HOOK, SSL_BYPASS }

/** What to do about a triggered [Threat]. Ordered by severity — [BLOCK] wins over [WARN] over [ALLOW]. */
enum class ThreatAction { ALLOW, WARN, BLOCK }

/** The app's stance: an action per threat. */
data class SecurityPosture(
    val actions: Map<Threat, ThreatAction>,
) {
    fun actionFor(threat: Threat): ThreatAction = actions[threat] ?: ThreatAction.ALLOW

    companion object {
        /** Release stance: refuse to run on root/hook/SSL-bypass/debugger; note (don't block) emulators. */
        fun strict(): SecurityPosture =
            SecurityPosture(
                mapOf(
                    Threat.ROOT to ThreatAction.BLOCK,
                    Threat.HOOK to ThreatAction.BLOCK,
                    Threat.SSL_BYPASS to ThreatAction.BLOCK,
                    Threat.DEBUGGER to ThreatAction.BLOCK,
                    Threat.EMULATOR to ThreatAction.WARN,
                ),
            )

        /** Dev/debug stance: nothing blocks (you're on an emulator, attached to a debugger); just warn. */
        fun lenient(): SecurityPosture =
            SecurityPosture(
                mapOf(
                    Threat.ROOT to ThreatAction.WARN,
                    Threat.HOOK to ThreatAction.WARN,
                    Threat.SSL_BYPASS to ThreatAction.WARN,
                    Threat.DEBUGGER to ThreatAction.ALLOW,
                    Threat.EMULATOR to ThreatAction.ALLOW,
                ),
            )
    }
}

/** The outcome of applying a posture to an audit. */
data class SecurityDecision(
    val action: ThreatAction,
    val triggered: List<Pair<Threat, ThreatAction>>,
) {
    val shouldBlock: Boolean get() = action == ThreatAction.BLOCK
    val shouldWarn: Boolean get() = action == ThreatAction.WARN

    /** e.g. "ROOT→BLOCK, EMULATOR→WARN" — for a log line or a debug screen. */
    fun summary(): String = triggered.joinToString { (t, a) -> "$t→$a" }
}

/**
 * Pure decision function: fold a [SecurityAudit] against a [SecurityPosture]. The overall action is
 * the most severe among the triggered threats (none triggered → ALLOW).
 */
object SecurityPolicy {
    fun evaluate(
        audit: SecurityAudit,
        posture: SecurityPosture,
    ): SecurityDecision {
        val triggered =
            buildList {
                if (audit.rooted) add(Threat.ROOT to posture.actionFor(Threat.ROOT))
                if (audit.emulator) add(Threat.EMULATOR to posture.actionFor(Threat.EMULATOR))
                if (audit.debuggerAttached) add(Threat.DEBUGGER to posture.actionFor(Threat.DEBUGGER))
                if (audit.hooked) add(Threat.HOOK to posture.actionFor(Threat.HOOK))
                if (audit.sslBypassSuspected) add(Threat.SSL_BYPASS to posture.actionFor(Threat.SSL_BYPASS))
            }
        val action = triggered.maxOfOrNull { it.second } ?: ThreatAction.ALLOW
        return SecurityDecision(action, triggered)
    }
}
