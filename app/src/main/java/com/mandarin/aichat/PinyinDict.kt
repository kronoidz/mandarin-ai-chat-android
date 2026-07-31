package com.mandarin.aichat

import android.content.Context

/**
 * Singleton that loads the compact pinyin dictionary from assets on first use and provides O(1)
 * codepoint-to-pinyin lookups.
 *
 * Dictionary is generated from the Unihan database (kMandarin field) by
 * [tools/generate_pinyin_dict.py].
 */
object PinyinDict {

    private val map = HashMap<Int, String>()
    @Volatile private var loaded = false

    /** Must be called once before any [get] calls, e.g. in Application.onCreate(). */
    fun init(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return

            val bytes = context.assets.open("pinyin_dict.bin").use { it.readBytes() }
            var offset = 0
            while (offset + 5 <= bytes.size) {
                val cp =
                        bytes[offset].toInt() and
                                0xFF or
                                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
                offset += 4
                val len = bytes[offset].toInt() and 0xFF
                offset += 1
                val pinyin = String(bytes, offset, len, Charsets.UTF_8)
                offset += len
                map[cp] = pinyin
            }
            loaded = true
        }
    }

    /** Returns the pinyin reading for [codepoint], or null if not found. */
    fun get(codepoint: Int): String? = map[codepoint]

    /** Whether [init] has completed successfully. */
    fun isLoaded(): Boolean = loaded

    /** Useful for CJK range checks — avoids map lookups outside this range. */
    val cjkRange: IntRange = 0x3400..0x9FFF
}
