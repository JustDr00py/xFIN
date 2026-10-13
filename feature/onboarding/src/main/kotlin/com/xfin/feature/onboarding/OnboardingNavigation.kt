package com.xfin.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object WelcomeRoute

@Serializable
data class AddSourceRoute(val kind: String)

enum class SourceKind { M3U, XTREAM }

fun NavGraphBuilder.onboardingScreens(
    onAddSource: (SourceKind) -> Unit,
    onSourceAdded: () -> Unit,
    onBack: () -> Unit,
) {
    composable<WelcomeRoute> {
        WelcomeScreen(onAddSource = onAddSource)
    }
    composable<AddSourceRoute> {
        AddSourceScreen(onDone = onSourceAdded, onBack = onBack)
    }
}
