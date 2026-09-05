package com.siddharth.kmp.llmchat

import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure
import com.siddharth.kmp.result.AiResult
import com.siddharth.kmp.result.Result
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [AiProvider.capabilities] — the honest, machine-readable descriptor a caller reads instead of
 * having to open provider source to learn which [AiConfig] field silently no-ops. All three real
 * HTTP providers stream for real and honor the same three [AiConfig] fields identically; the
 * default (an unmigrated implementer) stays conservative and blames a missing key.
 */
class AiProviderCapabilitiesTest {
    private val honoredCloudFields = setOf("maxTokens", "temperature", "timeoutMs")

    private class BareProvider(
        private val available: Boolean,
    ) : AiProvider {
        override val id = "bare"
        override val displayName = "Bare"

        override suspend fun isAvailable() = available

        override suspend fun complete(
            messages: List<AiMessage>,
            config: AiConfig,
        ): AiResult<String> = Result.Success("unused")
    }

    @Test
    fun default_capabilities_blames_missing_key_when_unavailable() =
        runTest {
            assertEquals(AiFailure.NoKey, BareProvider(available = false).capabilities().unavailableReason)
        }

    @Test
    fun default_capabilities_reports_no_failure_when_available() =
        runTest {
            assertNull(BareProvider(available = true).capabilities().unavailableReason)
        }

    @Test
    fun anthropicProvider_capabilities_streams_and_honors_the_shared_config_fields() =
        runTest {
            assertEquals(
                AiCapabilities(streaming = true, multimodal = false, honoredConfigFields = honoredCloudFields, unavailableReason = null),
                AnthropicProvider("key").capabilities(),
            )
        }

    @Test
    fun openAiProvider_capabilities_streams_and_honors_the_shared_config_fields() =
        runTest {
            assertEquals(
                AiCapabilities(streaming = true, multimodal = false, honoredConfigFields = honoredCloudFields, unavailableReason = null),
                OpenAiProvider("key").capabilities(),
            )
        }

    @Test
    fun geminiProvider_capabilities_streams_and_honors_the_shared_config_fields() =
        runTest {
            assertEquals(
                AiCapabilities(streaming = true, multimodal = false, honoredConfigFields = honoredCloudFields, unavailableReason = null),
                GeminiProvider("key").capabilities(),
            )
        }

    @Test
    fun cloudProviders_capabilities_report_noKey_when_apiKey_is_blank() =
        runTest {
            assertEquals(AiFailure.NoKey, AnthropicProvider("").capabilities().unavailableReason)
            assertEquals(AiFailure.NoKey, OpenAiProvider("").capabilities().unavailableReason)
            assertEquals(AiFailure.NoKey, GeminiProvider("").capabilities().unavailableReason)
        }
}
