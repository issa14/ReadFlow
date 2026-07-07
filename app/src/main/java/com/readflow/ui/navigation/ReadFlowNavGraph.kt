package com.readflow.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

/**
 * Routes de navigation type-safe pour ReadFlow.
 * Sera enrichi au fil des phases.
 */
object Routes {
    const val LIBRARY = "library"
    const val READER = "reader/{bookId}"
}

/**
 * NavGraph principal. Point d'entrée unique de la navigation.
 * Placeholder — sera remplacé par les vrais écrans en Phase 3.
 */
@Composable
fun ReadFlowNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.LIBRARY
    ) {
        composable(Routes.LIBRARY) {
            // TODO: Phase 3 — Remplacer par LibraryScreen
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ReadFlow",
                    style = MaterialTheme.typography.displayLarge
                )
            }
        }

        composable(Routes.READER) {
            // TODO: Phase 3 — Remplacer par ReaderScreen
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lecteur",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    }
}
