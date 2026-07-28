package com.savoo.scclient.data.repository

import com.savoo.scclient.BuildConfig
import com.savoo.scclient.data.model.UpdateChannel
import com.savoo.scclient.data.remote.GitHubReleaseApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateCheckResult {
    data class Available(
        val channel: UpdateChannel,
        val versionLabel: String,
        val changelog: String?,
        val htmlUrl: String,
    ) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult
    data object Error : UpdateCheckResult
}

/**
 * Checks GitHub Releases for updates. GitHub releases have no native version-code field, so the
 * source of truth is a `<!-- versionCode: N -->` marker embedded in the release body - releases
 * without it are treated as not-newer rather than crashing or false-positiving off versionName.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val api: GitHubReleaseApi,
) {
    companion object {
        private const val OWNER = "savo-o"
        private const val REPO = "resona"
        private val VERSION_CODE_MARKER = Regex("""<!--\s*versionCode:\s*(\d+)\s*-->""")
    }

    suspend fun checkForUpdate(channel: UpdateChannel): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val releases = api.listReleases(OWNER, REPO)
            val candidate = when (channel) {
                UpdateChannel.RELEASE -> releases.firstOrNull { !it.draft && !it.prerelease }
                UpdateChannel.CANARY -> releases.firstOrNull { !it.draft && it.prerelease }
            } ?: return@withContext UpdateCheckResult.UpToDate

            val body = candidate.body.orEmpty()
            val versionCode = VERSION_CODE_MARKER.find(body)?.groupValues?.get(1)?.toIntOrNull()
                ?: return@withContext UpdateCheckResult.UpToDate

            if (versionCode <= BuildConfig.VERSION_CODE) {
                return@withContext UpdateCheckResult.UpToDate
            }

            UpdateCheckResult.Available(
                channel = channel,
                versionLabel = (candidate.tagName ?: candidate.name.orEmpty()).removePrefix("v"),
                changelog = if (channel == UpdateChannel.RELEASE) {
                    body.replace(VERSION_CODE_MARKER, "").trim()
                } else {
                    null
                },
                htmlUrl = candidate.htmlUrl,
            )
        }.getOrElse { UpdateCheckResult.Error }
    }
}
