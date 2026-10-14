package com.xfin.feature.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.RefreshState
import com.xfin.core.designsystem.theme.XfinColors

/** Live TV: a grid of categories (like UHF); each opens the guide for its channels. */
@Composable
internal fun LiveTvScreen(
    onOpenCategory: (ChannelFilter) -> Unit,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val refresh by viewModel.refreshState.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val firstFocus = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = if (isTv) 24.dp else 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Live TV", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
        }
        AnimatedVisibility(refresh is RefreshState.Running) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        }
        val list = categories
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyVerticalGrid(
            columns = if (isTv) GridCells.Adaptive(300.dp) else GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTv) 20.dp else 12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
            items(list, key = { it.filter.toString() }) { category ->
                CategoryTile(
                    category = category,
                    onClick = { onOpenCategory(category.filter) },
                    modifier = if (category === list.first()) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
        if (isTv) LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
}

@Composable
private fun CategoryTile(category: Category, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val icon: ImageVector? = when (category.filter) {
        ChannelFilter.Favorites -> Icons.Rounded.Star
        ChannelFilter.Recent -> Icons.Rounded.History
        ChannelFilter.All -> Icons.Rounded.LiveTv
        else -> null
    }
    FocusSurface(onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 92.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        null,
                        tint = if (category.filter == ChannelFilter.Favorites) XfinColors.Favorite else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    category.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            category.count?.let {
                Text(
                    "%,d channels".format(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
