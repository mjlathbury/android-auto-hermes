package com.hermes.drive.llm

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.hermes.drive.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device LLM via Google's LiteRT-LM Kotlin API.
 *
 * Backend strategy (red: the Mali-G57 GPU delegate has been observed to hang during initialization
 * on some devices, never returning and never throwing a Kotlin exception). To stay robust we:
 *   1. Try GPU first (fastest when it works).
 *   2. On any exception, fall back to CPU (always works, just slower first token).
 *   3. Run initialization on a worker thread with a timeout (the "hang watchdog"). If the native
 *      layer stalls, the thread is interrupted/stopped and we surface a clear error instead of
 *      hanging forever with no log line.
 *
 * One [Conversation] is kept alive for the session so multi-turn context persists.
 */
class LiteRtEngine(
    private val context: Context,
    private val modelPath: String,
    private val systemInstruction: String,
) : LlmEngine {

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var conversation: Conversation? = null

    override val isReady: Boolean get() = conversation != null

    /** Seconds to wait for native init before declaring a hang and falling back / failing. */
    private val LOAD_TIMEOUT_SECONDS = 90L

    override suspend fun load() = withContext(Dispatchers.IO) {
        if (conversation != null) return@withContext

        // CPU first: it loads reliably on the Mali-G57 (GPU delegate has been observed to hang
        // during native init on this device, stalling the whole process). CPU is just a touch
        // slower to first token — acceptable for a voice assistant.
        val cpuResult = loadWithBackend(Backend.CPU(), "CPU")
        if (cpuResult == null) {
            DebugLog.event(context, "Engine loaded OK (backend=CPU)")
            return@withContext
        }
        // CPU failed -> try GPU as a fallback (in case the device has a working delegate).
        DebugLog.event(context, "CPU backend failed ($cpuResult) -> trying GPU")
        val gpuResult = loadWithBackend(Backend.GPU(), "GPU")
        if (gpuResult == null) {
            DebugLog.event(context, "Engine loaded OK (backend=GPU)")
            return@withContext
        }
        val msg = "Failed to load model on both CPU and GPU. Last error: $gpuResult"
        DebugLog.event(context, "Engine load FAILED: $msg")
        throw RuntimeException(msg)
    }

    /**
     * Attempts to initialize the engine with [backend]. Runs on a worker thread with a timeout so a
     * native hang doesn't stall forever. Returns null on success, or an error description on
     * failure/hang.
     */
    private fun loadWithBackend(backend: Backend, label: String): String? {
        val executor = Executors.newSingleThreadExecutor()
        val errorRef = AtomicReference<String?>(null)
        try {
            val future = executor.submit {
                try {
                    DebugLog.event(context, "Engine.load($label) starting…")
                    val cfg = EngineConfig(
                        modelPath = modelPath,
                        backend = backend,
                        cacheDir = context.cacheDir.path,
                    )
                    val eng = Engine(cfg)
                    eng.initialize()
                    val conv = eng.createConversation(
                        ConversationConfig(systemInstruction = Contents.of(systemInstruction)),
                    )
                    engine = eng
                    conversation = conv
                    DebugLog.event(context, "Engine.load($label) initialized")
                } catch (t: Throwable) {
                    errorRef.set("${t::class.java.simpleName}: ${t.message}")
                }
            }
            try {
                future.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                future.cancel(true)
                // Best-effort cleanup of a half-initialized native engine.
                try { engine?.close() } catch (_: Exception) {}
                engine = null
                conversation = null
                return "($label) init timed out after ${LOAD_TIMEOUT_SECONDS}s (native hang)"
            }
        } finally {
            executor.shutdownNow()
        }
        return errorRef.get()
    }

    override fun ask(userText: String): Flow<String> = callbackFlow {
        val conv = conversation ?: throw IllegalStateException("Engine not loaded")
        val sb = StringBuilder()
        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                val piece = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString("") { it.text }
                if (piece.isNotEmpty()) {
                    sb.append(piece)
                    trySend(sb.toString())
                }
            }

            override fun onDone() {
                channel.close()
            }

            override fun onError(throwable: Throwable) {
                channel.close(throwable)
            }
        }
        conv.sendMessageAsync(userText, callback)
        awaitClose { /* conversation is reused; no per-call teardown */ }
    }.flowOn(Dispatchers.IO)

    override fun close() {
        try { conversation?.close() } catch (_: Exception) { /* best effort */ }
        try { engine?.close() } catch (_: Exception) { /* best effort */ }
        conversation = null
        engine = null
    }
}
