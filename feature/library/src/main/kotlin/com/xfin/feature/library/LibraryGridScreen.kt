package com.xfin.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.model.LibraryView
import com.xfin.data.jellyfin.JellyfinRepository
import com.xfin.data.jellyfin.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryGridViewModel @Inject constructor(
    savedState: SavedStateHandle,
    jellyfin: JellyfinRepository,
) : ViewModel() {
    private val route = savedState.toRoute<LibraryGridRoute>()
    val title: String = route.name
    private val view = LibraryView(route.viewId, route.sourceId, route.name, route.collectionType, null)

    private val _sort = MutableStateFlow(LibrarySort.NAME)
    val sort: StateFlow<LibrarySort> = _sort.asStateFlow()

    val items = _sort.flatMapLatest { jellyfin.pagedItems(route.sourceId, view, it) }.cachedIn(viewModelScope)

    fun sortBy(sort: LibrarySort) {
        _sort.value = sort
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryGridScreen(
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryGridViewModel = hiltViewModel(),
) {
    val items = viewModel.items.collectAsLazyPagingItems()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { SortMenu(sort, viewModel::sortBy) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (isTv) 170.dp else 112.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(items.itemCount, key = items.itemKey { it.id }) { index ->
                val item = items[index]
                if (item != null) {
                    PosterCard(item, width = androidx.compose.ui.unit.Dp.Unspecified, onClick = { onOpenItem(item.sourceId, item.id) })
                } else {
                    Box(Modifier.padding(4.dp))
                }
            }
        }
    }
}
