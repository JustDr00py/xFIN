package com.xfin.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data object WelcomeRoute

@Serializable
data class AddSourceRoute(val kind: String, val sourceId: Long = 0) {
    val isEdit: Boolean get() = sourceId > 0
}

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
    composable<AddSourceRoute> { entry ->
        val editing = entry.toRoute<AddSourceRoute>().isEdit
        AddSourceScreen(onDone = if (editing) onBack else onSourceAdded, onBack = onBack)
    }
    composable<AddJellyfinRoute> {
        AddJellyfinScreen(onDone = onJellyfinAdded, onBack = onBack)
    }
}
