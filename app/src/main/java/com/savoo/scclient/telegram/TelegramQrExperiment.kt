package com.savoo.scclient.telegram

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramQrExperiment @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("debug_experiments", Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(preferences.getBoolean("telegram_qr_sign_in", true))
    val enabled = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("telegram_qr_sign_in", enabled).apply()
        _enabled.value = enabled
    }
}
