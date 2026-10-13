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
import com.xfin.core.model.encode
import com.xfin.feature.guide.GuideRoute
import com.xfin.feature.guide.guideScreen
import com.xfin.feature.livetv.LiveTvRoute
import com.xfin.feature.livetv.liveTvScreen
import com.xfin.feature.onboarding.AddSourceRoute
import com.xfin.feature.onboarding.WelcomeRoute
import com.xfin.feature.onboarding.onboardingScreens
import com.xfin.feature.player.PlayerRoute
import com.xfin.feature.player.playerScreen
import com.xfin.feature.settings.SettingsRoute
import com.xfin.feature.settings.settingsScreen

@Composable
fun XfinNavHost(hasSources: Boolean, navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination

    // If every source gets removed, go back to onboarding.
    LaunchedEffect(hasSources) {
        val onboarding = destination?.hasRoute<WelcomeRoute>() == true || destination?.hasRoute<AddSourceRoute>() == true
        if (!hasSources && destination != null && !onboarding) {
            navController.navigate(WelcomeRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    val shell: @Composable (@Composable () -> Unit) -> Unit = { content ->
        AppShell(
            current = when {
                destination?.hasRoute<SettingsRoute>() == true -> TopLevel.SETTINGS
                destination?.hasRoute<GuideRoute>() == true -> TopLevel.GUIDE
                else -> TopLevel.LIVE_TV
            },
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
    val startDestination: Any = remember { if (hasSources) LiveTvRoute else WelcomeRoute }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        onboardingScreens(
            onAddSource = { kind -> navController.navigate(AddSourceRoute(kind.name)) },
            onSourceAdded = {
                navController.navigate(LiveTvRoute) { popUpTo(0) { inclusive = true } }
            },
            onBack = { navController.popBackStack() },
        )
        liveTvScreen(
            onPlay = { id, filter -> navController.navigate(PlayerRoute(id, filter.encode())) },
            shell = shell,
        )
        guideScreen(
            onPlay = { id, filter -> navController.navigate(PlayerRoute(id, filter.encode())) },
            onOpenSettings = {
                navController.navigate(SettingsRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            },
            shell = shell,
        )
        settingsScreen(
            onAddSource = { navController.navigate(WelcomeRoute) },
            shell = shell,
        )
        playerScreen(onBack = { navController.popBackStack() })
    }
}
