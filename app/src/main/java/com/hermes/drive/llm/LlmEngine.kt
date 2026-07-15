package com.hermes.drive.llm

import kotlinx.coroutines.flow.Flow

interface LlmEngine {
    /** True once the model is loaded and ready to answer. */
    val isReady: Boolean

    /** Load the model. May take several seconds — call off the main thread. */
    suspend fun load()

    /** Generate a streamed reply for [userText]; emits partial text chunks as they arrive. */
    fun ask(userText: String): Flow<String>

    /** Free native resources. */
    fun close()
}
