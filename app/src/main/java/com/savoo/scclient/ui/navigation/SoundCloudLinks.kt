package com.savoo.scclient.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.provider.Settings

object SoundCloudLinks {

    val hosts = setOf("soundcloud.com", "m.soundcloud.com", "www.soundcloud.com", "on.soundcloud.com")

    fun startPositionMs(url: String): Long {
        val fragment = Uri.parse(url).fragment ?: return 0L
        val raw = fragment.split('&')
            .firstOrNull { it.startsWith("t=") }
            ?.removePrefix("t=")
            ?.trim()
            ?: return 0L
        return parseTime(raw)?.times(1000L) ?: 0L
    }

    private fun parseTime(raw: String): Long? {
        if (raw.contains(':')) {
            val parts = raw.split(':').map { it.toLongOrNull() ?: return null }
            if (parts.size > 3) return null
            return parts.fold(0L) { acc, part -> acc * 60 + part }
        }
        raw.toLongOrNull()?.let { return it }
        val match = Regex("""^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?$""").matchEntire(raw) ?: return null
        if (match.value.isEmpty()) return null
        val (h, m, s) = match.destructured
        return (h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)
    }

    fun isHandlingEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return true
        val state = runCatching { manager.getDomainVerificationUserState(context.packageName) }.getOrNull()
            ?: return true
        return state.hostToStateMap.any { (host, value) ->
            host in hosts && value != DomainVerificationUserState.DOMAIN_STATE_NONE
        }
    }

    fun openHandlingSettings(context: Context) {
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
