package com.xfin.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.xfin.core.designsystem.component.windowWidthDp
import androidx.compose.ui.unit.dp
import com.xfin.core.designsystem.component.FocusSurface
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.feature.livetv.LiveTvRoute
import com.xfin.feature.library.LibraryRoute
import com.xfin.feature.library.MoviesRoute
import com.xfin.feature.library.ShowsRoute
import com.xfin.feature.settings.SettingsRoute

/** The tabs, in UHF's order: Home, Live TV, Series, Movies (Settings on TV and tablets). */
enum class TopLevel(val route: Any, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(LibraryRoute, "Home", Icons.Outlined.Home, Icons.Rounded.Home),
    LIVE_TV(LiveTvRoute, "Live TV", Icons.Outlined.LiveTv, Icons.Rounded.LiveTv),
    SERIES(ShowsRoute, "Series", Icons.Outlined.Tv, Icons.Rounded.Tv),
    MOVIES(MoviesRoute, "Movies", Icons.Outlined.Movie, Icons.Rounded.Movie),
    SETTINGS(SettingsRoute, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

/** Top-level chrome: bottom bar on phones, rail on tablets, slim focus rail on TV. */
@Composable
fun AppShell(current: TopLevel, tabs: List<TopLevel>, onNavigate: (TopLevel) -> Unit, content: @Composable () -> Unit) {
    when {
        LocalIsTv.current -> TvShell(current, tabs, onNavigate, content)
        windowWidthDp() >= 600 -> RailShell(current, tabs, onNavigate, content)
        // A phone bar fits five tabs; past that, Settings lives behind the gear on Home and Live TV.
        else -> PhoneShell(current, if (tabs.size > 5) tabs - TopLevel.SETTINGS else tabs, onNavigate, content)
    }
}

@Composable
private fun PhoneShell(current: TopLevel, tabs: List<TopLevel>, onNavigate: (TopLevel) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                tabs.forEach { item ->
                    NavigationBarItem(
                        selected = item == current,
                        onClick = { onNavigate(item) },
                        icon = { Icon(if (item == current) item.selectedIcon else item.icon, null) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun RailShell(current: TopLevel, tabs: List<TopLevel>, onNavigate: (TopLevel) -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                tabs.forEach { item ->
                    NavigationRailItem(
                        selected = item == current,
                        onClick = { onNavigate(item) },
                        icon = { Icon(if (item == current) item.selectedIcon else item.icon, null) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun TvShell(current: TopLevel, tabs: List<TopLevel>, onNavigate: (TopLevel) -> Unit, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(88.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            tabs.forEach { item ->
                val selected = item == current
                FocusSurface(
                    onClick = { onNavigate(item) },
                    selected = selected,
                    focusedScale = 1.1f,
                ) {
                    Icon(
                        if (selected) item.selectedIcon else item.icon,
                        contentDescription = item.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp).size(28.dp),
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}
