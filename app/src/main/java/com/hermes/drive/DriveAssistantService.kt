package com.hermes.drive

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.hermes.drive.llm.LiteRtEngine
import com.hermes.drive.RestartReceiver
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
    /** MediaSession makes the OS treat this as a legitimate media/voice component (HyperOS is far
     *  less likely to kill a mediaPlayback foreground service with an active session). */
    private var mediaSession: MediaSession? = null
    /** Partial WakeLock keeps process importance high so the OS won't reclaim the service when the
     *  activity closes. Released in onDestroy. */
    private var wakeLock: android.os.PowerManager.WakeLock? = null
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
            // Establish a media session so the foreground service is seen as user-visible media.
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            val pi = PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            mediaSession = MediaSession(this, "HermesDrive").apply {
                setSessionActivity(pi)
                isActive = true
            }
            // Partial WakeLock raises process importance so HyperOS can't reclaim the FGS when the
            // activity closes (observed: stopService fired 9ms after onCreate without it).
            wakeLock = (getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager)
                .newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "HermesDrive::keepalive")
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire(6L * 60L * 60L * 1000L) // 6h hard cap; released in onDestroy
            session.addAssistant(getString(R.string.engine_loading))
            // Use a minimal, always-valid notification for startForeground(). The rich MessagingStyle
            // notification is posted separately via post() so a malformed style can't trigger
            // CannotPostForegroundServiceNotificationException (which HyperOS turns into a kill).
            try {
                startForeground(
                    ChatNotificationManager.NOTIF_ID,
                    ChatNotificationManager.buildForegroundNotification(this),
                )
            } catch (t: Throwable) {
                DebugLog.event(this, "startForeground FAILED: ${t::class.java.simpleName}: ${t.message}")
                // Fall back to posting without elevating to foreground so the service can still run.
                try {
                    ChatNotificationManager.post(
                        this,
                        ChatNotificationManager.buildForegroundNotification(this),
                    )
                } catch (_: Exception) {}
            }
            // Post the chat-style notification immediately (replaces the minimal one visually).
            try {
                ChatNotificationManager.post(
                    this,
                    ChatNotificationManager.buildNotification(this, session.messages, false),
                )
            } catch (_: Exception) {}
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App's task was swiped away / closed. With stopWithTask=false the service should keep
        // running; this log confirms whether HyperOS honours it. If it still stops, the
        // RestartReceiver self-heal in onDestroy handles transient kills.
        DebugLog.event(this, "onTaskRemoved — keeping service alive (stopWithTask=false)")
        super.onTaskRemoved(rootIntent)
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
        try { mediaSession?.release() } catch (_: Exception) {}
        mediaSession = null
        try { wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        engine?.close()
        // If the OS stopped us (not the user), restart shortly so a transient HyperOS FGS-stop
        // self-heals. With the service in its own :assistant process, app task removal no longer
        // triggers this, so a restart loop won't occur.
        if (!userStopped) {
            DebugLog.event(this, "Scheduling self-restart (OS stop, non-user)")
            try { RestartReceiver().schedule(this) } catch (_: Exception) {}
        }
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

        /** True if any of our activities is currently the foreground/topmost app. */
        fun isAppForeground(context: Context): Boolean {
            return try {
                val am = context.getSystemService(android.app.ActivityManager::class.java)
                val tasks = am.getRunningTasks(1)
                if (tasks.isNullOrEmpty()) return false
                tasks[0].topActivity?.packageName == context.packageName
            } catch (_: Exception) { false }
        }
    }
}
