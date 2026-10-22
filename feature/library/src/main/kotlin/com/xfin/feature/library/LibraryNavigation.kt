package com.xfin.feature.library

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
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

/**
 * Asks the title page owning this handle to reopen the version picker for [itemId] (the title or one
 * of its episodes), marking [failedVersionId] as the one that didn't play.
 */
fun SavedStateHandle.requestVersionRetry(itemId: String, failedVersionId: String, startMs: Long) {
    set(RETRY_FAILED_VERSION, failedVersionId)
    set(RETRY_START_MS, startMs)
    // Set last: the page reacts to this key.
    set(RETRY_ITEM, itemId)
}

internal const val RETRY_ITEM = "versionRetry.itemId"
internal const val RETRY_FAILED_VERSION = "versionRetry.failedVersionId"
internal const val RETRY_START_MS = "versionRetry.startMs"

@Serializable
data object MoviesRoute

@Serializable
data object ShowsRoute

/** [scope] is a [SearchScope] name; Library by default so huge channel lists don't drown titles. */
@Serializable
data class SearchRoute(val scope: String = SearchScope.LIBRARY.name)

fun NavGraphBuilder.libraryScreens(
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onOpenLibrary: (LibraryGridRoute) -> Unit,
    onPlay: (sourceId: Long, itemId: String, startMs: Long, mediaSourceId: String?) -> Unit,
    onConnect: () -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayChannel: (channelId: Long) -> Unit,
    onOpenRecentGuide: () -> Unit,
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
                onPlayChannel = onPlayChannel,
                onOpenRecentGuide = onOpenRecentGuide,
            )
        }
    }
    composable<MoviesRoute> {
        shell { CatalogScreen(MediaKind.MOVIE, onOpenItem = onOpenItem, onSearch = onSearch, onConnect = onConnect, onOpenSettings = onOpenSettings) }
    }
    composable<ShowsRoute> {
        shell { CatalogScreen(MediaKind.SERIES, onOpenItem = onOpenItem, onSearch = onSearch, onConnect = onConnect, onOpenSettings = onOpenSettings) }
    }
    composable<SearchRoute> {
        SearchScreen(onOpenItem = onOpenItem, onPlayChannel = onPlayChannel, onBack = onBack)
    }
    composable<LibraryGridRoute> {
        LibraryGridScreen(onOpenItem = onOpenItem, onBack = onBack)
    }
    composable<ItemRoute> {
        ItemDetailScreen(onOpenItem = onOpenItem, onPlay = onPlay, onBack = onBack)
    }
}
