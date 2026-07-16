package com.hermes.drive

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App entry point. Also installs a last-chance crash handler: if any thread dies uncaught (e.g.
 * an OutOfMemoryError while loading the model, or a native crash that surfaces as an Error), we
 * write the stack trace to filesDir/crash.log so the user can `adb pull` it and paste it back —
 * otherwise a silent process death on a real device is undiagnosable.
 */
class HermesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ChatNotificationManager.ensureChannel(this)
        try { java.io.File(filesDir, "app_boot.marker").writeText("app onCreate @ ${System.currentTimeMillis()}") } catch (_: Exception) {}
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val isNotifCrash = throwable::class.java.name.contains("RemoteServiceException")
                    || (throwable.message?.contains("Bad notification", ignoreCase = true) == true)
            try {
                val f = File(filesDir, "crash.log")
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val sb = StringBuilder()
                sb.append("CRASH $stamp on thread ${thread.name} ${if (isNotifCrash) "[NOTIF-SWALLOWED]" else ""}\n")
                sb.append("${throwable::class.java.name}: ${throwable.message}\n")
                throwable.stackTrace.forEach { sb.append("    at $it\n") }
                var c = throwable.cause
                while (c != null) {
                    sb.append("Caused by: ${c::class.java.name}: ${c.message}\n")
                    c.stackTrace.forEach { sb.append("    at $it\n") }
                    c = c.cause
                }
                val lines = if (f.exists()) f.readLines() else emptyList()
                f.writeText((lines + sb.toString()).takeLast(200).joinToString("\n"))
            } catch (_: Exception) { /* nothing else we can do */ }
            // A "Bad notification" / RemoteServiceException is a system notification-validation
            // crash, NOT a real app failure — rethrowing it kills the process for no reason.
            // Swallow it so the foreground service keeps running. Everything else goes to default.
            if (!isNotifCrash) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
}
