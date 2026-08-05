package com.mandarin.aichat

import java.io.File
import java.security.MessageDigest

/**
 * Simple disk cache for TTS audio files, keyed by an MD5 hash of the input text.
 *
 * Files are stored in [cacheDir]/tts_cache/ and survive app restarts as long as the system doesn't
 * clear the cache.
 */
object TtsAudioCache {

    private const val CACHE_SUBDIR = "tts_cache"

    private var cacheDir: File? = null

    /** Must be called once before use, typically from [App.onCreate]. */
    fun init(app: App) {
        cacheDir = File(app.cacheDir, CACHE_SUBDIR).also { it.mkdirs() }
    }

    /** Returns cached audio bytes for [text], or null if not cached. */
    fun get(text: String): ByteArray? {
        val file = cacheFile(text) ?: return null
        return if (file.exists()) file.readBytes() else null
    }

    /** Stores [audioBytes] in the cache for [text]. */
    fun put(text: String, audioBytes: ByteArray) {
        val file = cacheFile(text) ?: return
        file.writeBytes(audioBytes)
    }

    private fun cacheFile(text: String): File? {
        val dir = cacheDir ?: return null
        val hash = md5(text)
        return File(dir, "$hash.mp3")
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
