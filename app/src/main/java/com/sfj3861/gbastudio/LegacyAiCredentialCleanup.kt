package com.sfj3861.gbastudio

import android.content.Context
import java.security.KeyStore

/**
 * Removes the obsolete on-device OpenAI credential left by pre-backend builds.
 * The current app uses backend credentials only.
 */
internal object LegacyAiCredentialCleanup {
    private const val LEGACY_PREFERENCES = "gba_studio_ai_secure"
    private const val LEGACY_PREFERENCE_KEY = "openai_key"
    private const val LEGACY_KEYSTORE_ALIAS = "gba_studio_openai_key_v1"

    fun clear(context: Context): Boolean {
        val appContext = context.applicationContext
        return clearStores(
            clearPreference = {
                val preferences = appContext.getSharedPreferences(LEGACY_PREFERENCES, Context.MODE_PRIVATE)
                !preferences.contains(LEGACY_PREFERENCE_KEY) ||
                    preferences.edit().remove(LEGACY_PREFERENCE_KEY).commit()
            },
            deleteKeyAlias = {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (keyStore.containsAlias(LEGACY_KEYSTORE_ALIAS)) {
                    keyStore.deleteEntry(LEGACY_KEYSTORE_ALIAS)
                }
            }
        )
    }

    internal fun clearStores(
        clearPreference: () -> Boolean,
        deleteKeyAlias: () -> Unit
    ): Boolean {
        val preferenceCleared = runCatching(clearPreference).getOrDefault(false)
        val keyAliasCleared = runCatching(deleteKeyAlias).isSuccess
        return preferenceCleared && keyAliasCleared
    }
}
