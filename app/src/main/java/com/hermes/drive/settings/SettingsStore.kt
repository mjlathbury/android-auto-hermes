package com.hermes.drive.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<androidx.datastore.preferences.core.Preferences> by preferencesDataStore(
    name = "hermes_settings",
)

data class HermesSettings(
    val modelSize: String = "fast",
    val useCloudFallback: Boolean = false,
    val cloudBaseUrl: String = "",
)

class SettingsStore(private val context: Context) {

    private val MODEL_SIZE = stringPreferencesKey("model_size")
    private val USE_CLOUD = booleanPreferencesKey("use_cloud_fallback")
    private val CLOUD_URL = stringPreferencesKey("cloud_base_url")

    val settings: Flow<HermesSettings> = context.dataStore.data.map { prefs ->
        HermesSettings(
            modelSize = prefs[MODEL_SIZE] ?: MODEL_FAST,
            useCloudFallback = prefs[USE_CLOUD] ?: false,
            cloudBaseUrl = prefs[CLOUD_URL] ?: "",
        )
    }

    suspend fun setModelSize(size: String) {
        context.dataStore.edit { it[MODEL_SIZE] = size }
    }

    suspend fun setUseCloudFallback(on: Boolean) {
        context.dataStore.edit { it[USE_CLOUD] = on }
    }

    suspend fun setCloudBaseUrl(url: String) {
        context.dataStore.edit { it[CLOUD_URL] = url }
    }

    companion object {
        const val MODEL_FAST = "fast"
        const val MODEL_QUALITY = "quality"

        // .litertlm model files, expected under the app's filesDir (downloaded once over WiFi).
        // Both are UNGATED on HuggingFace, so in-app download works with one tap for either.
        // Default (fast) is the small Qwen3-0.6B; quality is Qwen2.5-1.5B (smarter, slower).
        const val FAST_MODEL_FILE = "Qwen3-0.6B.litertlm"
        const val QUALITY_MODEL_FILE = "qwen2.5-1.5b-instruct.litertlm"

        // Default download URLs (ungated). The quality file is the one already on-device for you.
        const val FAST_MODEL_URL =
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm?download=true"
        const val QUALITY_MODEL_URL =
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true"

        /** Default download URL for a given size (used to prefill the URL field). */
        fun urlForSize(size: String): String =
            if (size == MODEL_QUALITY) QUALITY_MODEL_URL else FAST_MODEL_URL

        // Default download URL used when the settings field is blank.
        const val DEFAULT_MODEL_URL = FAST_MODEL_URL
    }
}
