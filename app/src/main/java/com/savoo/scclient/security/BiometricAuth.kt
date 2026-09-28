package com.savoo.scclient.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

fun canUseBiometric(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

sealed interface BiometricKeyState {
    class Ready(val cipher: Cipher) : BiometricKeyState
    data object Invalidated : BiometricKeyState
    data object Unavailable : BiometricKeyState
}

object BiometricKey {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "app_lock_biometric"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun create(): Boolean = runCatching {
        delete()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                }
            }
            .build()
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(spec)
            generateKey()
        }
    }.isSuccess

    fun delete() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    fun state(): BiometricKeyState = try {
        val key = keyStore().getKey(ALIAS, null) as? SecretKey
        if (key == null) {
            BiometricKeyState.Unavailable
        } else {
            BiometricKeyState.Ready(Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) })
        }
    } catch (e: KeyPermanentlyInvalidatedException) {
        BiometricKeyState.Invalidated
    } catch (e: Exception) {
        BiometricKeyState.Unavailable
    }

    private fun keyStore() = KeyStore.getInstance(KEYSTORE).apply { load(null) }
}

fun FragmentActivity.promptBiometric(
    title: String,
    negativeText: String,
    cipher: Cipher,
    onResult: (Boolean) -> Unit,
) {
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val verified = runCatching {
                    result.cryptoObject?.cipher?.doFinal(ByteArray(16)) != null
                }.getOrDefault(false)
                onResult(verified)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false)
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .setNegativeButtonText(negativeText)
        .build()
    runCatching { prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher)) }
        .onFailure { onResult(false) }
}
