// SPDX-License-Identifier: GPL-3.0-or-later
package me.kafuuneko.rpclient.libs.disguise

import android.os.Build
import org.junit.Assume.assumeTrue

/**
 * Skips a test outside an emulator. Disguise tests overwrite the stored shortcut and switch the
 * launcher icon; on a real phone they would erase the owner's code or hide their icon.
 */
fun assumeEmulator() {
    assumeTrue("disguise tests run only on an emulator", isEmulator())
}

/** JUnit runs `@After` even when an assumption in `@Before` failed: cleanups check this first. */
fun isEmulator(): Boolean =
    Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.startsWith("generic") || Build.PRODUCT.contains("sdk")
