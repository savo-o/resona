package com.savoo.scclient.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.savoo.scclient.MainActivity
import com.savoo.scclient.R

object AppShortcuts {
    fun install(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "favorites", ShortcutTarget.FAVORITES, R.drawable.ic_shortcut_favorite, R.string.shortcut_favorites_short, R.string.shortcut_favorites_long),
            shortcut(context, "offline", ShortcutTarget.OFFLINE, R.drawable.ic_shortcut_offline, R.string.shortcut_offline_short, R.string.shortcut_offline_long),
            shortcut(context, "search", ShortcutTarget.SEARCH, R.drawable.ic_shortcut_search, R.string.shortcut_search_short, R.string.shortcut_search_long),
        )
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun shortcut(
        context: Context,
        id: String,
        target: ShortcutTarget,
        iconRes: Int,
        shortLabelRes: Int,
        longLabelRes: Int,
    ): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(DeepLinkTarget.EXTRA_SHORTCUT_TARGET, target.name)
        }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(shortLabelRes))
            .setLongLabel(context.getString(longLabelRes))
            .setIcon(IconCompat.createWithResource(context, iconRes))
            .setIntent(intent)
            .build()
    }
}
