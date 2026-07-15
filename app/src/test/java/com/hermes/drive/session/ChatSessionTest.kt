package com.hermes.drive.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSessionTest {

    @Test
    fun addUserAndAssistant() {
        val s = ChatSession()
        s.addUser("hello")
        s.addAssistant("hi")
        assertEquals(listOf(true to "hello", false to "hi"), s.messages)
    }

    @Test
    fun updateLastAssistantStreamsPartialText() {
        val s = ChatSession()
        s.addUser("q")
        s.addAssistant("…")
        s.updateLastAssistant("The")
        s.updateLastAssistant("The answer")
        assertEquals("The answer", s.messages.last().second)
        assertEquals(false, s.messages.last().first)
    }

    @Test
    fun pruneKeepsOnlyLastNMessages() {
        val s = ChatSession(maxMessages = 4)
        repeat(10) {
            s.addUser("u$it")
            s.addAssistant("a$it")
        }
        s.prune()
        assertTrue(s.messages.size <= 4)
        assertEquals(4, s.messages.size)
        assertEquals("u8", s.messages.first().second)
        assertEquals("a9", s.messages.last().second)
    }

    @Test
    fun clearEmptiesHistory() {
        val s = ChatSession()
        s.addUser("x")
        s.clear()
        assertTrue(s.messages.isEmpty())
    }
}
