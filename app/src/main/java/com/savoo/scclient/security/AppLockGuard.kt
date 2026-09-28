package com.savoo.scclient.security

import android.content.Context
import com.savoo.scclient.data.local.openSecurePrefsStrict
import com.savoo.scclient.debug.DebugLog

object AppLockGuard {

    private const val TAG = "AppLockGuard"
    private const val STATE_PREFS = "app_lock_state"
    private const val KEY_ENABLED = "enabled"
    private const val READ_ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 400L

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .commit()
    }

    fun verifyOrWipe(context: Context) {
        if (!isEnabled(context)) return
        repeat(READ_ATTEMPTS) { attempt ->
            val intact = try {
                val prefs = openSecurePrefsStrict(context, AppLockStore.PREFS_FILE)
                prefs.contains(AppLockStore.KEY_MODE) && prefs.contains(AppLockStore.KEY_PIN_HASH)
            } catch (e: Exception) {
                DebugLog.log(TAG, "lock storage unreadable on attempt ${attempt + 1}: ${e::class.simpleName}")
                null
            }
            when (intact) {
                true -> return
                false -> {
                    DebugLog.log(TAG, "lock storage is empty while the lock is on")
                    DuressWipe.wipeNow(context)
                    return
                }
                null -> if (attempt < READ_ATTEMPTS - 1) Thread.sleep(RETRY_DELAY_MS)
            }
        }
        DuressWipe.wipeNow(context)
    }
}
