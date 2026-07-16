package com.hermes.drive

import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug sink that writes events to filesDir/hermes_debug.log (ring-buffered) and also mirrors to
 * logcat (tag "Hermes"). Because filesDir is private (unreadable without a PC), [exportBundle]
 * collects the debug log, any crash.log, and a recent logcat dump into a single text file under
 * the app's external files dir, which is reachable via USB/MTP or any file manager — and
 * [shareIntent] wraps it so the user can send it straight to me from the phone (no PC needed).
 */
object DebugLog {
    const val TAG = "Hermes"
    const val MAX_LINES = 500

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun file(context: Context): File = File(context.filesDir, "hermes_debug.log")
    fun crashFile(context: Context): File = File(context.filesDir, "crash.log")

    @Synchronized
    fun event(context: Context, msg: String) {
        val line = "${fmt.format(Date())}  $msg"
        Log.d(TAG, msg)
        try {
            val f = file(context)
            val lines = if (f.exists()) f.readLines() else emptyList()
            val next = (lines + line).takeLast(MAX_LINES)
            f.writeText(next.joinToString("\n"))
        } catch (_: Exception) { /* best-effort */ }
    }

    fun read(context: Context): String =
        try { file(context).readText() } catch (_: Exception) { "(no debug log yet)" }

    /** Build a single shareable text file with debug + crash + recent logcat. Returns its path. */
    fun exportBundle(context: Context): String {
        val sb = StringBuilder()
        sb.append("=== Hermes Drive debug bundle ===\n")
        sb.append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n\n")
        sb.append("=== hermes_debug.log ===\n")
        sb.append(runCatching { file(context).readText() }.getOrDefault("(no debug log)").ifBlank { "(empty)" })
        sb.append("\n\n=== crash.log ===\n")
        sb.append(runCatching { crashFile(context).readText() }.getOrDefault("(no crash log)").ifBlank { "(none)" })
        sb.append("\n\n=== recent logcat (Hermes + crashes) ===\n")
        sb.append(recentLogcat())
        val out = File(context.getExternalFilesDir(null), "hermes_debug_bundle.txt")
        out.writeText(sb.toString())
        return out.absolutePath
    }

    /** Last ~400 lines of logcat filtered to Hermes / crash / AndroidRuntime. */
    private fun recentLogcat(): String = runCatching {
        val proc = ProcessBuilder("logcat", "-d", "-t", "400",
            "*:S", "Hermes:V", "AndroidRuntime:E", "System.err:W")
            .redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        text.ifBlank { "(no Hermes logcat lines captured)" }
    }.getOrDefault("(could not read logcat)")

    /** Intent to share the bundle file via email/messaging (no PC required). */
    fun shareIntent(context: Context): Intent {
        val path = exportBundle(context)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "com.hermes.drive.fileprovider", File(path))
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Hermes Drive debug bundle")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
