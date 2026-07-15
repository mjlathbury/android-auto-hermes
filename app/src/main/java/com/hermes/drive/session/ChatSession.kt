package com.hermes.drive.session

/**
 * Pure-Kotlin, Android-free chat history used by the assistant service.
 * Kept free of Android imports so it can be unit-tested on the JVM.
 *
 * A message is a [Pair] of (fromUser, text):
 *   - first == true  -> message from the driver ("You")
 *   - first == false -> message from Hermes
 */
class ChatSession(private val maxMessages: Int = 14) {
    private val _messages = mutableListOf<Pair<Boolean, String>>()

    val messages: List<Pair<Boolean, String>> get() = _messages.toList()

    fun addUser(text: String) {
        _messages.add(true to text)
    }

    fun addAssistant(text: String) {
        _messages.add(false to text)
    }

    fun clear() {
        _messages.clear()
    }

    fun lastAssistantIndex(): Int = _messages.indexOfLast { !it.first }

    /** Replace the most recent Hermes message with streamed [text]; add one if none exists. */
    fun updateLastAssistant(text: String) {
        val i = lastAssistantIndex()
        if (i >= 0) _messages[i] = false to text else addAssistant(text)
    }

    /** Drop oldest messages so the conversation stays within [maxMessages]. */
    fun prune() {
        while (_messages.size > maxMessages) _messages.removeAt(0)
    }
}
