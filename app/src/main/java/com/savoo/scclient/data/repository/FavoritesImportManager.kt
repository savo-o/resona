package com.savoo.scclient.data.repository

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val IMPORT_DONE_VISIBLE_MS = 5000L

data class FavoritesImportState(
    val fraction: Float?,
    val result: FavoritesExporter.ImportResult,
    val finished: Boolean = false,
    val errorMessage: String? = null,
)

@Singleton
class FavoritesImportManager @Inject constructor(
    private val exporter: FavoritesExporter,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var job: Job? = null
    private var running = false
    private var generation = 0

    private val _state = MutableStateFlow<FavoritesImportState?>(null)
    val state = _state.asStateFlow()

    fun start(uri: Uri): Boolean {
        synchronized(lock) {
            if (running) return false
            running = true
            val launchGeneration = ++generation
            job?.cancel()
            _state.value = FavoritesImportState(fraction = 0f, result = FavoritesExporter.ImportResult(0, 0, 0))
            job = scope.launch {
                val outcome = exporter.importFromFile(uri) { progress ->
                    synchronized(lock) {
                        if (running && generation == launchGeneration) {
                            _state.value = FavoritesImportState(progress.fraction, progress.result)
                        }
                    }
                }
                val finalState = outcome.fold(
                    onSuccess = { FavoritesImportState(fraction = 1f, result = it, finished = true) },
                    onFailure = { error ->
                        val partial = (error as? FavoritesExporter.ImportFailedException)?.partial
                            ?: _state.value?.result
                            ?: FavoritesExporter.ImportResult(0, 0, 0)
                        FavoritesImportState(
                            fraction = _state.value?.fraction,
                            result = partial,
                            finished = true,
                            errorMessage = error.message.orEmpty(),
                        )
                    },
                )
                synchronized(lock) {
                    if (generation != launchGeneration) return@launch
                    running = false
                    _state.value = finalState
                }
                delay(IMPORT_DONE_VISIBLE_MS)
                synchronized(lock) {
                    if (!running && generation == launchGeneration) _state.value = null
                }
            }
            return true
        }
    }

    fun cancel() {
        synchronized(lock) {
            generation++
            job?.cancel()
            job = null
            running = false
            _state.value = null
        }
    }
}
