package com.savoo.scclient.di

import com.savoo.scclient.auth.AuthRepository
import com.savoo.scclient.auth.SessionRefresher
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.compat.AppContext
import com.savoo.scclient.compat.BuildConfig
import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.SharedPreferences
import com.savoo.scclient.compat.http.OkHttpClient
import com.savoo.scclient.data.local.AppDatabase
import com.savoo.scclient.data.remote.AuthInterceptor
import com.savoo.scclient.data.remote.BadgeRepository
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.data.remote.DataDomeChallenge
import com.savoo.scclient.data.remote.GeniusApi
import com.savoo.scclient.data.remote.GitHubReleaseApi
import com.savoo.scclient.data.remote.LyricsApi
import com.savoo.scclient.data.remote.RestClient
import com.savoo.scclient.data.remote.SoundCloudApi
import com.savoo.scclient.data.remote.SoundCloudImportRepository
import com.savoo.scclient.data.remote.WebViewApiBridge
import com.savoo.scclient.data.repository.FavoritesExporter
import com.savoo.scclient.data.repository.FavoritesImportManager
import com.savoo.scclient.data.repository.FavoritesRepository
import com.savoo.scclient.data.repository.FavoritesSyncManager
import com.savoo.scclient.data.repository.LyricSearchRepository
import com.savoo.scclient.data.repository.LyricsRepository
import com.savoo.scclient.data.repository.PlaylistsRepository
import com.savoo.scclient.data.repository.ProfileRepository
import com.savoo.scclient.data.repository.RecapRepository
import com.savoo.scclient.data.repository.SearchHistoryManager
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.StatsRepository
import com.savoo.scclient.data.repository.TelegramImportRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.data.repository.UpdateRepository
import com.savoo.scclient.data.repository.WaveformRepository
import com.savoo.scclient.debug.DebugLog
import com.savoo.scclient.player.OfflineTrackManager
import com.savoo.scclient.player.PlayerController
import com.savoo.scclient.player.SoundEffects
import com.savoo.scclient.player.TrackCache
import com.savoo.scclient.security.AppLockStore
import com.savoo.scclient.ui.components.PlaylistActionsViewModel
import com.savoo.scclient.ui.components.UndoController
import com.savoo.scclient.ui.screens.account.AccountViewModel
import com.savoo.scclient.ui.screens.account.ProfileEditViewModel
import com.savoo.scclient.ui.screens.artist.ArtistViewModel
import com.savoo.scclient.ui.screens.charts.ChartsViewModel
import com.savoo.scclient.ui.screens.debug.CrashReportViewModel
import com.savoo.scclient.ui.screens.debug.DebugMenuViewModel
import com.savoo.scclient.ui.screens.disliked.DislikedArtistsViewModel
import com.savoo.scclient.ui.screens.favorites.FavoriteArtistsViewModel
import com.savoo.scclient.ui.screens.favorites.FavoritePlaylistsViewModel
import com.savoo.scclient.ui.screens.favorites.FavoritesViewModel
import com.savoo.scclient.ui.screens.history.HistoryViewModel
import com.savoo.scclient.ui.screens.home.HomeViewModel
import com.savoo.scclient.ui.screens.importexport.ImportExportViewModel
import com.savoo.scclient.ui.screens.offline.OfflineTracksViewModel
import com.savoo.scclient.ui.screens.player.PlayerViewModel
import com.savoo.scclient.ui.screens.playlist.AddTracksViewModel
import com.savoo.scclient.ui.screens.playlist.PlaylistViewModel
import com.savoo.scclient.ui.screens.search.SearchViewModel
import com.savoo.scclient.ui.screens.settings.AppLockViewModel
import com.savoo.scclient.ui.screens.settings.SettingsViewModel
import com.savoo.scclient.ui.screens.settings.UpdateAutoCheckViewModel
import com.savoo.scclient.ui.screens.stats.StatisticsViewModel
import com.savoo.scclient.work.AutoExportManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val USER_AGENT_RESONA = "Resona (unofficial SoundCloud client)"
private const val USER_AGENT_MOBILE =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

private fun plainHttp(vararg headers: Pair<String, String>) = HttpClient {
    expectSuccess = false
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
    defaultRequest { headers.forEach { (k, v) -> header(k, v) } }
}

