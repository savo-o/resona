package com.savoo.scclient.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.savoo.scclient.R
import android.widget.Toast
import androidx.annotation.StringRes
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.data.remote.DeepLinkResult
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.savoo.scclient.ui.screens.account.AccountScreen
import com.savoo.scclient.ui.screens.artist.ArtistScreen
import com.savoo.scclient.ui.screens.charts.ChartsScreen
import com.savoo.scclient.ui.screens.debug.CrashReportHost
import com.savoo.scclient.ui.screens.debug.DebugMenuScreen
import com.savoo.scclient.ui.screens.disliked.DislikedArtistsScreen
import com.savoo.scclient.ui.screens.favorites.FavoriteArtistsScreen
import com.savoo.scclient.ui.screens.favorites.FavoritePlaylistsScreen
import com.savoo.scclient.ui.screens.favorites.FavoritesScreen
import com.savoo.scclient.ui.screens.history.HistoryScreen
import com.savoo.scclient.ui.screens.home.HomeScreen
import com.savoo.scclient.ui.screens.importexport.ImportExportScreen
import com.savoo.scclient.ui.screens.offline.OfflineTracksScreen
import com.savoo.scclient.ui.screens.player.PlayerSheet
import com.savoo.scclient.ui.screens.playlist.PlaylistScreen
import com.savoo.scclient.ui.screens.search.SearchScreen
import com.savoo.scclient.ui.screens.settings.AppLockSettingsScreen
import com.savoo.scclient.ui.screens.settings.CustomizationScreen
import com.savoo.scclient.ui.screens.settings.SettingsScreen
import com.savoo.scclient.ui.screens.settings.UpdateCheckHost
import com.savoo.scclient.ui.screens.stats.StatisticsScreen
import com.savoo.scclient.ui.navigation.DeepLinkTarget
import com.savoo.scclient.ui.components.ConnectivityBanner

private val navOrder = listOf(Screen.Home.route, Screen.Search.route)

private fun iconFor(route: String): ImageVector = when (route) {
    Screen.Home.route -> Icons.Filled.Home
    Screen.Search.route -> Icons.Filled.Search
    else -> Icons.Filled.Home
}

private fun labelResFor(route: String): Int = when (route) {
    Screen.Home.route -> R.string.nav_home
    Screen.Search.route -> R.string.nav_search
    else -> R.string.nav_home
}

private fun slideTransition(
    slideForward: Boolean,
): Pair<EnterTransition, ExitTransition> {
    val animSpec = spring<IntOffset>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val fadeSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val enter = slideInHorizontally(
        initialOffsetX = { if (slideForward) it else -it },
        animationSpec = animSpec
    ) + fadeIn(fadeSpec)
    val exit = slideOutHorizontally(
        targetOffsetX = { if (slideForward) -it else it },
        animationSpec = animSpec
    ) + fadeOut(fadeSpec)
    return enter to exit
}

private sealed interface DeepLinkOutcome {
    data class OpenArtist(val userId: Long) : DeepLinkOutcome
    data class OpenPlaylist(val playlistId: Long) : DeepLinkOutcome
    data class PlayTrack(val track: Track) : DeepLinkOutcome
    data class Failed(@StringRes val messageRes: Int) : DeepLinkOutcome
}

@UnstableApi
private suspend fun resolveDeepLink(entryPoint: DeepLinkEntryPoint, url: String): DeepLinkOutcome =
    withContext(Dispatchers.IO) {
        entryPoint.soundCloudImportRepository().resolveUrl(url).fold(
            onSuccess = { result ->
                when (result) {
                    is DeepLinkResult.User -> DeepLinkOutcome.OpenArtist(result.userId)
                    is DeepLinkResult.Playlist -> DeepLinkOutcome.OpenPlaylist(result.playlistId)
                    is DeepLinkResult.Track -> runCatching { entryPoint.trackRepository().getTrack(result.trackId) }
                        .fold(
                            onSuccess = { DeepLinkOutcome.PlayTrack(it) },
                            onFailure = { DeepLinkOutcome.Failed(R.string.search_error_track_gone_title) },
                        )
                }
            },
            onFailure = { DeepLinkOutcome.Failed(R.string.deep_link_failed) },
        )
    }

