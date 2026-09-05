package com.siddharth.kmp.designsystem.ai

import com.siddharth.kmp.ai.ModelInfo
import com.siddharth.kmp.ai.ModelManager
import com.siddharth.kmp.ai.ModelManifestEntry
import com.siddharth.kmp.ai.OnDeviceLlm
import com.siddharth.kmp.llmchat.AiConfig
import com.siddharth.kmp.llmchat.AiMessage
import com.siddharth.kmp.llmchat.AiProvider
import com.siddharth.kmp.llmchat.AnthropicProvider
import com.siddharth.kmp.llmchat.GeminiProvider
import com.siddharth.kmp.llmchat.OpenAiProvider
import com.siddharth.kmp.llmchat.ProviderId
import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.Result
import com.siddharth.kmp.result.fold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where the AI-consent choice persists. Same seam shape as
 * [com.siddharth.kmp.designsystem.ThemeStore]: the default is in-memory, an app binds a
 * persistent implementation (DataStore/Settings) in Koin so the choice survives process death.
 */
interface AiConsentStore {
    /** The persisted consent choice, or null if the user has never been asked. */
    fun consentGiven(): Boolean?

    fun setConsent(consent: Boolean)
}

/** Default seam: holds the choice for the process lifetime only. */
object InMemoryAiConsentStore : AiConsentStore {
    private var value: Boolean? = null

    override fun consentGiven(): Boolean? = value

    override fun setConsent(consent: Boolean) {
        value = consent
    }
}

/** One row in the on-device model list: the manager's live [ModelInfo] plus its manifest metadata. */
data class OnDeviceModelRow(
    val info: ModelInfo,
    /** True when this model's license requires an explicit acknowledgement before [AiSettingsState.downloadModel]. */
    val requiresLicenseAck: Boolean,
)

/** Outcome of the settings screen's "test key" action for one cloud provider. */
enum class KeyTestOutcome { UNTESTED, TESTING, OK, FAILED }

/** One row in the cloud-provider picker: whether a key is saved, and the last "test key" outcome. */
data class ProviderRow(
    val providerId: ProviderId,
    val hasKey: Boolean,
    val testOutcome: KeyTestOutcome = KeyTestOutcome.UNTESTED,
    /** Only set once [testOutcome] is [KeyTestOutcome.FAILED] — the real reason, not a guess. */
    val testFailure: AiFailure? = null,
)

data class AiSettingsUiState(
    val aiConsentGiven: Boolean,
    /** Null until the first [OnDeviceLlm.capabilities] read completes. */
    val onDeviceCapabilities: AiCapabilities?,
    val models: List<OnDeviceModelRow>,
    val providers: List<ProviderRow>,
    val selectedProvider: ProviderId,
)

/**
 * Builds the real cloud [AiProvider] for a "test key" attempt — the same three vendor classes
 * [com.siddharth.kmp.llmchat.buildProviderChain] uses. Null for [ProviderId.ON_DEVICE]/
 * [ProviderId.OFFLINE_FALLBACK]: neither takes a cloud key, so there's nothing to test.
 */
private fun defaultCloudProvider(
    providerId: ProviderId,
    apiKey: String,
): AiProvider? =
    when (providerId) {
        ProviderId.ANTHROPIC -> AnthropicProvider(apiKey)
        ProviderId.OPENAI -> OpenAiProvider(apiKey)
        ProviderId.GEMINI -> GeminiProvider(apiKey)
        ProviderId.ON_DEVICE, ProviderId.OFFLINE_FALLBACK -> null
    }

/**
 * State holder behind `AiSettingsSection` — the ModelManager settings screen the toolkit never
 * shipped one for. Same shape as [com.siddharth.kmp.designsystem.ThemeController]: a plain class,
 * [StateFlow]-exposed, testable with fakes in commonTest, bound as a Koin screen-scoped instance
 * by the consuming app.
 *
 * Provider keys are read/written through [getKey]/[setKey] rather than a
 * [com.siddharth.kmp.llmchat.SecureKeyStore] directly — that type has a different constructor per
 * platform (Android needs a `Context`), so it can't be built generically in commonMain; the same
 * reason [com.siddharth.kmp.llmchat.loadAiProviderConfig] takes a plain function instead. Pass
 * `keyStore::getKey` / `keyStore::setKey` at the call site.
 *
 * [manifest] is caller-supplied because [ModelManager] itself doesn't expose one (by design — see
 * [ModelManifestEntry]'s KDoc): pass the same list the app gave its `ModelManager`.
 */
