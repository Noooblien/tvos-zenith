package org.zenith.tvos.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.zenith.tvos.core.security.ParentalLockEngine
import org.zenith.tvos.core.security.ParentalLockStorage

class ParentalLockEngineTest {

    private class InMemoryParentalLockStorage : ParentalLockStorage {
        var savedHash: String? = null
        var savedPackages: Set<String>? = null

        override fun savePinHash(hash: String) {
            savedHash = hash
        }

        override fun loadPinHash(): String? = savedHash

        override fun saveLockedPackages(packageNames: Set<String>) {
            savedPackages = packageNames.toSet()
        }

        override fun loadLockedPackages(): Set<String>? = savedPackages
    }

    @Test
    fun testDefaultPinVerificationAndSaltedHashing() {
        val storage = InMemoryParentalLockStorage()
        val engine = ParentalLockEngine(storage)

        // Plaintext PIN is never stored
        assertNotEquals("0000", storage.savedHash)
        assertEquals(64, storage.savedHash?.length) // 256-bit hex digest

        assertTrue(engine.verifyPin("0000"))
        assertFalse(engine.verifyPin("1234"))
    }

    @Test
    fun testLockAndUnlockPackagesPersistence() {
        val storage = InMemoryParentalLockStorage()
        val engine = ParentalLockEngine(storage)

        assertFalse(engine.isPackageLocked("com.netflix.ninja"))
        assertTrue(engine.lockPackage("com.netflix.ninja"))
        assertTrue(engine.isPackageLocked("com.netflix.ninja"))
        assertEquals(1, engine.getLockedCount())

        // Duplicate lock returns false
        assertFalse(engine.lockPackage("com.netflix.ninja"))

        // Reload from storage into a fresh instance
        val reloaded = ParentalLockEngine(storage)
        assertTrue(reloaded.isPackageLocked("com.netflix.ninja"))

        // Unlock
        assertTrue(reloaded.unlockPackage("com.netflix.ninja"))
        assertFalse(reloaded.isPackageLocked("com.netflix.ninja"))
        assertEquals(0, reloaded.getLockedCount())
    }

    @Test
    fun testUpdatePinRequiresValidCurrentAndNewFourDigitFormat() {
        val storage = InMemoryParentalLockStorage()
        val engine = ParentalLockEngine(storage)

        // Reject invalid formats
        assertFalse(engine.updatePin("0000", "12a4"))
        assertFalse(engine.updatePin("0000", "123"))
        assertFalse(engine.updatePin("0000", "12345"))

        // Reject wrong current PIN
        assertFalse(engine.updatePin("9999", "2580"))

        // Accept valid current + new PIN
        assertTrue(engine.updatePin("0000", "2580"))
        assertFalse(engine.verifyPin("0000"))
        assertTrue(engine.verifyPin("2580"))
    }

    @Test
    fun testBruteForceRateLimitingCooldownAndExpiry() {
        var fakeNowMs = 1_000_000L
        val engine = ParentalLockEngine(clockMs = { fakeNowMs })

        // 3 consecutive wrong attempts trigger 15-second cooldown
        assertFalse(engine.verifyPin("1111"))
        assertFalse(engine.isCoolingDown())
        assertFalse(engine.verifyPin("2222"))
        assertFalse(engine.isCoolingDown())
        assertFalse(engine.verifyPin("3333"))

        assertTrue(engine.isCoolingDown())
        assertEquals(15, engine.getRemainingCooldownSeconds())

        // Even the correct PIN is rejected while cooling down
        assertFalse(engine.verifyPin("0000"))

        // Advance clock past 15-second cooldown
        fakeNowMs += 15_001L
        assertFalse(engine.isCoolingDown())
        assertEquals(0, engine.getRemainingCooldownSeconds())

        // Correct PIN now succeeds and resets failedAttempts
        assertTrue(engine.verifyPin("0000"))
        assertEquals(0, engine.failedAttempts)
    }

    @Test
    fun testUnlockAllPackages() {
        val engine = ParentalLockEngine()
        engine.lockPackage("com.netflix.ninja")
        engine.lockPackage("com.google.android.youtube.tv")
        assertEquals(2, engine.getLockedCount())

        val unlockedCount = engine.unlockAllPackages()
        assertEquals(2, unlockedCount)
        assertEquals(0, engine.getLockedCount())
    }
}
