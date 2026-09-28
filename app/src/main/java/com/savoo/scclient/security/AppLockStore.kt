package com.savoo.scclient.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.savoo.scclient.data.local.createSecurePrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

enum class AppLockMode(val usesBiometric: Boolean) {
    OFF(false),
    PIN(false),
    BIOMETRIC_OR_PIN(true),
}

sealed interface PinCheckResult {
    data object Correct : PinCheckResult
    data object Duress : PinCheckResult
    data class Wrong(val lockedUntil: Long) : PinCheckResult
    data class LockedOut(val lockedUntil: Long) : PinCheckResult
}

const val MinPinLength = 4
const val MaxPinLength = 8

@Singleton
class AppLockStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy { createSecurePrefs(context, PREFS_FILE) }

    private val _mode = MutableStateFlow(AppLockMode.OFF)
    val mode: StateFlow<AppLockMode> = _mode.asStateFlow()

    private val _duressEnabled = MutableStateFlow(false)
    val duressEnabled: StateFlow<Boolean> = _duressEnabled.asStateFlow()

    @Volatile private var loaded = false
    @Volatile var sessionUnlocked = false
        private set

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            _mode.value = prefs.getString(KEY_MODE, null)
                ?.let { runCatching { AppLockMode.valueOf(it) }.getOrNull() }
                ?.takeIf { prefs.contains(KEY_PIN_HASH) }
                ?: AppLockMode.OFF
            _duressEnabled.value = prefs.contains(KEY_DURESS_HASH)
            if (_mode.value != AppLockMode.OFF && !AppLockGuard.isEnabled(context)) {
                AppLockGuard.setEnabled(context, true)
            }
            loaded = true
        }
    }

    fun load() = ensureLoaded()

    fun needsUnlock(): Boolean {
        ensureLoaded()
        return _mode.value != AppLockMode.OFF && !sessionUnlocked
    }

    fun markUnlocked() {
        sessionUnlocked = true
        prefs.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKED_UNTIL).apply()
    }

    fun hasPin(): Boolean {
        ensureLoaded()
        return prefs.contains(KEY_PIN_HASH)
    }

    fun lockedUntil(): Long =
        prefs.getLong(KEY_LOCKED_UNTIL, 0L).coerceAtMost(System.currentTimeMillis() + MAX_LOCKOUT_MS)

    suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        ensureLoaded()
        val salt = newSalt()
        prefs.edit()
            .putString(KEY_PIN_SALT, salt.encode())
            .putString(KEY_PIN_HASH, hash(pin, salt).encode())
            .commit()
    }

    fun setMode(mode: AppLockMode) {
        ensureLoaded()
        if (mode == AppLockMode.OFF) {
            AppLockGuard.setEnabled(context, false)
            prefs.edit().clear().commit()
            _duressEnabled.value = false
        } else {
            prefs.edit().putString(KEY_MODE, mode.name).commit()
            AppLockGuard.setEnabled(context, true)
        }
        if (!mode.usesBiometric) BiometricKey.delete()
        sessionUnlocked = true
        _mode.value = mode
    }

    suspend fun matchesPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        matches(pin, KEY_PIN_SALT, KEY_PIN_HASH)
    }

    suspend fun matchesDuressPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        matches(pin, KEY_DURESS_SALT, KEY_DURESS_HASH)
    }

    suspend fun setDuressPin(pin: String) = withContext(Dispatchers.Default) {
        val salt = newSalt()
        prefs.edit()
            .putString(KEY_DURESS_SALT, salt.encode())
            .putString(KEY_DURESS_HASH, hash(pin, salt).encode())
            .commit()
        _duressEnabled.value = true
    }

    fun clearDuress() {
        prefs.edit().remove(KEY_DURESS_SALT).remove(KEY_DURESS_HASH).commit()
        _duressEnabled.value = false
    }

    suspend fun check(pin: String): PinCheckResult = withContext(Dispatchers.Default) {
        ensureLoaded()
        val now = System.currentTimeMillis()
        val lockedUntil = lockedUntil().coerceAtMost(now + MAX_LOCKOUT_MS)
        if (lockedUntil > now) return@withContext PinCheckResult.LockedOut(lockedUntil)

        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val until = if (attempts >= FREE_ATTEMPTS) {
            val step = (attempts - FREE_ATTEMPTS).coerceAtMost(5)
            now + (BASE_LOCKOUT_MS shl step).coerceAtMost(MAX_LOCKOUT_MS)
        } else {
            0L
        }
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts)
            .putLong(KEY_LOCKED_UNTIL, until)
            .commit()

        val isPin = matches(pin, KEY_PIN_SALT, KEY_PIN_HASH)
        val isDuress = matches(pin, KEY_DURESS_SALT, KEY_DURESS_HASH)
        when {
            isDuress -> PinCheckResult.Duress
            isPin -> {
                markUnlocked()
                PinCheckResult.Correct
            }
            else -> PinCheckResult.Wrong(until)
        }
    }

    private fun matches(pin: String, saltKey: String, hashKey: String): Boolean {
        val salt = prefs.getString(saltKey, null)?.decode()
        val expected = prefs.getString(hashKey, null)?.decode()
        val actual = hash(pin, salt ?: DUMMY_SALT)
        return salt != null && expected != null && MessageDigest.isEqual(actual, expected)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }

    private fun ByteArray.encode() = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decode() = runCatching { Base64.decode(this, Base64.NO_WRAP) }.getOrNull()

    internal companion object {
        const val PREFS_FILE = "app_lock_secure_prefs"
        const val KEY_MODE = "mode"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_DURESS_SALT = "duress_salt"
        const val KEY_DURESS_HASH = "duress_hash"
        const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        const val KEY_LOCKED_UNTIL = "locked_until"
        const val ITERATIONS = 60_000
        const val KEY_BITS = 256
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15 * 60_000L
        val DUMMY_SALT = ByteArray(16)
    }
}
