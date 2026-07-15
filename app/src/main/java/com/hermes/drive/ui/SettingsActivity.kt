package com.hermes.drive.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.hermes.drive.settings.HermesSettings
import com.hermes.drive.settings.ModelManager
import com.hermes.drive.settings.SettingsStore
import com.hermes.drive.DriveAssistantService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private lateinit var store: SettingsStore
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* notified either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        if (Build.VERSION.SDK_INT >= 33) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            androidx.compose.material3.MaterialTheme {
                val settings by produceState(initialValue = HermesSettings(), key1 = store) {
                    value = store.settings.first()
                }
                var downloading by remember { mutableStateOf(false) }
                var status by remember { mutableStateOf("") }
                var running by remember { mutableStateOf(false) }
                val initialUrl = remember(settings.cloudBaseUrl) {
                    settings.cloudBaseUrl.ifBlank { SettingsStore.DEFAULT_MODEL_URL }
                }

                SettingsScreen(
                    settings = settings,
                    onModelSizeChange = { size -> lifecycleScope.launch { store.setModelSize(size) } },
                    onCloudToggle = { on -> lifecycleScope.launch { store.setUseCloudFallback(on) } },
                    onCloudUrlChange = { url -> lifecycleScope.launch { store.setCloudBaseUrl(url) } },
                    onDownload = { size, url ->
                        if (url.isBlank()) {
                            status = "Enter a model download URL first."
                            return@SettingsScreen
                        }
                        downloading = true
                        status = "Downloading…"
                        lifecycleScope.launch {
                            val res = ModelManager.download(this@SettingsActivity, size, url)
                            downloading = false
                            status = if (res.isSuccess) {
                                "Downloaded. Restart the app to load it."
                            } else {
                                "Failed: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    modelExists = { size -> ModelManager.modelExists(this@SettingsActivity, size) },
                    downloading = downloading,
                    status = status,
                    initialUrl = initialUrl,
                    running = running,
                    onToggleRunning = {
                        if (running) {
                            DriveAssistantService.stop(this@SettingsActivity)
                            running = false
                        } else {
                            DriveAssistantService.start(this@SettingsActivity)
                            running = true
                        }
                    },
                    onOpenDebug = {
                        startActivity(Intent(this@SettingsActivity, DebugActivity::class.java))
                    },
                )
            }
        }
    }
}
