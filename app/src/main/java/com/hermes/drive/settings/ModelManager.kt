package com.hermes.drive.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Locates and downloads the on-device .litertlm model.
 * The model is a single file placed in the app's filesDir. For a large/gated model the user may
 * instead `adb push` it there directly (see README).
 */
object ModelManager {

    /** Allows tests to inject a fake connection without real network. */
    internal var connectionFactory: ((String) -> HttpURLConnection)? = null

    fun modelFileFor(size: String): String =
        if (size == SettingsStore.MODEL_QUALITY) SettingsStore.QUALITY_MODEL_FILE else SettingsStore.FAST_MODEL_FILE

    fun modelExists(context: Context, size: String): Boolean =
        File(context.filesDir, modelFileFor(size)).exists()

    /** Core download logic operating on an explicit target file (testable without a Context). */
    internal suspend fun downloadToFile(target: File, url: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = connectionFactory?.invoke(url) ?: (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                val code = conn.responseCode
                if (code !in 200..299) {
                    conn.inputStream.use { it.bufferedReader().use { r -> r.readText() } } // best-effort body
                    val hint = when (code) {
                        401, 403 -> "HTTP $code — this model is license-gated. Log in to HuggingFace and accept the license on a PC, then adb push the .litertlm file, or use an ungated model URL."
                        else -> "HTTP $code while fetching the model."
                    }
                    throw IllegalStateException(hint)
                }
                val len = conn.contentLength.toLong()
                conn.inputStream.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                if (target.length() == 0L) {
                    target.delete()
                    throw IllegalStateException("Downloaded file is empty (0 bytes) — the URL did not return a model.")
                }
                if (len > 0 && target.length() < len / 2) {
                    // Got far less than advertised; likely an HTML error page saved as .litertlm.
                    target.delete()
                    throw IllegalStateException("Download incomplete (got ${target.length()} of $len bytes) — URL may not point at a .litertlm file.")
                }
                Unit
            }
        }

    /**
     * Download a .litertlm model from [url] into filesDir. Runs on IO dispatcher.
     * Fails loudly with a descriptive message on HTTP errors (e.g. 401/403 license-gating),
     * empty files, or non-model content — so a gated/blocked model is obvious, not silent.
     */
    suspend fun download(context: Context, size: String, url: String): Result<Unit> =
        downloadToFile(File(context.filesDir, modelFileFor(size)), url)
}
