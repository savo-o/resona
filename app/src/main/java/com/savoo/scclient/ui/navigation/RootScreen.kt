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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.common.util.UnstableApi
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.savoo.scclient.ui.screens.account.AccountScreen
import com.savoo.scclient.ui.screens.artist.ArtistScreen
import com.savoo.scclient.ui.screens.favorites.FavoriteArtistsScreen
import com.savoo.scclient.ui.screens.favorites.FavoritePlaylistsScreen
import com.savoo.scclient.ui.screens.favorites.FavoritesScreen
import com.savoo.scclient.ui.screens.importexport.ImportExportScreen
import com.savoo.scclient.ui.screens.library.LibraryScreen
import com.savoo.scclient.ui.screens.player.PlayerSheet
import com.savoo.scclient.ui.screens.playlist.PlaylistScreen
import com.savoo.scclient.ui.screens.search.SearchScreen
import com.savoo.scclient.ui.screens.settings.SettingsScreen

private val navOrder = listOf(Screen.Search.route, Screen.Library.route, Screen.Account.route)

private fun iconFor(route: String): ImageVector = when (route) {
    Screen.Search.route -> Icons.Filled.Search
    Screen.Library.route -> Icons.Filled.LibraryMusic
    Screen.Account.route -> Icons.Filled.AccountCircle
    else -> Icons.Filled.AccountCircle
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

@UnstableApi
@Composable
fun RootScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            Column {
                PlayerSheet(onArtistClick = { userId -> navController.navigate(Screen.Artist.createRoute(userId)) })
                NavigationBar {
                    bottomNavScreens.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(iconFor(screen.route), contentDescription = screen.label) },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Screen.Library.route,
                modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
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
                composable(Screen.Search.route) {
                    SearchScreen(
                        onArtistClick = { userId -> navController.navigate(Screen.Artist.createRoute(userId)) },
                        onPlaylistClick = { playlistId -> navController.navigate(Screen.Playlist.createRoute(playlistId)) },
                    )
                }
                composable(Screen.Library.route) {
                    val libraryViewModel = hiltViewModel<com.savoo.scclient.ui.screens.library.LibraryViewModel>()
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onFavorites = { navController.navigate(Screen.Favorites.route) },
                        onSettings = { navController.navigate(Screen.Settings.route) },
                        onImportExport = { navController.navigate(Screen.ImportExport.route) },
                        onFavoriteArtists = { navController.navigate(Screen.FavoriteArtists.route) },
                        onFavoritePlaylists = { navController.navigate(Screen.FavoritePlaylists.route) },
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
                composable(Screen.Account.route) {
                    AccountScreen(onOpenSettings = { navController.navigate(Screen.Settings.route) })
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.ImportExport.route) {
                    ImportExportScreen(onBack = { navController.popBackStack() })
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
                    )
                }
            }
        }
    }
}
