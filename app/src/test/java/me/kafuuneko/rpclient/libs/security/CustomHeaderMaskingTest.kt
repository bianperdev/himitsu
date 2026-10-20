// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.security

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses placeholder header values only; no real credential appears in tests. */
class CustomHeaderMaskingTest {
    private val plain = """{"Authorization":"placeholder-secret","X-Title":"placeholder-title"}"""

    @Test
    fun maskKeepsNamesAndHidesEveryValue() {
        val masked = CustomHeaderMasking.mask(plain)

        assertTrue(masked.contains("Authorization"))
        assertTrue(masked.contains("X-Title"))
        assertFalse(masked.contains("placeholder"))
        assertEquals("", CustomHeaderMasking.mask("not json"))
    }

    @Test
    fun unmaskRestoresHiddenValuesKeepsTypedOnesAndDropsOrphans() {
        val edited = """
            {
              "Authorization": "${CustomHeaderMasking.HIDDEN_VALUE}",
              "X-Title": "placeholder-new-title",
              "X-Renamed": "${CustomHeaderMasking.HIDDEN_VALUE}"
            }
        """.trimIndent()

        val merged = JsonParser.parseString(CustomHeaderMasking.unmask(edited, plain)).asJsonObject

        assertEquals("placeholder-secret", merged.get("Authorization").asString)
        assertEquals("placeholder-new-title", merged.get("X-Title").asString)
        assertFalse(merged.has("X-Renamed"))
    }

    @Test
    fun invalidEditedJsonIsReturnedForTheCallerToReject() {
        assertEquals("{broken", CustomHeaderMasking.unmask("{broken", plain))
    }
}
