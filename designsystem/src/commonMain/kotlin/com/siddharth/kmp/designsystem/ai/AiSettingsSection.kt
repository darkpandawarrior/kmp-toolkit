package com.siddharth.kmp.designsystem.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.siddharth.kmp.ai.ModelDownloadState
import com.siddharth.kmp.designsystem.DesignTokens
import com.siddharth.kmp.designsystem.SectionCard
import com.siddharth.kmp.designsystem.TagChipRow
import com.siddharth.kmp.llmchat.ProviderId
import com.siddharth.kmp.result.AiCapabilities
import com.siddharth.kmp.result.AiFailure

/**
 * The settings screen `ModelManager`/`AiProvider` never had (see the lane brief): on-device model
 * download/pause/resume/delete with progress and license gating, plus cloud-provider key entry
 * with a real "test key" call. Pure render — every action is a callback into [AiSettingsState] so
 * this composable stays testable via `runComposeUiTest` with a fake [AiSettingsUiState] and no
 * live [com.siddharth.kmp.ai.ModelManager]/[com.siddharth.kmp.llmchat.AiProvider].
 */
@Composable
fun AiSettingsSection(
    uiState: AiSettingsUiState,
    onConsentChange: (Boolean) -> Unit,
    onStartDownload: (modelId: String, licenseAcknowledged: Boolean) -> Unit,
    onPauseDownload: (modelId: String) -> Unit,
    onDeleteModel: (modelId: String) -> Unit,
    onSelectProvider: (ProviderId) -> Unit,
    onProviderKeyChange: (ProviderId, String) -> Unit,
    onClearProviderKey: (ProviderId) -> Unit,
    onTestProviderKey: (ProviderId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.l)) {
        AiConsentCard(uiState.aiConsentGiven, onConsentChange)
        OnDeviceModelsCard(
            capabilities = uiState.onDeviceCapabilities,
            models = uiState.models,
            onStartDownload = onStartDownload,
            onPauseDownload = onPauseDownload,
            onDelete = onDeleteModel,
        )
        CloudProviderCard(
            providers = uiState.providers,
            selectedProvider = uiState.selectedProvider,
            onSelectProvider = onSelectProvider,
            onKeyChange = onProviderKeyChange,
            onClearKey = onClearProviderKey,
            onTestKey = onTestProviderKey,
        )
    }
}

@Composable
private fun AiConsentCard(
    consentGiven: Boolean,
    onChange: (Boolean) -> Unit,
) {
    SectionCard(
        title = "AI features",
        subtitle =
            if (consentGiven) {
                "On — this app may call an on-device or cloud model."
            } else {
                "Off — no on-device or cloud model call will run."
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Enable AI features", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = consentGiven, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun OnDeviceModelsCard(
    capabilities: AiCapabilities?,
    models: List<OnDeviceModelRow>,
    onStartDownload: (modelId: String, licenseAcknowledged: Boolean) -> Unit,
    onPauseDownload: (modelId: String) -> Unit,
    onDelete: (modelId: String) -> Unit,
) {
    SectionCard(title = "On-device model", subtitle = capabilities.availabilitySubtitle()) {
        if (models.isEmpty()) {
            Text(
                "No downloadable model on this platform.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            models.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = DesignTokens.Spacing.s))
                OnDeviceModelRowContent(
                    row = row,
                    onStartDownload = { ack -> onStartDownload(row.info.id, ack) },
                    onPauseDownload = { onPauseDownload(row.info.id) },
                    onDelete = { onDelete(row.info.id) },
                )
            }
        }
    }
}

@Composable
private fun OnDeviceModelRowContent(
    row: OnDeviceModelRow,
    onStartDownload: (licenseAcknowledged: Boolean) -> Unit,
    onPauseDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    // ponytail: plain `remember`, not `rememberSaveable` — this module doesn't otherwise depend on
    // the runtime-saveable artifact. Losing this tick on a config change just means re-ticking the
    // box; upgrade to rememberSaveable if that proves annoying enough to matter.
    var licenseAccepted by remember(row.info.id) { mutableStateOf(false) }
    val needsLicenseGate =
        row.requiresLicenseAck &&
            row.info.state in setOf(ModelDownloadState.ABSENT, ModelDownloadState.FAILED, ModelDownloadState.PARTIALLY_DOWNLOADED)

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(row.info.displayName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${row.info.approxSizeMb} MB · ${row.info.state.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ModelActionButton(
                state = row.info.state,
                enabled = !needsLicenseGate || licenseAccepted,
                onStartDownload = { onStartDownload(licenseAccepted) },
                onPauseDownload = onPauseDownload,
                onDelete = onDelete,
            )
        }
        if (row.info.state == ModelDownloadState.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { row.info.progress },
                modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.xs),
            )
            row.info.downloadProgress?.let { progress ->
                Text(
                    "${progress.bytesPerSec / BYTES_PER_MB} MB/s" + if (progress.etaMs >= 0) " · ${progress.etaMs / MS_PER_SEC}s left" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (needsLicenseGate) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = DesignTokens.Spacing.xs)) {
                Checkbox(checked = licenseAccepted, onCheckedChange = { licenseAccepted = it })
                Text("I accept the model license", style = MaterialTheme.typography.bodySmall)
            }
        }
        row.info.error?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = DesignTokens.Spacing.xs),
            )
        }
    }
}

