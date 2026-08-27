package com.siddharth.kmp.mvi

import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.Test
import kotlin.test.assertEquals

/** A collaborator that exists only to be mocked. Nothing in production depends on it. */
private interface CompatCollaborator {
    fun load(key: String): Int
}

/**
 * Canary for the Mokkery compiler plugin, NOT a test of anything in this module.
 *
 * Mokkery is a Kotlin compiler plugin, which is the single riskiest category of dependency to pin
 * against a `-RC` compiler: it generates code against compiler internals, so a Kotlin bump can
 * break it in a way no version-catalogue check would notice. It was adopted anyway because it
 * closes a real gap — MockK is JVM-only, and the collaborators worth faking in this toolkit
 * (`mvi-core` reducers, `auth`, `payments-api`, `offline-outbox`) live in commonMain.
 *
 * This test's job is to fail loudly and early on the next toolchain move. It deliberately runs on
 * every target rather than just the JVM, because the JVM is the one platform where Mokkery is
 * *least* likely to break and where MockK would have been an option anyway. Proven green on
 * jvm, iosSimulatorArm64 and wasmJs against Kotlin 2.4.20-RC, 2026-08-27.
 *
 * If this fails after a Kotlin upgrade, the answer is hand-written fakes, not a Mokkery snapshot.
 */
class MokkeryCompatTest {
    @Test
    fun mockAndVerifyWorkOnThisTarget() {
        val collaborator =
            mock<CompatCollaborator> {
                every { load("a") } returns 41
            }

        val result = collaborator.load("a") + 1

        assertEquals(42, result)
        verify { collaborator.load("a") }
    }
}