@UnstableApi
@Composable
fun RootScreen(initialDeepLink: DeepLinkTarget? = null, deepLinkKey: Int = 0) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialDeepLink, deepLinkKey) {
        when (initialDeepLink) {
            is DeepLinkTarget.Artist -> {
                navController.navigate(Screen.Artist.createRoute(initialDeepLink.userId))
            }
            is DeepLinkTarget.Playlist -> {
                navController.navigate(Screen.Playlist.createRoute(initialDeepLink.playlistId))
            }
            is DeepLinkTarget.Shortcut -> {
                val route = when (initialDeepLink.target) {
                    ShortcutTarget.FAVORITES -> Screen.Favorites.route
                    ShortcutTarget.OFFLINE -> Screen.OfflineTracks.route
                    ShortcutTarget.SEARCH -> Screen.Search.route
                }
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            is DeepLinkTarget.ResolveUrl -> {
                val context = navController.context
                val entryPoint = EntryPointAccessors
                    .fromApplication(context.applicationContext, DeepLinkEntryPoint::class.java)
                when (val outcome = resolveDeepLink(entryPoint, initialDeepLink.url)) {
                    is DeepLinkOutcome.OpenArtist -> navController.navigate(Screen.Artist.createRoute(outcome.userId))
                    is DeepLinkOutcome.OpenPlaylist -> navController.navigate(Screen.Playlist.createRoute(outcome.playlistId))
                    is DeepLinkOutcome.PlayTrack -> entryPoint.playerController().playQueue(
                        listOf(outcome.track),
                        0,
                        startPositionMs = initialDeepLink.startPositionMs,
                    )
                    is DeepLinkOutcome.Failed ->
                        Toast.makeText(context, outcome.messageRes, Toast.LENGTH_SHORT).show()
                }
            }
            is DeepLinkTarget.None -> {}
            null -> {}
        }
    }

    UpdateCheckHost()
    CrashReportHost()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        val selectDestination: (Screen) -> Unit = { screen ->
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Row(Modifier.fillMaxSize()) {
        if (useRail) {
            NavigationRail(modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                listOf(
                    Triple(Screen.Home, Icons.Filled.Home, R.string.nav_home),
                    Triple(Screen.Search, Icons.Filled.Search, R.string.nav_search),
                    Triple(Screen.Favorites, Icons.Filled.Favorite, R.string.nav_favorites),
                    Triple(Screen.OfflineTracks, Icons.Filled.CloudDownload, R.string.shortcut_offline_short),
                    Triple(Screen.Settings, Icons.Filled.Settings, R.string.nav_settings),
                ).forEach { (screen, icon, label) ->
                    NavigationRailItem(
                        selected = currentRoute == screen.route,
                        onClick = { selectDestination(screen) },
                        icon = { Icon(icon, contentDescription = stringResource(label)) },
                        label = { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                PlayerSheet(
                    showDockBar = !useRail,
                    onArtistClick = { userId -> navController.navigate(Screen.Artist.createRoute(userId)) },
                    dockBar = {
                        ResonaDockBar(
                            items = bottomNavScreens.map { it to iconFor(it.route) },
                            currentRoute = currentRoute,
                            labelFor = { screen -> stringResource(labelResFor(screen.route)) },
                            onSelect = { screen ->
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    },
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .then(if (useRail && currentRoute != Screen.Home.route) Modifier.widthIn(max = 1040.dp) else Modifier)
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding()),
                    enterTransition = {
                        val from = initialState.destination.route ?: return@NavHost fadeIn(spring())
                        val to = targetState.destination.route ?: return@NavHost fadeIn(spring())
                        val fromIdx = navOrder.indexOf(from)
                        val toIdx = navOrder.indexOf(to)
                        val slideForward = if (fromIdx >= 0 && toIdx >= 0) toIdx > fromIdx else true
                        slideTransition(slideForward).first
                    },
                    exitTransition = {
                        val from = initialState.destination.route ?: return@NavHost fadeOut(spring())
                        val to = targetState.destination.route ?: return@NavHost fadeOut(spring())
                        val fromIdx = navOrder.indexOf(from)
                        val toIdx = navOrder.indexOf(to)
                        val slideForward = if (fromIdx >= 0 && toIdx >= 0) toIdx > fromIdx else true
                        slideTransition(slideForward).second
                    },
                    popEnterTransition = {
                        val from = initialState.destination.route ?: return@NavHost fadeIn(spring())
                        val to = targetState.destination.route ?: return@NavHost fadeIn(spring())
                        val fromIdx = navOrder.indexOf(from)
                        val toIdx = navOrder.indexOf(to)
                        val slideForward = if (fromIdx >= 0 && toIdx >= 0) toIdx > fromIdx else true
                        slideTransition(slideForward).first
                    },
                    popExitTransition = {
                        val from = initialState.destination.route ?: return@NavHost fadeOut(spring())
                        val to = targetState.destination.route ?: return@NavHost fadeOut(spring())
                        val fromIdx = navOrder.indexOf(from)
                        val toIdx = navOrder.indexOf(to)
                        val slideForward = if (fromIdx >= 0 && toIdx >= 0) toIdx > fromIdx else true
                        slideTransition(slideForward).second
                    },
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            onAccount = { navController.navigate(Screen.Account.route) },
                            onSettings = { navController.navigate(Screen.Settings.route) },
                            onImportExport = { navController.navigate(Screen.ImportExport.route) },
                            onArtistClick = { userId -> navController.navigate(Screen.Artist.createRoute(userId)) },
                            onFavorites = { navController.navigate(Screen.Favorites.route) },
                            onFavoriteArtists = { navController.navigate(Screen.FavoriteArtists.route) },
                            onFavoritePlaylists = { navController.navigate(Screen.FavoritePlaylists.route) },
                            onPlaylistClick = { playlistId -> navController.navigate(Screen.Playlist.createRoute(playlistId)) },
                            onOfflineTracks = { navController.navigate(Screen.OfflineTracks.route) },
                            onStatistics = { navController.navigate(Screen.Statistics.route) },
                            onHistory = { navController.navigate(Screen.History.route) },
                        )
                    }
                    composable(Screen.Search.route) {
                        SearchScreen(
                            onArtistClick = { userId -> navController.navigate(Screen.Artist.createRoute(userId)) },
                            onPlaylistClick = { playlistId -> navController.navigate(Screen.Playlist.createRoute(playlistId)) },
                        )
                    }
                    composable(Screen.Favorites.route) {
                        FavoritesScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.FavoriteArtists.route) {
                        FavoriteArtistsScreen(
                            onBack = { navController.popBackStack() },
                            onArtistClick = { userId -> navController.navigate(Screen.Artist.createRoute(userId)) },
                        )
                    }
                    composable(Screen.FavoritePlaylists.route) {
                        FavoritePlaylistsScreen(
                            onBack = { navController.popBackStack() },
                            onPlaylistClick = { playlistId -> navController.navigate(Screen.Playlist.createRoute(playlistId)) },
                        )
                    }
                    composable(Screen.Statistics.route) {
                        StatisticsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.History.route) {
                        HistoryScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Account.route) {
                        AccountScreen(onOpenSettings = { navController.navigate(Screen.Settings.route) })
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenDebugMenu = { navController.navigate(Screen.DebugMenu.route) },
                            onOpenDislikedArtists = { navController.navigate(Screen.DislikedArtists.route) },
                            onOpenCustomization = { navController.navigate(Screen.Customization.route) },
                            onOpenAppLock = { navController.navigate(Screen.AppLock.route) },
                        )
                    }
                    composable(Screen.AppLock.route) {
                        AppLockSettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.Customization.route) {
                        CustomizationScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.DebugMenu.route) {
                        DebugMenuScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.DislikedArtists.route) {
                        DislikedArtistsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.ImportExport.route) {
                        ImportExportScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Screen.OfflineTracks.route) {
                        OfflineTracksScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = Screen.Charts.route,
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                    ) {
                        ChartsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = Screen.Artist.route,
                        arguments = listOf(navArgument("userId") { type = NavType.LongType }),
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getLong("userId") ?: return@composable
                        ArtistScreen(
                            userId = userId,
                            onBack = { navController.popBackStack() },
                            onArtistClick = { id -> navController.navigate(Screen.Artist.createRoute(id)) },
                            onPlaylistClick = { id -> navController.navigate(Screen.Playlist.createRoute(id)) },
                        )
                    }
                    composable(
                        route = Screen.Playlist.route,
                        arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
                        enterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                        exitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { -it / 3 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                        popEnterTransition = {
                            slideInHorizontally(
                                initialOffsetX = { -it / 3 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeIn(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                        popExitTransition = {
                            slideOutHorizontally(
                                targetOffsetX = { it },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                            ) + fadeOut(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium))
                        },
                    ) { backStackEntry ->
                        val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
                        PlaylistScreen(
                            playlistId = playlistId,
                            onBack = { navController.popBackStack() },
                            onArtistClick = { id -> navController.navigate(Screen.Artist.createRoute(id)) },
                            onPlaylistClick = { id -> navController.navigate(Screen.Playlist.createRoute(id)) },
                        )
                    }
                }
            }
        }
        }
        ConnectivityBanner(modifier = Modifier.align(Alignment.TopCenter))
    }
}