class AiSettingsState(
    private val modelManager: ModelManager,
    manifest: List<ModelManifestEntry>,
    private val onDeviceLlm: OnDeviceLlm,
    private val getKey: (ProviderId) -> String?,
    private val setKey: (ProviderId, String?) -> Unit,
    private val scope: CoroutineScope,
    private val consentStore: AiConsentStore = InMemoryAiConsentStore,
    private val providerFactory: (ProviderId, String) -> AiProvider? = ::defaultCloudProvider,
) {
    private val licenseAckByModelId: Map<String, Boolean> = manifest.associate { it.id to it.requiresLicenseAck }

    private val _state =
        MutableStateFlow(
            AiSettingsUiState(
                aiConsentGiven = consentStore.consentGiven() ?: false,
                onDeviceCapabilities = null,
                models = modelManager.models().map(::toRow),
                providers = CLOUD_PROVIDERS.map(::toProviderRow),
                selectedProvider = ProviderId.OFFLINE_FALLBACK,
            ),
        )
    val uiState: StateFlow<AiSettingsUiState> = _state.asStateFlow()

    init {
        scope.launch {
            val caps = onDeviceLlm.capabilities()
            _state.update { it.copy(onDeviceCapabilities = caps) }
        }
        // One collector per manifest model rather than per ModelManager.models() entry: a model
        // absent from models() today (not yet on this platform's manifest) still gets observed the
        // moment it's added, without restarting AiSettingsState.
        manifest.forEach { entry ->
            scope.launch {
                modelManager.observe(entry.id).collect { updated ->
                    _state.update { s -> s.replaceModel(updated) }
                }
            }
        }
    }

    fun setAiConsent(consent: Boolean) {
        consentStore.setConsent(consent)
        _state.update { it.copy(aiConsentGiven = consent) }
    }

    fun selectProvider(providerId: ProviderId) {
        _state.update { it.copy(selectedProvider = providerId) }
    }

    /** Saves (or, when [apiKey] is null/blank, clears) the key for [providerId]. */
    fun setProviderKey(
        providerId: ProviderId,
        apiKey: String?,
    ) {
        setKey(providerId, apiKey)
        _state.update { s -> s.replaceProvider(toProviderRow(providerId)) }
    }

    fun clearProviderKey(providerId: ProviderId) = setProviderKey(providerId, null)

    /**
     * Runs one real completion against [providerId]'s saved key and records the typed [AiFailure]
     * if it fails — so a wrong key reads "unauthorized" here instead of an empty reply three
     * screens later.
     */
    fun testKey(providerId: ProviderId) {
        val key = getKey(providerId)
        val provider = key?.takeIf { it.isNotBlank() }?.let { providerFactory(providerId, it) }
        if (provider == null) {
            _state.update { s -> s.updateProvider(providerId) { it.copy(testOutcome = KeyTestOutcome.FAILED, testFailure = AiFailure.NoKey) } }
            return
        }
        _state.update { s -> s.updateProvider(providerId) { it.copy(testOutcome = KeyTestOutcome.TESTING, testFailure = null) } }
        scope.launch {
            val result = provider.complete(listOf(AiMessage(AiMessage.Role.USER, TEST_PROMPT)), AiConfig(maxTokens = TEST_MAX_TOKENS))
            _state.update { s ->
                s.updateProvider(providerId) { row ->
                    result.fold(
                        onSuccess = { row.copy(testOutcome = KeyTestOutcome.OK, testFailure = null) },
                        onFailure = { reason -> row.copy(testOutcome = KeyTestOutcome.FAILED, testFailure = reason) },
                    )
                }
            }
        }
    }

    /** Job of the in-flight [ModelManager.download] call per model id, so [pauseDownload] can cancel it. */
    private val downloadJobs = mutableMapOf<String, Job>()

    /**
     * Starts (or resumes) downloading [modelId] in this state's own [scope]. [licenseAcknowledged]
     * must be true for a model whose [OnDeviceModelRow.requiresLicenseAck] is true — see
     * [ModelManager.download].
     */
    fun startDownload(
        modelId: String,
        licenseAcknowledged: Boolean = false,
    ) {
        downloadJobs[modelId]?.cancel()
        downloadJobs[modelId] = scope.launch { modelManager.download(modelId, licenseAcknowledged) }
    }

    /**
     * Cancels an in-flight download. [ModelManager.download] resumes from the `.tmp` a cancelled
     * transfer leaves behind (surfaced as [com.siddharth.kmp.ai.ModelDownloadState.PARTIALLY_DOWNLOADED]),
     * so this is a real pause, not a restart-from-zero — no new cancellation plumbing needed, the
     * manager already tears down its resumable state on any interruption.
     */
    fun pauseDownload(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
    }

    fun deleteModel(modelId: String) {
        downloadJobs.remove(modelId)?.cancel()
        scope.launch { modelManager.delete(modelId) }
    }

    private fun toRow(info: ModelInfo) = OnDeviceModelRow(info, licenseAckByModelId[info.id] == true)

    private fun toProviderRow(providerId: ProviderId) =
        ProviderRow(providerId = providerId, hasKey = !getKey(providerId).isNullOrBlank())

    private fun AiSettingsUiState.replaceModel(updated: ModelInfo): AiSettingsUiState =
        copy(models = models.map { row -> if (row.info.id == updated.id) row.copy(info = updated) else row })

    private fun AiSettingsUiState.replaceProvider(updated: ProviderRow): AiSettingsUiState =
        copy(providers = providers.map { row -> if (row.providerId == updated.providerId) updated else row })

    private fun AiSettingsUiState.updateProvider(
        providerId: ProviderId,
        transform: (ProviderRow) -> ProviderRow,
    ): AiSettingsUiState = copy(providers = providers.map { row -> if (row.providerId == providerId) transform(row) else row })

    companion object {
        private const val TEST_PROMPT = "Reply with OK."
        private const val TEST_MAX_TOKENS = 8
        private val CLOUD_PROVIDERS = listOf(ProviderId.ANTHROPIC, ProviderId.OPENAI, ProviderId.GEMINI)
    }
}
