package com.hermes.drive.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Locates and downloads the on-device .litertlm model.
 * The model is a single file placed in the app's filesDir. For a large model the user may
 * instead `adb push` it there directly (see README).
 */
object ModelManager {

    fun modelFileFor(size: String): String =
        if (size == SettingsStore.MODEL_QUALITY) SettingsStore.QUALITY_MODEL_FILE else SettingsStore.FAST_MODEL_FILE

    fun modelExists(context: Context, size: String): Boolean =
        File(context.filesDir, modelFileFor(size)).exists()

    /** Download a .litertlm model from [url] into filesDir. Runs on IO dispatcher. */
    suspend fun download(context: Context, size: String, url: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(context.filesDir, modelFileFor(size))
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000
                conn.inputStream.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                Unit
            }
        }
}
