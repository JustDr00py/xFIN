package com.xfin.feature.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.xfin.core.model.Source
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsScreen(
    onAddSource: () -> Unit,
    onEditSource: (Source) -> Unit,
    shell: @Composable (content: @Composable () -> Unit) -> Unit,
) {
    composable<SettingsRoute> {
        shell { SettingsScreen(onAddSource = onAddSource, onEditSource = onEditSource) }
    }
}
