// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.translation.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlEntityDecodingTest {
    @Test
    fun `named and numeric entities are decoded`() {
        assertEquals("'\"&<>'", decodeHtmlEntities("&#39;&quot;&amp;&lt;&gt;&apos;"))
        assertEquals("caf\u00E9 \uD83D\uDE00", decodeHtmlEntities("caf&#233; &#x1F600;"))
    }

    @Test
    fun `decoding is a single pass`() {
        assertEquals("&lt;", decodeHtmlEntities("&amp;lt;"))
    }

    @Test
    fun `placeholder tags and unknown or invalid entities stay as they are`() {
        val text = "<x id=\"1\"/> a &copy; b &#0; c &#99999999; d & e"
        assertEquals(text, decodeHtmlEntities(text))
    }
}
