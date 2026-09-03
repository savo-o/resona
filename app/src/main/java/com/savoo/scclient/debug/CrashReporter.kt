package com.savoo.scclient.debug

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashReporter {
    private const val PREFS_NAME = "crash_reports"
    private const val KEY_REPORT = "pending_report"

    private lateinit var prefs: SharedPreferences

    fun install(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                prefs.edit().putString(KEY_REPORT, buildReport(throwable)).commit()
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun buildReport(throwable: Throwable): String = buildString {
        appendLine("Crash at ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine(Log.getStackTraceString(throwable))
        val trace = DebugLog.entries.value.filterNot { it.contains("[HTTP]") }
        if (trace.isNotEmpty()) {
            appendLine()
            appendLine("Recent trace:")
            trace.takeLast(50).forEach { appendLine(it) }
        }
    }

    fun consumePendingReport(): String? {
        if (!::prefs.isInitialized) return null
        val report = prefs.getString(KEY_REPORT, null) ?: return null
        prefs.edit().remove(KEY_REPORT).apply()
        return report
    }
}
