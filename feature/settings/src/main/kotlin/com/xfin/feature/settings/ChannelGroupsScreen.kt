package com.xfin.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelGroupsScreen(onBack: () -> Unit, viewModel: ChannelGroupsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val change by viewModel.lastChange.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val q = state.query

    LaunchedEffect(change) {
        val c = change ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(c.message, actionLabel = "Undo", duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.dismissChange()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Channel groups")
                        if (state.loaded) {
                            Text(
                                "%,d of %,d shown".format(state.total - state.hiddenTotal, state.total),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = q.text,
                        onValueChange = viewModel::setText,
                        placeholder = { Text("Search groups, e.g. US, CA") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (q.text.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setText("") }) { Icon(Icons.Rounded.Close, "Clear search") }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        FilterChip(
                            selected = q.startsWith,
                            onClick = { viewModel.setStartsWith(!q.startsWith) },
                            label = { Text("Starts with") },
                        )
                        GroupShowing.entries.forEach { showing ->
                            FilterChip(
                                selected = q.showing == showing,
                                onClick = { viewModel.setShowing(showing) },
                                label = {
                                    Text(
                                        when (showing) {
                                            GroupShowing.ALL -> "All"
                                            GroupShowing.SHOWN -> "Shown"
                                            GroupShowing.HIDDEN -> "Hidden"
                                        },
                                    )
                                },
                            )
                        }
                    }
                    Text(
                        "Commas mean “or”: US, CA, UK",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BulkActions(state, viewModel)
                }
                if (state.loaded && state.groups.isEmpty()) {
                    item {
                        Text(
                            "No groups match.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(state.groups, key = { it.name }) { group ->
                    ListItem(
                        headlineContent = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("%,d channels".format(group.channelCount)) },
                        trailingContent = { Checkbox(checked = !group.hidden, onCheckedChange = { viewModel.toggle(group) }) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable { viewModel.toggle(group) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BulkActions(state: ChannelGroupsUiState, viewModel: ChannelGroupsViewModel) {
    val n = state.groups.size
    val filtering = state.query.isFiltering
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    ) {
        OutlinedButton(onClick = { viewModel.setListedHidden(false) }, enabled = n > 0) {
            Icon(Icons.Rounded.Visibility, null, Modifier.padding(end = 6.dp))
            Text(if (filtering) "Show %,d".format(n) else "Show all", maxLines = 1, softWrap = false)
        }
        OutlinedButton(onClick = { viewModel.setListedHidden(true) }, enabled = n > 0) {
            Icon(Icons.Rounded.VisibilityOff, null, Modifier.padding(end = 6.dp))
            Text(if (filtering) "Hide %,d".format(n) else "Hide all", maxLines = 1, softWrap = false)
        }
        if (filtering) {
            FilledTonalButton(onClick = viewModel::showOnlyListed, enabled = n > 0) {
                Icon(Icons.Rounded.FilterAlt, null, Modifier.padding(end = 6.dp))
                Text("Only these", maxLines = 1, softWrap = false)
            }
        }
    }
}
