package org.zenith.tvos.core

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.security.IntentSanitizer

class IntentSanitizerTest {

    @Test
    fun testValidPackageNames() {
        assertTrue(IntentSanitizer.isValidPackageName("com.netflix.ninja"))
        assertTrue(IntentSanitizer.isValidPackageName("org.zenith.tvos"))
        assertTrue(IntentSanitizer.isValidPackageName("com.google.android.youtube.tv"))
        assertTrue(IntentSanitizer.isValidPackageName("a.b.c"))
    }

    @Test
    fun testInvalidPackageNamesRejected() {
        assertFalse(IntentSanitizer.isValidPackageName(""))
        assertFalse(IntentSanitizer.isValidPackageName("com.evil;rm -rf /"))
        assertFalse(IntentSanitizer.isValidPackageName("../../../system/bin/sh"))
        assertFalse(IntentSanitizer.isValidPackageName("com.foo..bar"))
        assertFalse(IntentSanitizer.isValidPackageName("123invalid.package"))
    }

    @Test
    fun testValidClassNames() {
        assertTrue(IntentSanitizer.isValidClassName("com.netflix.ninja.MainActivity"))
        assertTrue(IntentSanitizer.isValidClassName("org.zenith.tvos.ui.MainActivity"))
    }

    @Test
    fun testInvalidClassNamesRejected() {
        assertFalse(IntentSanitizer.isValidClassName(""))
        assertFalse(IntentSanitizer.isValidClassName("com.evil.Class$1;DROP TABLE"))
        assertFalse(IntentSanitizer.isValidClassName("/bin/sh"))
    }

    @Test
    fun testSanitizeFlagsStripsGrantUriPermissionsAndClearsClipDataAndSelector() {
        var currentFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

        val intent = mockk<Intent>(relaxed = true)
        every { intent.flags } answers { currentFlags }
        every { intent.setFlags(any()) } answers {
            currentFlags = firstArg()
            intent
        }

        IntentSanitizer.sanitizeFlags(intent)

        assertTrue((currentFlags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue((currentFlags and Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED) != 0)
        assertTrue((currentFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0)
        assertTrue((currentFlags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0)
        assertTrue((currentFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0)
        verify { intent.clipData = null }
        verify { intent.selector = null }

        // Pure bitwise test
        val bitwiseSanitized = IntentSanitizer.sanitizeFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        assertTrue((bitwiseSanitized and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue((bitwiseSanitized and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0)
        assertTrue((bitwiseSanitized and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0)
    }

    @Test
    fun testSanitizeSearchQueryStripsControlAndBidiCharsAndCapsLength() {
        assertEquals("", IntentSanitizer.sanitizeSearchQuery(null))
        assertEquals("", IntentSanitizer.sanitizeSearchQuery("   \u0000\u0007\u202E  "))
        assertEquals("Netflix 4K", IntentSanitizer.sanitizeSearchQuery("  Netflix\u0000\u200B   4K\n\r "))

        val oversized = "A".repeat(120)
        val capped = IntentSanitizer.sanitizeSearchQuery(oversized)
        assertEquals(IntentSanitizer.MAX_SEARCH_QUERY_LENGTH, capped.length)
    }

    @Test
    fun testPrivateOrLoopbackIpv4Verification() {
        assertTrue(IntentSanitizer.isPrivateOrLoopbackIpv4("127.0.0.1"))
        assertTrue(IntentSanitizer.isPrivateOrLoopbackIpv4("10.0.0.42"))
        assertTrue(IntentSanitizer.isPrivateOrLoopbackIpv4("172.16.0.1"))
        assertTrue(IntentSanitizer.isPrivateOrLoopbackIpv4("172.31.255.254"))
        assertTrue(IntentSanitizer.isPrivateOrLoopbackIpv4("192.168.1.105"))
        assertTrue(IntentSanitizer.isPrivateOrLoopbackIpv4("169.254.10.20"))
        assertTrue(IntentSanitizer.isPrivateOrLoopbackIpv4("::1"))

        // Public WAN IPs and malformed addresses must be rejected
        assertFalse(IntentSanitizer.isPrivateOrLoopbackIpv4("8.8.8.8"))
        assertFalse(IntentSanitizer.isPrivateOrLoopbackIpv4("172.32.0.1"))
        assertFalse(IntentSanitizer.isPrivateOrLoopbackIpv4("192.169.1.1"))
        assertFalse(IntentSanitizer.isPrivateOrLoopbackIpv4("invalid.ip"))
        assertFalse(IntentSanitizer.isPrivateOrLoopbackIpv4(""))
    }
}
