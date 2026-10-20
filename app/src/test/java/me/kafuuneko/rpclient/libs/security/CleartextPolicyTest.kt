// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextPolicyTest {
    @Test
    fun plainHttpToAnotherHostIsReported() {
        assertTrue(CleartextPolicy.isUnencryptedRemote("http://192.168.1.20:11434"))
        assertTrue(CleartextPolicy.isUnencryptedRemote(" HTTP://example.org/v1 "))
    }

    @Test
    fun httpsAndLoopbackAreNotReported() {
        assertFalse(CleartextPolicy.isUnencryptedRemote("https://example.org/v1"))
        assertFalse(CleartextPolicy.isUnencryptedRemote("http://localhost:8080"))
        assertFalse(CleartextPolicy.isUnencryptedRemote("http://127.0.0.1:1234/v1"))
        assertFalse(CleartextPolicy.isUnencryptedRemote("http://[::1]:8080"))
    }
}
