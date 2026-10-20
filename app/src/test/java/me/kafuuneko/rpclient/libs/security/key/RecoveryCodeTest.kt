// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security.key

import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeTest {
    @Test
    fun displayedCodeParsesBackToTheSameKeyMaterial() {
        val code = RecoveryCode.generate(SecureRandom())
        val display = code.toDisplayString()

        val parsed = RecoveryCode.parse(display)!!

        assertEquals(7, display.split('-').size)
        assertTrue(parsed.keyMaterial().contentEquals(code.keyMaterial()))
    }

    @Test
    fun typingVariationsAreAccepted() {
        val canonical = RecoveryCode.parse("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV")!!

        val variants = listOf(
            " 0123 4567 89ab cdef ghjk mnpq rstv ",
            "O123-4567-89AB-CDEF-GHJK-MNPQ-RSTV",
            "0I23-4567-89AB-CDEF-GHJK-MNPQ-RSTV",
            "0l23-4567-89AB-CDEF-GHJK-MNPQ-RSTV"
        )

        variants.forEach { variant ->
            assertTrue(RecoveryCode.parse(variant)!!.keyMaterial().contentEquals(canonical.keyMaterial()))
        }
    }

    @Test
    fun wrongLengthOrSymbolsAreRejected() {
        assertNull(RecoveryCode.parse("0123-4567"))
        assertNull(RecoveryCode.parse("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-W"))
        assertNull(RecoveryCode.parse("U123-4567-89AB-CDEF-GHJK-MNPQ-RSTV"))
        assertNull(RecoveryCode.parse("0123-4567-89AB-CDEF-GHJK-MNPQ-RST!"))
    }

    @Test
    fun toStringNeverRevealsTheCode() {
        val code = RecoveryCode.parse("0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV")!!

        assertFalse(code.toString().contains("0123"))
    }
}
