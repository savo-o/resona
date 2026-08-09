package com.savoo.scclient.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Switches which launcher [android.content.pm.PackageManager] activity-alias is enabled, which is
 * what actually changes the icon shown on the home screen for [AppIconOption]. The alias names are
 * anchored to the fixed manifest namespace (not [Context.packageName]), since applicationIdSuffix
 * build variants (canary/debug) change the installed package name but not the compiled class names.
 */
object AppIconManager {
    private const val NAMESPACE = "com.savoo.scclient"
    private const val ALIAS_NORMAL = "$NAMESPACE.LauncherNormal"
    private const val ALIAS_DYNAMIC = "$NAMESPACE.LauncherDynamic"

    fun apply(context: Context, option: AppIconOption) {
        val (toEnable, toDisable) = when (option) {
            AppIconOption.NORMAL -> ALIAS_NORMAL to ALIAS_DYNAMIC
            AppIconOption.DYNAMIC -> ALIAS_DYNAMIC to ALIAS_NORMAL
        }
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            ComponentName(context.packageName, toEnable),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        pm.setComponentEnabledSetting(
            ComponentName(context.packageName, toDisable),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
