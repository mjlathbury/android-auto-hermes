package com.hermes.drive

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny debug sink used so we can see what the assistant is doing on-device (no cable required).
 * Every event is both sent to logcat (tag "Hermes") and appended to filesDir/hermes_debug.log,
 * which the user can `adb pull` and paste back. Ring-buffered to the last ~500 lines.
 */
object DebugLog {
    const val TAG = "Hermes"
    const val MAX_LINES = 500

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun file(context: Context): File = File(context.filesDir, "hermes_debug.log")

    @Synchronized
    fun event(context: Context, msg: String) {
        val line = "${fmt.format(Date())}  $msg"
        Log.d(TAG, msg)
        try {
            val f = file(context)
            val lines = if (f.exists()) f.readLines() else emptyList()
            val next = (lines + line).takeLast(MAX_LINES)
            f.writeText(next.joinToString("\n"))
        } catch (_: Exception) {
            // best-effort; logcat already has it
        }
    }

    fun read(context: Context): String =
        try { file(context).readText() } catch (_: Exception) { "(no debug log yet)" }
}
