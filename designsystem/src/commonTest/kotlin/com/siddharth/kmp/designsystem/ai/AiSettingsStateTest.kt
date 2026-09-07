@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // TestScope.backgroundScope / runCurrent

package com.siddharth.kmp.designsystem.ai

import com.siddharth.kmp.ai.ModelDownloadState
import com.siddharth.kmp.ai.ModelInfo
import com.siddharth.kmp.ai.ModelManager
import com.siddharth.kmp.ai.ModelManifestEntry
import com.siddharth.kmp.ai.OnDeviceLlm
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.llmchat.ProviderId
import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val AVAILABLE_CAPS = AiCapabilities(streaming = true, multimodal = false, honoredConfigFields = emptySet(), unavailableReason = null)

private fun entry(
    id: String,
    requiresLicenseAck: Boolean = false,
) = ModelManifestEntry(id, id, approxSizeMb = 500, fileName = "$id.task", hfRepo = "repo/$id", hfFile = "$id.task", requiresLicenseAck = requiresLicenseAck)

private fun modelInfo(
    id: String,
    state: ModelDownloadState = ModelDownloadState.ABSENT,
    progress: Float = 0f,
) = ModelInfo(id, id, approxSizeMb = 500, state = state, progress = progress)

private class FakeModelManager(initial: List<ModelInfo>) : ModelManager {
    private val flows = initial.associateTo(linkedMapOf()) { it.id to MutableStateFlow(it) }
    val downloadCalls = mutableListOf<Pair<String, Boolean>>()
    val deleteCalls = mutableListOf<String>()

    override fun models(): List<ModelInfo> = flows.values.map { it.value }

    override fun observe(modelId: String): Flow<ModelInfo> = flows.getValue(modelId).asStateFlow()

    override suspend fun download(
        modelId: String,
        licenseAcknowledged: Boolean,
    ) {
        downloadCalls += modelId to licenseAcknowledged
    }

    override suspend fun delete(modelId: String) {
        deleteCalls += modelId
    }

    fun push(
        modelId: String,
        info: ModelInfo,
    ) {
        flows.getValue(modelId).value = info
    }
}

/** A [ModelManager] whose [download] never completes on its own, so cancelling it is the only way a call ends. */
private class SlowFakeModelManager : ModelManager {
    val downloadCalls = mutableListOf<String>()

    override fun models(): List<ModelInfo> = emptyList()

    override fun observe(modelId: String): Flow<ModelInfo> = MutableStateFlow(modelInfo(modelId)).asStateFlow()

    override suspend fun download(
        modelId: String,
        licenseAcknowledged: Boolean,
    ) {
        downloadCalls += modelId
        awaitCancellation()
    }

    override suspend fun delete(modelId: String) = Unit
}

private class FakeOnDeviceLlm(private val caps: AiCapabilities) : OnDeviceLlm {
    override fun isAvailable(): Boolean = caps.unavailableReason == null

    override suspend fun capabilities(): AiCapabilities = caps

    override suspend fun generate(prompt: String): AiResult<String> = Result.Success("")
}

/** A fresh instance per test, unlike [InMemoryAiConsentStore] (a process-lifetime singleton) — same reason [com.siddharth.kmp.designsystem.ThemeControllerTest] fakes [com.siddharth.kmp.designsystem.ThemeStore] instead of reusing its shared default. */
private class FakeConsentStore(private var value: Boolean? = null) : AiConsentStore {
    override fun consentGiven(): Boolean? = value

    override fun setConsent(consent: Boolean) {
        value = consent
    }
}

private class FakeAiProvider(private val result: AiResult<String>) : AiProvider {
    override val id = "fake"
    override val displayName = "fake"
    var calls = 0
        private set

    override suspend fun isAvailable(): Boolean = true

    override suspend fun complete(
        messages: List<AiMessage>,
        config: AiConfig,
    ): AiResult<String> {
        calls++
        return result
    }
}

class AiSettingsStateTest {
    private fun testState(
        manifest: List<ModelManifestEntry> = emptyList(),
        modelManager: ModelManager = FakeModelManager(emptyList()),
        onDeviceLlm: OnDeviceLlm = FakeOnDeviceLlm(AVAILABLE_CAPS),
        keys: MutableMap<ProviderId, String?> = mutableMapOf(),
        consentStore: AiConsentStore = FakeConsentStore(),
        providerFactory: (ProviderId, String) -> AiProvider? = { _, _ -> null },
        scope: kotlinx.coroutines.CoroutineScope,
    ) = AiSettingsState(
        modelManager = modelManager,
        manifest = manifest,
        onDeviceLlm = onDeviceLlm,
        getKey = { keys[it] },
        setKey = { id, key -> if (key.isNullOrBlank()) keys.remove(id) else keys[id] = key },
        scope = scope,
        consentStore = consentStore,
        providerFactory = providerFactory,
    )

    @Test
    fun modelRowsCarryTheirManifestLicenseFlag() =
        runTest {
            val manifest = listOf(entry("gated", requiresLicenseAck = true), entry("open", requiresLicenseAck = false))
            val manager = FakeModelManager(listOf(modelInfo("gated"), modelInfo("open")))
            val state = testState(manifest = manifest, modelManager = manager, scope = backgroundScope)

            val rows = state.uiState.value.models.associateBy { it.info.id }
            assertTrue(rows.getValue("gated").requiresLicenseAck)
            assertTrue(!rows.getValue("open").requiresLicenseAck)
        }

