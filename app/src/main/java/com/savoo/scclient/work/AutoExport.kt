package com.savoo.scclient.work

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.savoo.scclient.data.repository.FavoritesExporter
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.debug.DebugLog
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AutoExport"
private const val WORK_NAME = "favorites_auto_export"
private const val MAX_ATTEMPTS = 3

@HiltWorker
class FavoritesAutoExportWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val exporter: FavoritesExporter,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.settings.first()
        if (!settings.autoExportEnabled) return Result.success()
        val uri = settings.autoExportUri?.let(Uri::parse) ?: return Result.success()

        return exporter.exportToFile(uri).fold(
            onSuccess = {
                DebugLog.log(TAG, "worker export OK -> $uri")
                settingsRepository.setAutoExportLastRunAt(System.currentTimeMillis())
                Result.success()
            },
            onFailure = {
                DebugLog.log(TAG, "worker export failed (attempt ${runAttemptCount + 1}): $it")
                if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
            },
        )
    }
}

@Singleton
class AutoExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val exporter: FavoritesExporter,
) {

    suspend fun syncSchedule() {
        val settings = settingsRepository.settings.first()
        val workManager = WorkManager.getInstance(context)
        if (!settings.autoExportEnabled || settings.autoExportUri == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            DebugLog.log(TAG, "schedule cancelled")
            return
        }
        val request = PeriodicWorkRequestBuilder<FavoritesAutoExportWorker>(
            settings.autoExportInterval.hours,
            TimeUnit.HOURS,
        ).build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        DebugLog.log(TAG, "scheduled every ${settings.autoExportInterval.hours}h")
    }

    // OEM battery managers (MIUI in particular) routinely delay or drop periodic work, so a due
    // export also runs on app start - that keeps the copy fresh even when the scheduler never fires.
    suspend fun catchUpIfDue() {
        val settings = settingsRepository.settings.first()
        if (!settings.autoExportEnabled) return
        val uri = settings.autoExportUri?.let(Uri::parse) ?: return
        val dueAt = settings.autoExportLastRunAt + TimeUnit.HOURS.toMillis(settings.autoExportInterval.hours)
        if (System.currentTimeMillis() < dueAt) return
        exporter.exportToFile(uri)
            .onSuccess {
                DebugLog.log(TAG, "catch-up export OK -> $uri")
                settingsRepository.setAutoExportLastRunAt(System.currentTimeMillis())
            }
            .onFailure { DebugLog.log(TAG, "catch-up export failed: $it") }
    }

    suspend fun exportNow(): Result<Unit> {
        val settings = settingsRepository.settings.first()
        val uri = settings.autoExportUri?.let(Uri::parse)
            ?: return Result.failure(IllegalStateException("No auto export destination"))
        return exporter.exportToFile(uri).onSuccess {
            settingsRepository.setAutoExportLastRunAt(System.currentTimeMillis())
        }
    }
}
