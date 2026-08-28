package com.example.util

import java.text.Normalizer
import java.util.Locale

object BengaliTextNormalizer {

    /**
     * Normalizes Bengali and English text safely for fast indexing and searching.
     * Preserves semantic meaning while removing diacritic variances, zero-width characters,
     * punctuation inconsistencies, and excessive whitespace.
     */
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""

        // 1. Unicode NFC Normalization
        var text = Normalizer.normalize(input, Normalizer.Form.NFC)

        // 2. Remove Zero-Width Non-Joiner (ZWNJ \u200C) and Zero-Width Joiner (ZWJ \u200D)
        text = text.replace("\u200C", "").replace("\u200D", "")

        // 3. Normalize Bengali Dari (।) and Double Dari (॥) to spaces
        text = text.replace("\u0964", " ").replace("\u0965", " ")

        // 4. Normalize common Bengali punctuation and quotes
        text = text.replace("’", "'").replace("‘", "'")
            .replace("“", "\"").replace("”", "\"")
            .replace("—", " ").replace("–", " ")

        // 5. English lowercase normalization
        text = text.lowercase(Locale.ROOT)

        // 6. Clean extra spaces
        return text.trim().replace(Regex("\\s+"), " ")
    }

    /**
     * Prepares a sanitized FTS query string with prefix wildcard support.
     */
    fun prepareFtsQuery(rawQuery: String): String {
        val clean = normalize(rawQuery)
        val tokens = clean.split(" ").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        // Form: token1* AND token2*
        return tokens.joinToString(" ") { "$it*" }
    }
}
