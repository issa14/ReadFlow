package com.readflow.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}"
    const val BOOKMARKS = "bookmarks/{bookId}/{bookTitle}"
    const val SEARCH = "search/{bookId}/{bookTitle}"
    const val STATS = "stats"
    const val DEBUG = "debug"

    fun readerRoute(bookId: String) = "reader/$bookId"
    fun bookmarksRoute(bookId: String, bookTitle: String) = "bookmarks/$bookId/$bookTitle"
    fun searchRoute(bookId: String, bookTitle: String) = "search/$bookId/$bookTitle"
}

@Composable
fun ReadFlowNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY
    ) {
        // ── Bibliothèque ──────────────────────────
        composable(Routes.LIBRARY) {
            com.readflow.ui.screen.library.LibraryScreen(
                onBookClick = { bookId ->
                    navController.navigate(Routes.readerRoute(bookId))
                },
                onDebugClick = {
                    navController.navigate(Routes.DEBUG)
                },
                onStatsClick = {
                    navController.navigate(Routes.STATS)
                }
            )
        }

        // ── Lecteur ───────────────────────────────
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            com.readflow.ui.screen.reader.ReaderScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() },
                onBookmarksClick = { title ->
                    navController.navigate(Routes.bookmarksRoute(bookId, title))
                },
                onSearchClick = { title ->
                    navController.navigate(Routes.searchRoute(bookId, title))
                }
            )
        }

        // ── Signets ────────────────────────────────
        composable(
            route = Routes.BOOKMARKS,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("bookTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val bookTitle = backStackEntry.arguments?.getString("bookTitle") ?: ""
            com.readflow.ui.screen.bookmark.BookmarkScreen(
                bookId = bookId,
                bookTitle = bookTitle,
                onBack = { navController.popBackStack() },
                onNavigate = { chapter, sentence ->
                    navController.popBackStack()
                    // TODO: navigate back to reader with chapter/sentence params
                }
            )
        }

        // ── Recherche ───────────────────────────────
        composable(
            route = Routes.SEARCH,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("bookTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            val bookTitle = backStackEntry.arguments?.getString("bookTitle") ?: ""
            com.readflow.ui.screen.search.SearchScreen(
                bookId = bookId,
                bookTitle = bookTitle,
                onBack = { navController.popBackStack() },
                onNavigate = { _, _ -> navController.popBackStack() }
            )
        }

        // ── Statistiques ───────────────────────────
        composable(Routes.STATS) {
            com.readflow.ui.screen.stats.StatsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // ── Debug TTS (conservé) ──────────────────
        composable(Routes.DEBUG) {
            com.readflow.ui.screen.TtsTestScreen()
        }
    }
}
