package com.xfin.feature.library

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.xfin.core.model.MediaKind
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data class LibraryGridRoute(val sourceId: Long, val viewId: String, val name: String, val collectionType: String?)

@Serializable
data class ItemRoute(val sourceId: Long, val itemId: String)

@Serializable
data object MoviesRoute

@Serializable
data object ShowsRoute

@Serializable
data object SearchRoute

fun NavGraphBuilder.libraryScreens(
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onOpenLibrary: (LibraryGridRoute) -> Unit,
    onPlay: (sourceId: Long, itemId: String, startMs: Long, mediaSourceId: String?) -> Unit,
    onConnect: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    shell: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    composable<LibraryRoute> {
        shell {
            LibraryHomeScreen(
                onOpenItem = onOpenItem,
                onOpenLibrary = onOpenLibrary,
                onPlay = onPlay,
                onConnect = onConnect,
                onSearch = onSearch,
                onOpenSettings = onOpenSettings,
            )
        }
    }
    composable<MoviesRoute> {
        shell { CatalogScreen(MediaKind.MOVIE, onOpenItem = onOpenItem, onSearch = onSearch, onConnect = onConnect) }
    }
    composable<ShowsRoute> {
        shell { CatalogScreen(MediaKind.SERIES, onOpenItem = onOpenItem, onSearch = onSearch, onConnect = onConnect) }
    }
    composable<SearchRoute> {
        SearchScreen(onOpenItem = onOpenItem, onBack = onBack)
    }
    composable<LibraryGridRoute> {
        LibraryGridScreen(onOpenItem = onOpenItem, onBack = onBack)
    }
    composable<ItemRoute> {
        ItemDetailScreen(onOpenItem = onOpenItem, onPlay = onPlay, onBack = onBack)
    }
}
