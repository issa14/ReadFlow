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
    const val DEBUG = "debug"

    fun readerRoute(bookId: String) = "reader/$bookId"
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
                onBack = { navController.popBackStack() }
            )
        }

        // ── Debug TTS (conservé) ──────────────────
        composable(Routes.DEBUG) {
            com.readflow.ui.screen.TtsTestScreen()
        }
    }
}
