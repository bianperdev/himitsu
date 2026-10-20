// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses obviously fake placeholder secrets only. */
class RequestLogRedactorTest {
    private val fakeKey = "placeholder-key-0000"

    @Test
    fun knownSecretsAreRemovedEvenOutsideJson() {
        val stream = "data: {\"echo\":\"$fakeKey\"}\n\ndata: [DONE]"

        val redacted = RequestLogRedactor.redact(stream, listOf(fakeKey))

        assertFalse(redacted.contains(fakeKey))
        assertTrue(redacted.contains(RequestLogRedactor.REDACTED))
    }

    @Test
    fun credentialFieldsAreHiddenAtAnyDepthAndOtherFieldsKept() {
        val json = """{"model":"m","headers":{"Authorization":"Bearer placeholder"},"items":[{"api_key":"placeholder"}]}"""

        val redacted = RequestLogRedactor.redact(json, emptyList())

        assertFalse(redacted.contains("placeholder"))
        assertTrue(redacted.contains("\"model\":\"m\""))
    }

    @Test
    fun shortSecretsAreNotReplacedVerbatim() {
        assertEquals("a b c", RequestLogRedactor.redact("a b c", listOf("b")))
    }

    @Test
    fun providerSecretsIncludeCustomHeaderValues() {
        val secrets = RequestLogRedactor.providerSecrets(fakeKey, """{"X-Title":"placeholder-header-value"}""")

        assertEquals(listOf(fakeKey, "placeholder-header-value"), secrets)
    }
}
