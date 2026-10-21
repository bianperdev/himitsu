// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexTextProtectorTest {
    private val protector = RegexTextProtector()

    private fun roundTrip(text: String, names: List<String> = emptyList()) {
        val protected = protector.protect(text, names)
        assertEquals(text, protector.restore(protected.text, protected))
    }

    @Test
    fun roundTripsPlainAndMarkedUpText() {
        roundTrip("")
        roundTrip("Hello there, how are you?")
        roundTrip("*Alice smiles* \"Hi, {{user}}!\" **bold** _it_ ~~gone~~", listOf("Alice", "Bob"))
        roundTrip("# Title\n> quoted line\nSee https://example.org/a_b?c=1. Tom & Jerry <b>x</b>")
        roundTrip("Code `val a_b = 1` and\n```\nfun x() = \"y\"\n```\nend")
        roundTrip("アリスは笑った。“こんにちは”", listOf("アリス"))
    }

    @Test
    fun protectsNamesMacrosMarkersAndUrls() {
        val text = "*Alice waves at {{user}}* \"Look: https://example.org\" <i>ok</i> # not a heading"
        val protected = protector.protect(text, listOf("Alice"))
        val values = protected.tokens.values.joinToString("|")
        assertTrue(values.contains("Alice"))
        assertTrue(values.contains("{{user}}"))
        assertTrue(values.contains("https://example.org"))
        assertTrue(values.contains("<i>"))
        // Outside the tokens, only translatable text is left
        val stripped = protected.tokens.keys.fold(protected.text) { acc, key -> acc.replace(key, "") }
        assertFalse(stripped.contains("Alice"))
        assertFalse(stripped.contains("*") || stripped.contains("\""))
        assertFalse(stripped.contains('<') || stripped.contains('>') || stripped.contains('&'))
        // A '#' that is not at line start stays text
        assertTrue(protected.text.contains("# not a heading"))
    }

    @Test
    fun protectsHeadingsQuotesAndAmpersands() {
        val protected = protector.protect("## Day 1\n> She said & left", emptyList())
        assertEquals("<x id=\"1\"/> Day 1\n<x id=\"2\"/> She said <x id=\"3\"/> left", protected.text)
    }

    @Test
    fun usesNumberedSelfClosingTagsAndMergesTouchingSpans() {
        val protected = protector.protect("*\"Hi\"*", emptyList())
        assertEquals("<x id=\"1\"/>Hi<x id=\"2\"/>", protected.text)
        assertEquals(mapOf("<x id=\"1\"/>" to "*\"", "<x id=\"2\"/>" to "\"*"), protected.tokens)
    }

    @Test
    fun namesMatchWholeWordsOnly() {
        val protected = protector.protect("Ann met Annabel.", listOf("Ann", " "))
        assertEquals(1, protected.tokens.size)
        assertTrue(protected.text.contains("Annabel"))
    }

    @Test
    fun restoresTranslatedTextAndTolerantTokenForms() {
        val protected = protector.protect("*Alice smiles*", listOf("Alice"))
        assertEquals("<x id=\"1\"/> smiles<x id=\"2\"/>", protected.text)
        assertEquals("*Alice sonríe*", protector.restore("<x id=\"1\"/> sonríe<x id=\"2\"/>", protected))
        assertEquals("*Alice sonríe*", protector.restore("<x id='1' /> sonríe<x id=2></x>", protected))
    }

    @Test
    fun restoreFailsWhenATokenIsLostDuplicatedOrUnknown() {
        val protected = protector.protect("*Alice smiles*", listOf("Alice"))
        assertNull(protector.restore("<x id=\"1\"/> sonríe", protected))
        assertNull(protector.restore("<x id=\"1\"/> sonríe<x id=\"2\"/><x id=\"2\"/>", protected))
        assertNull(protector.restore("<x id=\"1\"/> sonríe<x id=\"2\"/><x id=\"3\"/>", protected))
        val plain = protector.protect("Hello", emptyList())
        assertNull(protector.restore("Hola <x id=\"1\"/>", plain))
        assertEquals("Hola", protector.restore("Hola", plain))
    }
}
