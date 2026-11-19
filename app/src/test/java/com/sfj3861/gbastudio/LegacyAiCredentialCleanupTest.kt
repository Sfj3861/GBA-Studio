package com.sfj3861.gbastudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyAiCredentialCleanupTest {
    @Test
    fun clearsBothLegacyCredentialStores() {
        var preferenceCleared = false
        var keyAliasDeleted = false

        val result = LegacyAiCredentialCleanup.clearStores(
            clearPreference = {
                preferenceCleared = true
                true
            },
            deleteKeyAlias = { keyAliasDeleted = true }
        )

        assertTrue(result)
        assertTrue(preferenceCleared)
        assertTrue(keyAliasDeleted)
    }

    @Test
    fun stillAttemptsKeyDeletionWhenPreferenceCleanupFails() {
        var keyAliasDeleted = false

        val result = LegacyAiCredentialCleanup.clearStores(
            clearPreference = { false },
            deleteKeyAlias = { keyAliasDeleted = true }
        )

        assertFalse(result)
        assertTrue(keyAliasDeleted)
    }

    @Test
    fun reportsKeystoreCleanupFailureWithoutThrowing() {
        var preferenceCleared = false

        val result = LegacyAiCredentialCleanup.clearStores(
            clearPreference = {
                preferenceCleared = true
                true
            },
            deleteKeyAlias = { error("Keystore unavailable") }
        )

        assertFalse(result)
        assertTrue(preferenceCleared)
    }
}
