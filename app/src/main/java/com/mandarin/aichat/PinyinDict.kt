package com.mandarin.aichat

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * Singleton that wraps [PinyinHelper] from the pinyin4j library to convert Hanzi codepoints to
 * pinyin readings.
 *
 * Since many CJK characters have multiple valid pinyin readings (depending on context), [get]
 * returns only the first (most common) reading while [getAll] returns every known reading. Results
 * are cached in-memory after first lookup.
 */
object PinyinDict {

    private val outputFormat =
            HanyuPinyinOutputFormat().apply {
                toneType = HanyuPinyinToneType.WITH_TONE_MARK
                vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
            }

    /** Cache: codepoint → array of pinyin readings. */
    private val cache = HashMap<Int, Array<String>>()

    /**
     * Returns the first (most common) pinyin reading for [codepoint], or null if the character is
     * not a Hanzi character.
     */
    fun get(codepoint: Int): String? = getAll(codepoint).firstOrNull()

    /**
     * Returns all known pinyin readings for [codepoint], or an empty array if the character is not
     * a Hanzi character.
     *
     * Readings are cached after the first lookup.
     */
    fun getAll(codepoint: Int): Array<String> {
        cache[codepoint]?.let {
            return it
        }
        val chars = Character.toChars(codepoint)
        if (chars.size != 1) return emptyArray()
        val result = PinyinHelper.toHanyuPinyinStringArray(chars[0], outputFormat) ?: emptyArray()
        cache[codepoint] = result
        return result
    }

    /** Useful for CJK range checks — avoids map lookups outside this range. */
    val cjkRange: IntRange = 0x3400..0x9FFF
}
