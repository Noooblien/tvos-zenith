package org.zenith.tvos.core

import android.content.Intent
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
    fun testSanitizeFlagsStripsGrantUriPermissions() {
        val intent = Intent().apply {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }

        IntentSanitizer.sanitizeFlags(intent)

        assertTrue((intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue((intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0)
        assertTrue((intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0)
        assertTrue((intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) == 0)
    }
}
