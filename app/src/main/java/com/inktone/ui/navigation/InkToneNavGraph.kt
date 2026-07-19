package com.inktone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}?jumpChapter={jumpChapter}&jumpSentence={jumpSentence}"
    const val BOOKMARKS = "bookmarks/{bookId}/{bookTitle}"
    const val SEARCH = "search/{bookId}/{bookTitle}"
    const val DEBUG = "debug"

    fun readerRoute(bookId: String) = "reader/$bookId"
    fun readerRoute(bookId: String, jumpChapter: Int, jumpSentence: Int) =
        "reader/$bookId?jumpChapter=$jumpChapter&jumpSentence=$jumpSentence"
    fun bookmarksRoute(bookId: String, bookTitle: String) = "bookmarks/$bookId/$bookTitle"
    fun searchRoute(bookId: String, bookTitle: String) = "search/$bookId/$bookTitle"
}

@Composable
fun InkToneNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY
    ) {
        // ── Bibliothèque ──────────────────────────
        composable(Routes.LIBRARY) {
            com.inktone.ui.screen.library.LibraryScreen(
                onBookClick = { bookId ->
                    navController.navigate(Routes.readerRoute(bookId))
                },
                onNavigateToBookmark = { bookId, chapterIndex, sentenceIndex ->
                    navController.navigate(Routes.readerRoute(bookId, chapterIndex, sentenceIndex))
                },
                onDebugClick = {
                    navController.navigate(Routes.DEBUG)
                }
            )
        }

        // ── Lecteur ───────────────────────────────
        composable(
            route = Routes.READER,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("jumpChapter") { type = NavType.IntType; defaultValue = -1 },
                navArgument("jumpSentence") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            com.inktone.ui.screen.reader.ReaderScreen(
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
            com.inktone.ui.screen.bookmark.BookmarkScreen(
                bookId = bookId,
                bookTitle = bookTitle,
                onBack = { navController.popBackStack() },
                onNavigate = { chapter, sentence ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("jumpChapter", chapter)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("jumpSentence", sentence)
                    navController.popBackStack()
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
            com.inktone.ui.screen.search.SearchScreen(
                bookId = bookId,
                bookTitle = bookTitle,
                onBack = { navController.popBackStack() },
                onNavigate = { chapter, sentence ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("jumpChapter", chapter)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("jumpSentence", sentence)
                    navController.popBackStack()
                }
            )
        }

        // ── Debug TTS (build debug uniquement) ────
        composable(Routes.DEBUG) {
            if (com.inktone.BuildConfig.DEBUG) {
                com.inktone.ui.screen.TtsTestScreen()
            }
        }
    }
}
