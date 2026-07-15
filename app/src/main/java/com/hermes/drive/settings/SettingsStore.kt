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

        // .litertlm model files, expected under the app's filesDir (downloaded once over WiFi)
        const val FAST_MODEL_FILE = "gemma3-1b-it.litertlm"
        const val QUALITY_MODEL_FILE = "gemma3-4b-it.litertlm"
    }
}
