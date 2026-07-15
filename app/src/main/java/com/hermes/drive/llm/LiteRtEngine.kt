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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * On-device LLM via Google's LiteRT-LM Kotlin API (GPU backend).
 * One [Conversation] is kept alive for the session so multi-turn context persists.
 *
 * A streamed [Message] carries its text in [Message.getContents]: a list of [Content],
 * where [Content.Text] holds the raw string. We flatten those into the partial-text flow.
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

    override suspend fun load() = withContext(Dispatchers.Default) {
        if (conversation != null) return@withContext
        val cfg = EngineConfig(
            modelPath = modelPath,
            backend = Backend.GPU(),
            cacheDir = context.cacheDir.path,
        )
        val eng = Engine(cfg)
        eng.initialize()
        val conv = eng.createConversation(
            ConversationConfig(systemInstruction = Contents.of(systemInstruction)),
        )
        engine = eng
        conversation = conv
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
    }.flowOn(Dispatchers.Default)

    override fun close() {
        try { conversation?.close() } catch (_: Exception) { /* best effort */ }
        try { engine?.close() } catch (_: Exception) { /* best effort */ }
        conversation = null
        engine = null
    }
}
