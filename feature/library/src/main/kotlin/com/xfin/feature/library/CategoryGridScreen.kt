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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
import com.xfin.core.model.MediaKind
import com.xfin.data.jellyfin.LibrarySort
import com.xfin.data.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** "View all" for one row of the Movies or Series tab: a category, or every title when [categoryId] is null. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CategoryGridViewModel @Inject constructor(
    savedState: SavedStateHandle,
    library: LibraryRepository,
) : ViewModel() {
    private val route = savedState.toRoute<CategoryGridRoute>()
    val title: String = route.name
    private val kind = MediaKind.valueOf(route.kind)

    private val _sort = MutableStateFlow(LibrarySort.valueOf(route.sort))
    val sort: StateFlow<LibrarySort> = _sort.asStateFlow()

    val items = _sort.flatMapLatest { sort ->
        flow {
            val category = route.categoryId
            emitAll(
                if (category == null) {
                    library.pagedAll(route.sourceId, kind, sort)
                } else {
                    library.pagedCategory(route.sourceId, kind, category, sort)
                },
            )
        }
    }.cachedIn(viewModelScope)

    fun sortBy(sort: LibrarySort) {
        _sort.value = sort
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryGridScreen(
    onOpenItem: (sourceId: Long, itemId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: CategoryGridViewModel = hiltViewModel(),
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
            // Same columns as the Movies and Series tabs.
            columns = GridCells.Adaptive(if (isTv) 124.dp else 112.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 24.dp else 16.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(items.itemCount, key = items.itemKey { "${it.sourceId}-${it.id}" }) { index ->
                val item = items[index]
                if (item != null) {
                    PosterCard(item, width = Dp.Unspecified, onClick = { onOpenItem(item.sourceId, item.id) })
                } else {
                    Box(Modifier.padding(4.dp))
                }
            }
        }
    }
}
