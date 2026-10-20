// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise.secret

import java.security.SecureRandom
import me.kafuuneko.rpclient.libs.disguise.Appearance
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRecordTest {
    private val random = SecureRandom()

    /** Low iteration count: these tests check the logic, not the cost. */
    private fun record(appearance: Appearance, secret: String) =
        SecretRecord.create(appearance, secret.toCharArray(), random, iterations = 1_000)

    @Test
    fun pbkdf2MatchesTheRfc7914TestVector() {
        val derived = SecretRecord.pbkdf2("passwd".toCharArray(), "salt".toByteArray(), iterations = 1, bits = 512)
        val expected = "55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc49ca9cccf179b645991664b39d77ef317c71b845b1e30bd509112041d3a19783"
        assertEquals(expected, derived.joinToString("") { "%02x".format(it) })
    }

    @Test
    fun onlyTheRightShortcutOfTheRightDecoyMatches() {
        val record = record(Appearance.Calculator, "482913")
        assertTrue(record.matches(Appearance.Calculator, "482913".toCharArray()))
        assertFalse(record.matches(Appearance.Calculator, "482914".toCharArray()))
        assertFalse(record.matches(Appearance.Converter, "482913".toCharArray()))
    }

    @Test
    fun eachRecordHasItsOwnSaltAndNeverStoresTheShortcut() {
        val first = record(Appearance.Calculator, "482913")
        val second = record(Appearance.Calculator, "482913")
        assertFalse(first.salt.contentEquals(second.salt))
        assertFalse(first.hash.contentEquals(second.hash))
        assertFalse(first.toString().contains("482913"))
    }

    @Test
    fun theCallersShortcutIsLeftAsItWas() {
        val secret = "482913".toCharArray()
        SecretRecord.create(Appearance.Calculator, secret, random, iterations = 1_000)
        assertArrayEquals("482913".toCharArray(), secret)
    }
}
