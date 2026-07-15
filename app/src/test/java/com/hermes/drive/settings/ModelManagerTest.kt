package com.hermes.drive.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Exercises ModelManager.downloadToFile without real network by injecting a fake connection.
 * Verifies: a 200 with bytes succeeds; a 401 (license-gating) fails with a clear message;
 * an empty 200 body fails loudly instead of writing a 0-byte model.
 */
class ModelManagerTest {

    private fun fakeConnection(code: Int, body: ByteArray = ByteArray(0), length: Int = body.size): HttpURLConnection {
        return object : HttpURLConnection(URL("https://example.com/x.litertlm")) {
            override fun disconnect() {}
            override fun usingProxy(): Boolean = false
            override fun connect() {}
            override fun getResponseCode(): Int = code
            override fun getInputStream(): InputStream = ByteArrayInputStream(body)
            override fun getContentLength(): Int = length
            override fun getHeaderField(name: String?): String? = null
        }
    }

    private fun tmpTarget(): File =
        File.createTempFile("hermes_model_", ".litertlm").apply { deleteOnExit() }

    @Test
    fun successOn200WithBytes() {
        ModelManager.connectionFactory = { fakeConnection(200, "MODELDATA".toByteArray(), 9) }
        val res = kotlinx.coroutines.runBlocking {
            ModelManager.downloadToFile(tmpTarget(), "https://example.com/x.litertlm")
        }
        assertTrue(res.isSuccess)
        ModelManager.connectionFactory = null
    }

    @Test
    fun failsLoudlyOn401Gated() {
        ModelManager.connectionFactory = { fakeConnection(401, "<html>gated</html>".toByteArray()) }
        val res = kotlinx.coroutines.runBlocking {
            ModelManager.downloadToFile(tmpTarget(), "https://example.com/x.litertlm")
        }
        assertFalse(res.isSuccess)
        assertTrue(res.exceptionOrNull()?.message?.contains("license-gated") == true)
        ModelManager.connectionFactory = null
    }

    @Test
    fun failsLoudlyOnEmptyBody() {
        ModelManager.connectionFactory = { fakeConnection(200, ByteArray(0), 0) }
        val res = kotlinx.coroutines.runBlocking {
            ModelManager.downloadToFile(tmpTarget(), "https://example.com/x.litertlm")
        }
        assertFalse(res.isSuccess)
        assertTrue(res.exceptionOrNull()?.message?.contains("empty") == true)
        ModelManager.connectionFactory = null
    }

    @Test
    fun defaultUrlPointsAtUngatedQwen() {
        // Default (fast) is the small Qwen3-0.6B; quality is Qwen2.5-1.5B.
        assertTrue(SettingsStore.DEFAULT_MODEL_URL.contains("Qwen3-0.6B"))
        assertTrue(SettingsStore.DEFAULT_MODEL_URL.endsWith(".litertlm?download=true"))
        assertTrue(SettingsStore.urlForSize(SettingsStore.MODEL_QUALITY).contains("Qwen2.5-1.5B-Instruct"))
        assertTrue(SettingsStore.urlForSize(SettingsStore.MODEL_FAST).contains("Qwen3-0.6B"))
    }
}