val appModule = module {
    single<Context> { AppContext }
    single<SharedPreferences> { get<Context>().getSharedPreferences("sc_client_prefs", Context.MODE_PRIVATE) }
    single { OkHttpClient() }

    single(named("soundcloud")) {
        HttpClient {
            expectSuccess = false
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    level = if (DebugLog.verboseNetworkLogging.value) LogLevel.BODY else LogLevel.INFO
                    logger = object : Logger {
                        override fun log(message: String) = DebugLog.log("HTTP", message)
                    }
                }
            }
        }.also { get<AuthInterceptor>().install(it) }
    }
    single { SoundCloudApi(RestClient(get(named("soundcloud")), "https://api-v2.soundcloud.com/")) }
    single { LyricsApi(RestClient(plainHttp("User-Agent" to USER_AGENT_RESONA), "https://lrclib.net/")) }
    single {
        GitHubReleaseApi(
            RestClient(
                plainHttp("Accept" to "application/vnd.github+json", "User-Agent" to USER_AGENT_RESONA),
                "https://api.github.com/",
            )
        )
    }
    single {
        GeniusApi(
            RestClient(
                plainHttp("User-Agent" to USER_AGENT_MOBILE, "Accept" to "text/html,application/json"),
                "https://genius.com/",
            )
        )
    }

    single { AppDatabase.create(get()) }
    single { get<AppDatabase>().favoritesDao() }
    single { get<AppDatabase>().offlineDao() }
    single { get<AppDatabase>().telegramImportDao() }
    single { get<AppDatabase>().playHistoryDao() }
    single { get<AppDatabase>().excludedArtistDao() }
    single { get<AppDatabase>().lyricsCacheDao() }
    single { get<AppDatabase>().lyricsSyncDao() }
    single { get<AppDatabase>().unavailableTrackDao() }
    single { get<AppDatabase>().localPlaylistDao() }

    singleOf(::TokenStore)
    singleOf(::ClientIdProvider)
    singleOf(::DataDomeChallenge)
    singleOf(::SessionRefresher)
    singleOf(::AuthInterceptor)
    singleOf(::WebViewApiBridge)
    singleOf(::AuthRepository)
    singleOf(::BadgeRepository)
    singleOf(::SoundCloudImportRepository)
    singleOf(::FavoritesExporter)
    singleOf(::FavoritesImportManager)
    singleOf(::FavoritesRepository)
    singleOf(::FavoritesSyncManager)
    singleOf(::LyricSearchRepository)
    singleOf(::LyricsRepository)
    singleOf(::PlaylistsRepository)
    singleOf(::ProfileRepository)
    singleOf(::RecapRepository)
    singleOf(::SearchHistoryManager)
    singleOf(::SettingsRepository)
    singleOf(::StatsRepository)
    singleOf(::TrackRepository)
    singleOf(::UpdateRepository)
    singleOf(::WaveformRepository)
    singleOf(::OfflineTrackManager)
    singleOf(::PlayerController)
    singleOf(::SoundEffects)
    singleOf(::TrackCache)
    singleOf(::AppLockStore)
    singleOf(::UndoController)
    singleOf(::AutoExportManager)
    single<com.savoo.scclient.telegram.TelegramClient> { com.savoo.scclient.telegram.TelegramUnavailableClient() }
    singleOf(::TelegramImportRepository)

    viewModelOf(::AccountViewModel)
    viewModelOf(::AddTracksViewModel)
    viewModelOf(::AppLockViewModel)
    viewModelOf(::ArtistViewModel)
    viewModelOf(::ChartsViewModel)
    viewModelOf(::CrashReportViewModel)
    viewModelOf(::DebugMenuViewModel)
    viewModelOf(::DislikedArtistsViewModel)
    viewModelOf(::FavoriteArtistsViewModel)
    viewModelOf(::FavoritePlaylistsViewModel)
    viewModelOf(::FavoritesViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::ImportExportViewModel)
    viewModelOf(::OfflineTracksViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::PlaylistActionsViewModel)
    viewModelOf(::PlaylistViewModel)
    viewModelOf(::ProfileEditViewModel)
    viewModelOf(::SearchViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::StatisticsViewModel)
    viewModelOf(::UpdateAutoCheckViewModel)
}