@Composable
private fun ModelActionButton(
    state: ModelDownloadState,
    enabled: Boolean,
    onStartDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    when (state) {
        ModelDownloadState.ABSENT, ModelDownloadState.FAILED ->
            TextButton(onClick = onStartDownload, enabled = enabled) { Text("Download") }
        ModelDownloadState.PARTIALLY_DOWNLOADED ->
            TextButton(onClick = onStartDownload, enabled = enabled) { Text("Resume") }
        ModelDownloadState.DOWNLOADING ->
            TextButton(onClick = onPauseDownload) { Text("Pause") }
        ModelDownloadState.READY ->
            TextButton(onClick = onDelete) { Text("Delete") }
    }
}

private val CLOUD_PROVIDER_LABELS =
    linkedMapOf(
        ProviderId.ANTHROPIC to "Anthropic",
        ProviderId.OPENAI to "OpenAI",
        ProviderId.GEMINI to "Gemini",
    )

@Composable
private fun CloudProviderCard(
    providers: List<ProviderRow>,
    selectedProvider: ProviderId,
    onSelectProvider: (ProviderId) -> Unit,
    onKeyChange: (ProviderId, String) -> Unit,
    onClearKey: (ProviderId) -> Unit,
    onTestKey: (ProviderId) -> Unit,
) {
    SectionCard(title = "Cloud provider", subtitle = "Bring your own API key — tried in this order when on-device is off or unavailable.") {
        TagChipRow(
            tags = CLOUD_PROVIDER_LABELS.values.toList(),
            selected = setOfNotNull(CLOUD_PROVIDER_LABELS[selectedProvider]),
            onTagClick = { label -> CLOUD_PROVIDER_LABELS.entries.first { it.value == label }.key.let(onSelectProvider) },
        )
        providers.forEachIndexed { index, row ->
            Spacer(Modifier.height(DesignTokens.Spacing.m))
            if (index > 0) HorizontalDivider(modifier = Modifier.padding(bottom = DesignTokens.Spacing.m))
            ProviderKeyRow(
                row = row,
                onKeyChange = { key -> onKeyChange(row.providerId, key) },
                onClear = { onClearKey(row.providerId) },
                onTest = { onTestKey(row.providerId) },
            )
        }
    }
}

@Composable
private fun ProviderKeyRow(
    row: ProviderRow,
    onKeyChange: (String) -> Unit,
    onClear: () -> Unit,
    onTest: () -> Unit,
) {
    // Cleared after every successful Save: the field is a paste-and-submit box, not a place the
    // real (already-encrypted) key round-trips back into for display.
    var draft by remember(row.providerId) { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        Text(CLOUD_PROVIDER_LABELS.getValue(row.providerId), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = DesignTokens.Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text(if (row.hasKey) "•••• key saved" else "Paste API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onKeyChange(draft); draft = "" },
                enabled = draft.isNotBlank(),
            ) { Text("Save") }
        }
        Row(
            modifier = Modifier.padding(top = DesignTokens.Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClear, enabled = row.hasKey) { Text("Clear") }
            TextButton(onClick = onTest, enabled = row.hasKey && row.testOutcome != KeyTestOutcome.TESTING) { Text("Test key") }
            when (row.testOutcome) {
                KeyTestOutcome.TESTING -> CircularProgressIndicator(modifier = Modifier.size(DesignTokens.Size.iconInline), strokeWidth = 2.dp)
                KeyTestOutcome.OK -> Text("Key works", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                KeyTestOutcome.FAILED ->
                    Text(
                        row.testFailure?.label() ?: "Test failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                KeyTestOutcome.UNTESTED -> Unit
            }
        }
    }
}

private fun AiCapabilities?.availabilitySubtitle(): String {
    if (this == null) return "Checking availability…"
    val reason = unavailableReason
    return when {
        reason != null -> "Off — ${reason.label()}"
        streaming -> "Available · streams tokens"
        else -> "Available"
    }
}

private fun ModelDownloadState.label(): String =
    when (this) {
        ModelDownloadState.ABSENT -> "Not downloaded"
        ModelDownloadState.DOWNLOADING -> "Downloading…"
        ModelDownloadState.PARTIALLY_DOWNLOADED -> "Paused"
        ModelDownloadState.READY -> "Ready"
        ModelDownloadState.FAILED -> "Failed"
    }

private fun AiFailure.label(): String =
    when (this) {
        AiFailure.NoKey -> "No key saved"
        AiFailure.Unauthorized -> "Key rejected (unauthorized)"
        AiFailure.RateLimited -> "Rate limited — try again shortly"
        AiFailure.Timeout -> "Timed out"
        AiFailure.Network -> "Network error"
        AiFailure.ModelNotResident -> "Model not downloaded yet"
        AiFailure.NotSupportedOnPlatform -> "Not supported on this platform"
        AiFailure.EmptyReply -> "Model returned no reply"
    }

private const val BYTES_PER_MB = 1_000_000L
private const val MS_PER_SEC = 1_000L
