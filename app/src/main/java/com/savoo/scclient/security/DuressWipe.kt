package com.savoo.scclient.security

import android.app.Activity
import android.app.Application
import android.app.job.JobScheduler
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.savoo.scclient.MainActivity
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore

object DuressWipe {

    private const val MARKER_NAME = "pending_reset"
    private const val RESTART_PROCESS_SUFFIX = ":restart"

    fun trigger(activity: Activity) {
        writeMarker(activity)
        runCatching { NotificationManagerCompat.from(activity).cancelAll() }
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(activity) }
        activity.startActivity(
            Intent(activity, RestartActivity::class.java)
                .putExtra(RestartActivity.EXTRA_PID, Process.myPid())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Runtime.getRuntime().exit(0)
    }

    fun wipeNow(context: Context) {
        writeMarker(context)
        runIfPending(context)
    }

    private fun writeMarker(context: Context) {
        val marker = marker(context)
        marker.parentFile?.mkdirs()
        FileOutputStream(marker).use {
            it.write(1)
            it.fd.sync()
        }
    }

    fun runIfPending(context: Context) {
        val marker = marker(context)
        if (!marker.exists()) return

        runCatching { (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancelAll() }
        runCatching {
            val resolver = context.contentResolver
            resolver.persistedUriPermissions.forEach { permission ->
                val flags = (if (permission.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or
                    (if (permission.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
                runCatching { resolver.releasePersistableUriPermission(permission.uri, flags) }
            }
        }
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyStore.aliases().toList().forEach { alias -> runCatching { keyStore.deleteEntry(alias) } }
        }

        val externalDirs = buildList {
            addAll(context.externalCacheDirs.filterNotNull())
            addAll(context.getExternalFilesDirs(null).filterNotNull().mapNotNull { it.parentFile })
            @Suppress("DEPRECATION")
            addAll(context.externalMediaDirs.filterNotNull())
        }
        externalDirs.forEach { runCatching { it.deleteRecursively() } }

        val keep = marker.parentFile
        val dataDirs = listOfNotNull(
            ContextCompat.createDeviceProtectedStorageContext(context)?.dataDir,
            File(context.applicationInfo.dataDir),
        ).distinct()
        dataDirs.forEach { dir ->
            dir.listFiles()?.forEach { child ->
                if (child.name != "lib" && child != keep) runCatching { child.deleteRecursively() }
            }
        }
        keep?.deleteRecursively()
    }

    fun isRestartProcess(application: Application): Boolean {
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrNull()
        }
        return name?.endsWith(RESTART_PROCESS_SUFFIX) == true
    }

    private fun marker(context: Context) = File(context.noBackupFilesDir, MARKER_NAME)
}

class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pid = intent.getIntExtra(EXTRA_PID, -1)
        if (pid > 0 && pid != Process.myPid()) Process.killProcess(pid)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        const val EXTRA_PID = "pid"
    }
}
