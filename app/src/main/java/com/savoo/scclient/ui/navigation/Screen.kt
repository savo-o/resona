package com.savoo.scclient.ui.navigation

sealed class Screen(val route: String, val label: String) {
    data object Search : Screen("search", "Search")
    data object Library : Screen("library", "Library")
    data object Favorites : Screen("favorites", "Favorites")
    data object Account : Screen("account", "Account")
    data object Settings : Screen("settings", "Settings")
    data object Artist : Screen("artist/{userId}", "Artist") {
        fun createRoute(userId: Long) = "artist/$userId"
    }
    data object Playlist : Screen("playlist/{playlistId}", "Playlist") {
        fun createRoute(playlistId: Long) = "playlist/$playlistId"
    }
    data object FavoriteArtists : Screen("favorite_artists", "Favorite Artists")
    data object FavoritePlaylists : Screen("favorite_playlists", "Favorite Playlists")
}

val bottomNavScreens = listOf(Screen.Search, Screen.Library, Screen.Account)
