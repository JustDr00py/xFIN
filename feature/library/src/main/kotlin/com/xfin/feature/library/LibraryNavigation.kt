package com.xfin.feature.library

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data class LibraryGridRoute(val sourceId: Long, val viewId: String, val name: String, val collectionType: String?)

@Serializable
data class ItemRoute(val sourceId: Long, val itemId: String)

fun NavGraphBuilder.libraryScreens(
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onOpenLibrary: (LibraryGridRoute) -> Unit,
    onPlay: (sourceId: Long, itemId: String, startMs: Long) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    shell: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    composable<LibraryRoute> {
        shell { LibraryHomeScreen(onOpenItem = onOpenItem, onOpenLibrary = onOpenLibrary, onPlay = onPlay, onConnect = onConnect) }
    }
    composable<LibraryGridRoute> {
        LibraryGridScreen(onOpenItem = onOpenItem, onBack = onBack)
    }
    composable<ItemRoute> {
        ItemDetailScreen(onOpenItem = onOpenItem, onPlay = onPlay, onBack = onBack)
    }
}
