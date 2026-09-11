package com.savoo.scclient.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.savoo.scclient.debug.DebugLog
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

private const val TAG = "SecurePrefs"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

fun createSecurePrefs(context: Context, fileName: String): SharedPreferences =
    try {
        buildSecurePrefs(context, fileName)
    } catch (e: GeneralSecurityException) {
        recoverSecurePrefs(context, fileName, e)
    } catch (e: IOException) {
        recoverSecurePrefs(context, fileName, e)
    }

private fun buildSecurePrefs(context: Context, fileName: String): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        fileName,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
}

private fun recoverSecurePrefs(
    context: Context,
    fileName: String,
    cause: Exception,
): SharedPreferences {
    DebugLog.log(TAG, "$fileName is unreadable (${cause::class.simpleName}), resetting it")
    context.deleteSharedPreferences(fileName)
    runCatching {
        KeyStore.getInstance(ANDROID_KEYSTORE)
            .apply { load(null) }
            .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
    }.onFailure { DebugLog.log(TAG, "could not drop the master key: $it") }
    return buildSecurePrefs(context, fileName)
}
