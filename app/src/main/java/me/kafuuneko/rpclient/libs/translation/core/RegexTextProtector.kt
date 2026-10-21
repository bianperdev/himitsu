// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.core

import me.kafuuneko.rpclient.libs.translation.ProtectedText
import me.kafuuneko.rpclient.libs.translation.TextProtector

/**
 * Shields the parts of a role-play message that a translation service must not touch, using
 * regular expressions.
 *
 * Protected spans:
 * - fenced code blocks and inline code spans (with their backticks)
 * - `{{macros}}`, URLs and `<...>` tags
 * - the character's and the user's names (whole words; CJK names match anywhere)
 * - markdown markers: runs of `*` (bold, italics and the markers around `*actions*`), `_` at
 *   word edges, `~~`, `#` headings at line start
 * - straight and curly double quotes, which services like to rewrite
 * - every other `<`, `>` (block quotes included) and `&`
 *
 * Token format, which the provider clients rely on: each protected span, after merging the
 * spans that touch each other, becomes one self-closing XML tag `<x id="N"/>`, with `N` counting
 * from 1 in order of appearance. Because every `<`, `>` and `&` of the input is protected, the
 * protected text contains those characters only inside tokens, so it is valid XML/HTML text as
 * is:
 * - DeepL: send with `tag_handling=xml` (optionally `ignore_tags=x`).
 * - Google Cloud and LibreTranslate: send with `format=html`, and decode HTML entities of the
 *   response (`&#39;`, `&quot;`, `&amp;`) before calling [restore].
 * - LLM translators: instruct the model to copy every `<x id="N"/>` tag unchanged.
 *
 * [restore] accepts the variants services produce (`<x id="1" />`, single quotes, `<x id=1>`,
 * `<x id="1"></x>`) and returns null when a token is missing, duplicated or unknown.
 */
class RegexTextProtector : TextProtector {

    /**
     * Replaces the protected spans of [text] with tokens.
     *
     * @param names the character's and the user's names; blank ones are ignored.
     */
    override fun protect(text: String, names: List<String>): ProtectedText {
        if (text.isEmpty()) return ProtectedText(text, emptyMap())
        // Collect the spans by priority: a span overlapping an earlier one is dropped
        val taken = BooleanArray(text.length)
        val spans = mutableListOf<IntRange>()
        for (pattern in patternsFor(names)) {
            for (match in pattern.findAll(text)) {
                val range = match.range
                if (range.isEmpty() || range.any { taken[it] }) continue
                range.forEach { taken[it] = true }
                spans += range
            }
        }
        if (spans.isEmpty()) return ProtectedText(text, emptyMap())
        // Merge spans that touch, so "**" next to a quote becomes one token
        val merged = mutableListOf<IntRange>()
        for (span in spans.sortedBy { it.first }) {
            val last = merged.lastOrNull()
            if (last != null && last.last + 1 == span.first) {
                merged[merged.lastIndex] = last.first..span.last
            } else {
                merged += span
            }
        }
        // Rebuild the text with numbered tokens
        val tokens = LinkedHashMap<String, String>()
        val out = StringBuilder(text.length + merged.size * 8)
        var cursor = 0
        merged.forEachIndexed { index, span ->
            out.append(text, cursor, span.first)
            val token = tokenOf(index + 1)
            tokens[token] = text.substring(span)
            out.append(token)
            cursor = span.last + 1
        }
        out.append(text, cursor, text.length)
        return ProtectedText(out.toString(), tokens)
    }

    /**
     * Puts the protected spans back into [translated].
     *
     * @return the restored text, or null when a token is missing, duplicated or unknown.
     */
    override fun restore(translated: String, protected: ProtectedText): String? {
        val seen = HashSet<String>()
        val out = StringBuilder(translated.length)
        var cursor = 0
        for (match in TOKEN_PATTERN.findAll(translated)) {
            val token = tokenOf(match.groupValues[1].toIntOrNull() ?: return null)
            val original = protected.tokens[token] ?: return null
            if (!seen.add(token)) return null
            out.append(translated, cursor, match.range.first)
            out.append(original)
            cursor = match.range.last + 1
        }
        if (seen.size != protected.tokens.size) return null
        out.append(translated, cursor, translated.length)
        return out.toString()
    }

    /** The patterns in priority order, names after code, macros, URLs and tags. */
    private fun patternsFor(names: List<String>): List<Regex> =
        listOf(FENCED_CODE, INLINE_CODE, MACRO, URL, TAG) + namePatterns(names) + MARKERS

    /** One pattern per name, longest first so "Anna Lee" wins over "Anna". */
    private fun namePatterns(names: List<String>): List<Regex> =
        names.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sortedByDescending { it.length }
            .map { name ->
                // Word edges only for scripts that separate words with spaces
                val before = if (needsWordEdge(name.first())) "(?<![\\p{L}\\p{N}_])" else ""
                val after = if (needsWordEdge(name.last())) "(?![\\p{L}\\p{N}_])" else ""
                Regex(before + Regex.escape(name) + after)
            }

    private fun needsWordEdge(char: Char): Boolean {
        val script = runCatching { Character.UnicodeScript.of(char.code) }.getOrNull()
        return script !in NO_SPACE_SCRIPTS
    }

    private companion object {
        val FENCED_CODE = Regex("```[\\s\\S]*?```")
        val INLINE_CODE = Regex("`[^`\\n]+`")
        val MACRO = Regex("\\{\\{[\\s\\S]*?\\}\\}")
        val URL = Regex("(?i)\\b(?:https?|ftp)://[^\\s<>\"'`]+[^\\s<>\"'`.,;:!?)\\]]")
        val TAG = Regex("<[^<>\\n]+>")
        val MARKERS = listOf(
            Regex("\\*+"),
            Regex("(?<![\\p{L}\\p{N}])_+|_+(?![\\p{L}\\p{N}])"),
            Regex("~~"),
            Regex("(?m)^[ \\t]*#{1,6}(?=[ \\t])"),
            Regex("[\"\u201C\u201D]"),
            Regex("[<>&`]")
        )

        /** A token as [restore] reads it, tolerating the forms services return. */
        val TOKEN_PATTERN = Regex("<x\\s+id\\s*=\\s*[\"']?(\\d+)[\"']?\\s*/?>(?:\\s*</x>)?")

        /** Scripts written without spaces between words, where names need no word edges. */
        val NO_SPACE_SCRIPTS = setOf(
            Character.UnicodeScript.HAN,
            Character.UnicodeScript.HIRAGANA,
            Character.UnicodeScript.KATAKANA,
            Character.UnicodeScript.HANGUL,
            Character.UnicodeScript.THAI
        )

        fun tokenOf(id: Int): String = "<x id=\"$id\"/>"
    }
}
