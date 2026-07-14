package com.siddharth.kmp.security.di

import com.siddharth.kmp.security.AndroidDeviceIntegrity
import com.siddharth.kmp.security.AndroidSecurityAuditor
import com.siddharth.kmp.security.AntiDebugDetector
import com.siddharth.kmp.security.AntiHookDetector
import com.siddharth.kmp.security.AntiSslBypassDetector
import com.siddharth.kmp.security.AppSecurityManager
import com.siddharth.kmp.security.DeviceIntegrity
import com.siddharth.kmp.security.KeystoreSecureStore
import com.siddharth.kmp.security.SecureStore
import com.siddharth.kmp.security.SecurityAuditor
import com.siddharth.kmp.security.SecurityConfig
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Koin wiring for `core:security`.
 *
 * - [SecureStore] is bound to the Keystore-backed implementation — the only production-safe one.
 * - [SecurityConfig] is passed in ([config]) — it is app-level policy, so the app owns it and can set
 *   the VAPT `bypass*` flags from `BuildConfig` for a compliance-test build. Defaults to full protection.
 * - [DeviceIntegrity] resolves the config via `get()`, so the two stay consistent.
 * - [AppSecurityManager] owns the screen-facing defenses (FLAG_SECURE, tapjacking, background
 *   overlay). The app calls `install(application)` on it once from `Application.onCreate` and
 *   `applySecurityToActivity(this)` from each Activity's `onCreate`.
 * - [AntiDebugDetector] / [AntiHookDetector] / [AntiSslBypassDetector] are Kotlin `object`s; they are
 *   registered so [AndroidSecurityAuditor] can be assembled purely from the graph.
 * - [SecurityAuditor] is the single launch-time posture call — `audit()` (suspend) produces a
 *   [com.siddharth.kmp.security.SecurityAudit]; turn it into an action with `SecurityPolicy.evaluate`.
 *
 * Assemble into the app graph: `modules(securityModule(SecurityConfig(...)), …)`.
 */
fun securityModule(config: SecurityConfig = SecurityConfig()): Module =
    module {
        single<SecureStore> { KeystoreSecureStore(androidContext()) }
        single { config }
        single<DeviceIntegrity> { AndroidDeviceIntegrity(androidContext(), get()) }

        single { AppSecurityManager(get()) }

        single { AntiDebugDetector }
        single { AntiHookDetector }
        single { AntiSslBypassDetector }

        single<SecurityAuditor> {
            AndroidSecurityAuditor(androidContext(), get(), get(), get(), get(), get())
        }
    }