    @Test
    fun cloudProviderRowsCoverAllThreeVendorsUnselectedByDefault() =
        runTest {
            val state = testState(scope = backgroundScope)

            val ids = state.uiState.value.providers.map { it.providerId }.toSet()
            assertEquals(setOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.GEMINI), ids)
            assertEquals(ProviderId.OFFLINE_FALLBACK, state.uiState.value.selectedProvider)
        }

    @Test
    fun setAiConsentPersistsThroughTheStore() =
        runTest {
            val store = FakeConsentStore()
            val state = testState(consentStore = store, scope = backgroundScope)

            state.setAiConsent(true)

            assertTrue(state.uiState.value.aiConsentGiven)
            assertEquals(true, store.consentGiven())
        }

    @Test
    fun selectProviderUpdatesSelection() =
        runTest {
            val state = testState(scope = backgroundScope)

            state.selectProvider(ProviderId.GEMINI)

            assertEquals(ProviderId.GEMINI, state.uiState.value.selectedProvider)
        }

    @Test
    fun modelManagerUpdatesPropagateIntoUiState() =
        runTest {
            val manager = FakeModelManager(listOf(modelInfo("m1")))
            val state = testState(manifest = listOf(entry("m1")), modelManager = manager, scope = backgroundScope)
            runCurrent()

            manager.push("m1", modelInfo("m1", state = ModelDownloadState.DOWNLOADING, progress = 0.4f))
            runCurrent()

            val row = state.uiState.value.models.single()
            assertEquals(ModelDownloadState.DOWNLOADING, row.info.state)
            assertEquals(0.4f, row.info.progress)
        }

    @Test
    fun startDownloadForwardsTheLicenseAcknowledgement() =
        runTest {
            val manager = FakeModelManager(listOf(modelInfo("gated")))
            val state = testState(manifest = listOf(entry("gated", requiresLicenseAck = true)), modelManager = manager, scope = backgroundScope)

            state.startDownload("gated", licenseAcknowledged = true)
            runCurrent()

            assertEquals(listOf("gated" to true), manager.downloadCalls)
        }

    @Test
    fun pauseCancelsAnInFlightDownloadAndStartCanResumeAfresh() =
        runTest {
            // download() never returns on its own (mirrors a real in-flight HTTP transfer) — the
            // only way a second call happens is if pauseDownload actually cancels the first job.
            val manager = SlowFakeModelManager()
            val state = testState(manifest = listOf(entry("m1")), modelManager = manager, scope = backgroundScope)

            state.startDownload("m1")
            runCurrent()
            assertEquals(listOf("m1"), manager.downloadCalls)

            state.pauseDownload("m1")
            state.startDownload("m1")
            runCurrent()

            assertEquals(listOf("m1", "m1"), manager.downloadCalls)
        }

    @Test
    fun deleteModelDelegatesToTheManager() =
        runTest {
            val manager = FakeModelManager(listOf(modelInfo("m1")))
            val state = testState(manifest = listOf(entry("m1")), modelManager = manager, scope = backgroundScope)

            state.deleteModel("m1")
            runCurrent()

            assertEquals(listOf("m1"), manager.deleteCalls)
        }

    @Test
    fun setProviderKeySavesAndClearRemovesIt() =
        runTest {
            val keys = mutableMapOf<ProviderId, String?>()
            val state = testState(keys = keys, scope = backgroundScope)

            state.setProviderKey(ProviderId.GEMINI, "a-key")
            assertTrue(state.uiState.value.providers.single { it.providerId == ProviderId.GEMINI }.hasKey)
            assertEquals("a-key", keys[ProviderId.GEMINI])

            state.clearProviderKey(ProviderId.GEMINI)
            assertTrue(!state.uiState.value.providers.single { it.providerId == ProviderId.GEMINI }.hasKey)
            assertNull(keys[ProviderId.GEMINI])
        }

    @Test
    fun testKeyWithNoSavedKeyFailsWithNoKeyWithoutTouchingTheNetwork() =
        runTest {
            var factoryCalls = 0
            val state = testState(providerFactory = { _, _ -> factoryCalls++; null }, scope = backgroundScope)

            state.testKey(ProviderId.ANTHROPIC)

            val row = state.uiState.value.providers.single { it.providerId == ProviderId.ANTHROPIC }
            assertEquals(KeyTestOutcome.FAILED, row.testOutcome)
            assertEquals(AiFailure.NoKey, row.testFailure)
            assertEquals(0, factoryCalls)
        }

    @Test
    fun testKeySuccessReportsOk() =
        runTest {
            val keys = mutableMapOf<ProviderId, String?>(ProviderId.ANTHROPIC to "a-key")
            val provider = FakeAiProvider(Result.Success("OK"))
            val state = testState(keys = keys, providerFactory = { _, _ -> provider }, scope = backgroundScope)

            state.testKey(ProviderId.ANTHROPIC)
            assertEquals(KeyTestOutcome.TESTING, state.uiState.value.providers.single { it.providerId == ProviderId.ANTHROPIC }.testOutcome)
            runCurrent()

            val row = state.uiState.value.providers.single { it.providerId == ProviderId.ANTHROPIC }
            assertEquals(KeyTestOutcome.OK, row.testOutcome)
            assertNull(row.testFailure)
            assertEquals(1, provider.calls)
        }

    @Test
    fun testKeyFailureRecordsTheTypedReason() =
        runTest {
            val keys = mutableMapOf<ProviderId, String?>(ProviderId.OPENAI to "wrong-key")
            val provider = FakeAiProvider(Result.Failure(AiFailure.Unauthorized))
            val state = testState(keys = keys, providerFactory = { _, _ -> provider }, scope = backgroundScope)

            state.testKey(ProviderId.OPENAI)
            runCurrent()

            val row = state.uiState.value.providers.single { it.providerId == ProviderId.OPENAI }
            assertEquals(KeyTestOutcome.FAILED, row.testOutcome)
            assertEquals(AiFailure.Unauthorized, row.testFailure)
        }
}
