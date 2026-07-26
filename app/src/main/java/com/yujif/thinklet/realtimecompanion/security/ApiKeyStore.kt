package com.yujif.thinklet.realtimecompanion.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Stores the experimental BYOK OpenAI API key as AES-GCM ciphertext in private preferences.
 * The AES key is non-exportable and owned by Android Keystore, so a copied preference file is
 * not sufficient to recover the API key.
 */
class ApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
    private val cipher = runCatching { ApiKeyCipher(loadOrCreateKey()) }.getOrNull()

    fun get(): String {
        val encrypted = prefs.getString(PREF_API_KEY, null) ?: return ""
        return cipher?.decrypt(encrypted).orEmpty()
    }

    fun set(apiKey: String) {
        if (apiKey.isBlank()) {
            clear()
            return
        }
        val encrypted = runCatching { cipher?.encrypt(apiKey) }.getOrNull() ?: run {
            // A restored or invalidated Keystore key must never crash the activity or leave a
            // stale ciphertext that could be mistaken for a usable credential.
            clear()
            return
        }
        prefs.edit().putString(PREF_API_KEY, encrypted).apply()
    }

    fun clear() {
        prefs.edit().remove(PREF_API_KEY).apply()
    }

    /**
     * One-time migration from this app's earlier plaintext `Activity.getPreferences()` storage.
     * Copies any key found there into the Keystore-protected store, then wipes the plaintext
     * copy so it does not linger on disk.
     */
    fun migrateFromLegacyPlaintext(legacyPrefs: SharedPreferences) {
        val legacyKey = legacyPrefs.getString(LEGACY_PREF_API_KEY, null) ?: return
        if (get().isBlank() && legacyKey.isNotBlank()) {
            set(legacyKey)
        }
        legacyPrefs.edit().remove(LEGACY_PREF_API_KEY).apply()
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREF_FILE_NAME = "api_key_prefs"
        const val PREF_API_KEY = "openai_api_key"
        const val LEGACY_PREF_API_KEY = "openai_api_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "thinklet_realtime_companion_api_key"
    }
}
