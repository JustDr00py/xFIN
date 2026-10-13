package com.xfin.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xfin.core.model.ChannelFilter
import com.xfin.core.model.encode
import com.xfin.feature.guide.GuideRoute
import com.xfin.feature.guide.guideScreen
import com.xfin.feature.livetv.LiveTvRoute
import com.xfin.feature.library.ItemRoute
import com.xfin.feature.library.LibraryRoute
import com.xfin.feature.library.MoviesRoute
import com.xfin.feature.library.SearchRoute
import com.xfin.feature.library.SearchScope
import com.xfin.feature.library.ShowsRoute
import com.xfin.feature.library.libraryScreens
import com.xfin.feature.onboarding.AddJellyfinRoute
import com.xfin.feature.onboarding.SourceKind
import com.xfin.feature.player.VodPlayerRoute
import com.xfin.feature.player.MultiviewRoute
import com.xfin.app.SourceSummary
import com.xfin.feature.livetv.liveTvScreen
import com.xfin.feature.onboarding.AddSourceRoute
import com.xfin.feature.onboarding.WelcomeRoute
import com.xfin.feature.onboarding.onboardingScreens
import com.xfin.feature.player.PlayerRoute
import com.xfin.feature.player.playerScreen
import com.xfin.feature.settings.SettingsRoute
import com.xfin.feature.settings.settingsScreen

@Composable
fun XfinNavHost(sources: SourceSummary, navController: NavHostController = rememberNavController()) {
    val hasSources = sources.any
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    // If every source gets removed, go back to onboarding.
    LaunchedEffect(hasSources) {
        val onboarding = destination?.hasRoute<WelcomeRoute>() == true || destination?.hasRoute<AddSourceRoute>() == true ||
            destination?.hasRoute<AddJellyfinRoute>() == true
        if (!hasSources && destination != null && !onboarding) {
            navController.navigate(WelcomeRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    // Only show tabs for the kinds of sources the user has.
    val tabs = buildList {
        if (sources.iptv || !sources.library) addAll(listOf(TopLevel.LIVE_TV, TopLevel.GUIDE))
        if (sources.library) addAll(listOf(TopLevel.LIBRARY, TopLevel.MOVIES, TopLevel.SHOWS))
        add(TopLevel.SETTINGS)
    }
    val shell: @Composable (@Composable () -> Unit) -> Unit = { content ->
        AppShell(
            current = when {
                destination?.hasRoute<SettingsRoute>() == true -> TopLevel.SETTINGS
                destination?.hasRoute<GuideRoute>() == true -> TopLevel.GUIDE
                destination?.hasRoute<LibraryRoute>() == true -> TopLevel.LIBRARY
                destination?.hasRoute<MoviesRoute>() == true -> TopLevel.MOVIES
                destination?.hasRoute<ShowsRoute>() == true -> TopLevel.SHOWS
                else -> TopLevel.LIVE_TV
            },
            tabs = tabs,
            onNavigate = { target ->
                navController.navigate(target.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            content = content,
        )
    }

    // Decided once: changing NavHost's start destination later would reset the whole back stack
    // (e.g. the moment onboarding inserts its first source row).
    val startDestination: Any = remember {
        when {
            sources.iptv -> LiveTvRoute
            sources.library -> LibraryRoute
            else -> WelcomeRoute
        }
    }

    val openSettings: () -> Unit = {
        navController.navigate(SettingsRoute) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        onboardingScreens(
            onAddSource = { kind ->
                if (kind == SourceKind.JELLYFIN) navController.navigate(AddJellyfinRoute) else navController.navigate(AddSourceRoute(kind.name))
            },
            onSourceAdded = {
                navController.navigate(LiveTvRoute) { popUpTo(0) { inclusive = true } }
            },
            onJellyfinAdded = {
                navController.navigate(LibraryRoute) { popUpTo(0) { inclusive = true } }
            },
            onBack = { navController.popBackStack() },
        )
        liveTvScreen(
            onPlay = { id, filter -> navController.navigate(PlayerRoute(id, filter.encode())) },
            onSearch = { navController.navigate(SearchRoute(SearchScope.LIVE.name)) },
            onOpenSettings = openSettings,
            shell = shell,
        )
        guideScreen(
            onPlay = { id, filter -> navController.navigate(PlayerRoute(id, filter.encode())) },
            onOpenSettings = openSettings,
            shell = shell,
        )
        settingsScreen(
            onAddSource = { navController.navigate(WelcomeRoute) },
            onEditSource = { source -> navController.navigate(AddSourceRoute(source.type.name, source.id)) },
            shell = shell,
        )
        libraryScreens(
            onOpenItem = { sourceId, itemId -> navController.navigate(ItemRoute(sourceId, itemId)) },
            onOpenLibrary = { navController.navigate(it) },
            onPlay = { sourceId, itemId, startMs, mediaSourceId ->
                navController.navigate(VodPlayerRoute(sourceId, itemId, startMs, mediaSourceId))
            },
            onConnect = { navController.navigate(AddJellyfinRoute) },
            onSearch = { navController.navigate(SearchRoute()) },
            onPlayChannel = { channelId -> navController.navigate(PlayerRoute(channelId, ChannelFilter.All.encode())) },
            onOpenSettings = openSettings,
            onBack = { navController.popBackStack() },
            shell = shell,
        )
        playerScreen(
            onBack = { navController.popBackStack() },
            onPlayNext = { next ->
                navController.navigate(next) { popUpTo<VodPlayerRoute> { inclusive = true } }
            },
            // Multiview replaces the single player (and vice versa) so streams are never doubled up.
            onOpenMultiview = { route ->
                navController.navigate(route) { popUpTo<PlayerRoute> { inclusive = true } }
            },
            onOpenChannel = { route ->
                navController.navigate(route) { popUpTo<MultiviewRoute> { inclusive = true } }
            },
        )
    }
}
