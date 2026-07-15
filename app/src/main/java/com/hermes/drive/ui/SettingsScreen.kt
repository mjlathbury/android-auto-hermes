package com.hermes.drive.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.drive.settings.HermesSettings
import com.hermes.drive.settings.SettingsStore
import com.hermes.drive.BuildConfig

@Composable
fun SettingsScreen(
    settings: HermesSettings,
    onModelSizeChange: (String) -> Unit,
    onCloudToggle: (Boolean) -> Unit,
    onCloudUrlChange: (String) -> Unit,
    onDownload: (String, String) -> Unit,
    modelExists: (String) -> Boolean,
    downloading: Boolean,
    status: String,
    initialUrl: String,
    running: Boolean,
    onToggleRunning: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Hermes Drive", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            Text("On-device model", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = settings.modelSize == SettingsStore.MODEL_FAST,
                    onClick = { onModelSizeChange(SettingsStore.MODEL_FAST) },
                    label = { Text("Fast (Qwen 1.5B)") },
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = settings.modelSize == SettingsStore.MODEL_QUALITY,
                    onClick = { onModelSizeChange(SettingsStore.MODEL_QUALITY) },
                    label = { Text("Quality (Gemma 4B, gated)") },
                )
            }
            Text(
                if (modelExists(settings.modelSize)) {
                    "Model present on device."
                } else {
                    "Model missing — download or push via adb (see README)."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            var urlState by remember(initialUrl) {
                mutableStateOf(initialUrl)
            }
            OutlinedTextField(
                value = urlState,
                onValueChange = {
                    urlState = it
                    onCloudUrlChange(it)
                },
                label = { Text("Model URL (.litertlm)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onDownload(settings.modelSize, urlState) }, enabled = !downloading) {
                Text(if (downloading) "Downloading…" else "Download model")
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onToggleRunning,
                enabled = modelExists(settings.modelSize),
            ) {
                Text(if (running) "Stop Hermes" else "Start Hermes")
            }
            Text(
                if (running) "Assistant is live — connect Android Auto or tap Reply on the notification to speak."
                else "Assistant is stopped.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenDebug) { Text("Debug log") }
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Text("Cloud fallback (optional)", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Use cloud when signal is strong", modifier = Modifier.weight(1f))
                Switch(checked = settings.useCloudFallback, onCheckedChange = onCloudToggle)
            }
            if (settings.useCloudFallback) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.cloudBaseUrl,
                    onValueChange = onCloudUrlChange,
                    label = { Text("OpenAI-compatible base URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            Text(
                "Build ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
