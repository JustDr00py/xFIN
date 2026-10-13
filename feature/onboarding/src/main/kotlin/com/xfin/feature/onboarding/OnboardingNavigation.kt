package com.xfin.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object WelcomeRoute

@Serializable
data class AddSourceRoute(val kind: String)

@Serializable
data object AddJellyfinRoute

enum class SourceKind { M3U, XTREAM, JELLYFIN }

fun NavGraphBuilder.onboardingScreens(
    onAddSource: (SourceKind) -> Unit,
    onSourceAdded: () -> Unit,
    onJellyfinAdded: () -> Unit,
    onBack: () -> Unit,
) {
    composable<WelcomeRoute> {
        WelcomeScreen(onAddSource = onAddSource)
    }
    composable<AddSourceRoute> {
        AddSourceScreen(onDone = onSourceAdded, onBack = onBack)
    }
    composable<AddJellyfinRoute> {
        AddJellyfinScreen(onDone = onJellyfinAdded, onBack = onBack)
    }
}
