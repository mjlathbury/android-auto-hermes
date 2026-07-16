package com.hermes.drive

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.hermes.drive.llm.LiteRtEngine
import com.hermes.drive.session.ChatSession
import com.hermes.drive.settings.ModelManager
import com.hermes.drive.settings.SettingsStore
import com.hermes.drive.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the on-device LLM alive for the duration of a drive and turns
 * each reply from the driver into a streamed Hermes answer posted back as a MessagingStyle
 * notification (which Android Auto reads aloud).
 */
class DriveAssistantService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var settingsStore: SettingsStore
    private var engine: LiteRtEngine? = null
    private val session = ChatSession()
    private var loading = false
    /** Set true only when WE initiate the stop (Stop button / car disconnect), so onDestroy can
     *  distinguish a user stop from an OS/system kill. */
    private var userStopped = false

    override fun onCreate() {
        super.onCreate()
        try {
            // Marker so we can confirm onCreate actually ran (independent of DebugLog).
            java.io.File(filesDir, "svc_boot.marker").writeText(
                "onCreate reached @ ${System.currentTimeMillis()}"
            )
            settingsStore = SettingsStore(this)
            ChatNotificationManager.ensureChannel(this)
            DebugLog.event(this, "Service onCreate — build ${BuildConfig.VERSION_NAME} (code ${BuildConfig.BUILD_NUMBER})")
            session.addAssistant(getString(R.string.engine_loading))
            startForeground(
                ChatNotificationManager.NOTIF_ID,
                ChatNotificationManager.buildNotification(this, session.messages, false),
            )
            scope.launch { loadEngine() }
        } catch (t: Throwable) {
            DebugLog.event(this, "Service onCreate FAILED: ${t::class.java.simpleName}: ${t.message}")
            try { stopSelf() } catch (_: Exception) {}
        }
    }

    private suspend fun loadEngine() {
        if (loading) return
        loading = true
        val settings = settingsStore.settings.first()
        if (!ModelManager.modelExists(this, settings.modelSize)) {
            DebugLog.event(this, "Model missing for size=${settings.modelSize}")
            session.clear()
            session.addAssistant(getString(R.string.engine_missing))
            post()
            loading = false
            return
        }
        val path = java.io.File(filesDir, ModelManager.modelFileFor(settings.modelSize)).absolutePath
        DebugLog.event(this, "Model found: $path (${java.io.File(path).length()} bytes)")
        engine = LiteRtEngine(this, path, SYSTEM_PROMPT)
        try {
            engine?.load()
            DebugLog.event(this, "Engine loaded OK")
            session.clear()
            session.addAssistant("Hermes ready. Tap reply and speak.")
        } catch (t: Throwable) {
            DebugLog.event(this, "Engine load FAILED: ${t::class.java.simpleName}: ${t.message}")
            session.clear()
            session.addAssistant("Failed to load model: ${t.message}")
        }
        post()
        loading = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.hermes.drive.ACTION_USER_STOP") {
            userStopped = true
            DebugLog.event(this, "User stop requested")
            stopSelf()
            return START_NOT_STICKY
        }
        val query = intent?.getStringExtra(EXTRA_QUERY)
        DebugLog.event(this, "onStartCommand query=${if (query.isNullOrBlank()) "<none>" else "\"$query\""}")
        if (!query.isNullOrBlank()) scope.launch { handleQuery(query) }
        return START_NOT_STICKY
    }

    private suspend fun handleQuery(query: String) {
        if (engine == null || !engine!!.isReady) {
            DebugLog.event(this, "Query ignored: engine not ready")
            session.addUser(query)
            session.addAssistant("Hermes isn't ready yet. Wait a moment and try again.")
            post()
            return
        }
        DebugLog.event(this, "Query: $query")
        session.addUser(query)
        session.addAssistant("…")
        post()
        val sb = StringBuilder()
        try {
            engine!!.ask(query).collect { chunk ->
                sb.append(chunk)
                session.updateLastAssistant(sb.toString())
                post()
            }
            DebugLog.event(this, "Answer (${sb.length} chars): ${sb.toString().take(120)}")
        } catch (t: Throwable) {
            DebugLog.event(this, "Answer FAILED: ${t::class.java.simpleName}: ${t.message}")
            session.updateLastAssistant("Sorry, something went wrong: ${t.message}")
        }
        val final = sb.toString().trim().ifEmpty { "(no response)" }
        session.updateLastAssistant(final)
        session.prune()
        post()
    }

    private fun post() {
        ChatNotificationManager.post(
            this,
            ChatNotificationManager.buildNotification(this, session.messages, false),
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        DebugLog.event(this, "onTrimMemory level=$level (engineReady=${engine?.isReady == true})")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        DebugLog.event(this, "onLowMemory")
    }

    override fun onDestroy() {
        val job = scope.coroutineContext[kotlinx.coroutines.Job]
        DebugLog.event(this, "onDestroy — userStopped=$userStopped; engineReady=${engine?.isReady == true}; scopeActive=${job?.isActive == true}")
        scope.cancel()
        engine?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_QUERY = "com.hermes.drive.EXTRA_QUERY"
        const val SYSTEM_PROMPT =
            "You are Hermes, a calm voice assistant for a driver. Keep replies to 1-3 short " +
                "spoken sentences. No markdown, no lists, no symbols. Be direct and useful."

        fun enqueueQuery(context: Context, query: String) {
            val intent = Intent(context, DriveAssistantService::class.java).apply {
                putExtra(EXTRA_QUERY, query)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        /** Start the assistant with no query — boots the engine and posts the "ready" notification. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, DriveAssistantService::class.java),
            )
        }

        /** Stop the foreground assistant (frees the model from RAM). */
        fun stop(context: Context) {
            // Mark intent so the service's onDestroy can tell a user stop from a system kill.
            val intent = Intent(context, DriveAssistantService::class.java).apply {
                action = "com.hermes.drive.ACTION_USER_STOP"
            }
            context.stopService(intent)
        }
    }
}
