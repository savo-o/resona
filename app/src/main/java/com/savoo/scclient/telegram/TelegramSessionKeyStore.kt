package com.savoo.scclient.telegram

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the random key TDLib uses to encrypt its own local session database
 * (`TdlibParameters.databaseEncryptionKey`). The key itself lives in Keystore-backed
 * EncryptedSharedPreferences (same pattern as [com.savoo.scclient.auth.TokenStore]) so it survives
 * app restarts without ever being written out in the clear.
 */
@Singleton
class TelegramSessionKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "telegram_import_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Returns the existing key, generating and persisting a fresh 32-byte one on first use. */
    fun getOrCreateDatabaseEncryptionKey(): ByteArray {
        prefs.getString(KEY_DB_ENC, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_DB_ENC, Base64.encodeToString(key, Base64.NO_WRAP)).apply()
        return key
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_DB_ENC = "db_encryption_key"
    }
}
