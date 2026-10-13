package com.xfin.feature.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.model.XtreamOutput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddSourceScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddSourceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.editing -> "Edit source"
                            state.kind == SourceKind.M3U -> "Add M3U playlist"
                            else -> "Add Xtream Codes"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = if (LocalIsTv.current) 720.dp else 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (state.kind) {
                    SourceKind.M3U -> M3uForm(state, viewModel)
                    SourceKind.XTREAM -> XtreamForm(state, viewModel)
                    SourceKind.JELLYFIN -> JellyfinForm(state, viewModel)
                }

                AnimatedVisibility(state.error != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = state.canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.importing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (state.imported > 0) "Importing… ${"%,d".format(state.imported)} channels" else "Connecting…",
                        )
                    } else {
                        Text(if (state.editing) "Save" else "Add and import")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun M3uForm(state: AddSourceUiState, viewModel: AddSourceViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            viewModel.pickedFile(uri.toString(), name)
        }
    }
    var advanced by rememberSaveable { mutableStateOf(state.editing) }

    Field("Name (optional)", state.name, { viewModel.update { copy(name = it) } })
    if (state.fileName != null) {
        OutlinedTextField(
            value = state.fileName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Playlist file") },
            trailingIcon = {
                TextButton(onClick = { viewModel.update { copy(url = "", fileName = null) } }) { Text("Clear") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Field(
            label = "Playlist URL",
            value = state.url,
            onValueChange = { viewModel.update { copy(url = it) } },
            placeholder = "http://provider.tv/get.php?…",
            keyboardType = KeyboardType.Uri,
        )
        if (!LocalIsTv.current) {
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.FolderOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("Choose a file instead")
            }
        }
    }
    TextButton(onClick = { advanced = !advanced }) {
        Text("Advanced")
        Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
    }
    AnimatedVisibility(advanced) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field(
                label = "EPG / XMLTV URL (optional)",
                value = state.epgUrl,
                onValueChange = { viewModel.update { copy(epgUrl = it) } },
                placeholder = "Detected from the playlist when available",
                keyboardType = KeyboardType.Uri,
            )
            Field(
                label = "User-Agent (optional)",
                value = state.userAgent,
                onValueChange = { viewModel.update { copy(userAgent = it) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun XtreamForm(state: AddSourceUiState, viewModel: AddSourceViewModel) {
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Field("Name (optional)", state.name, { viewModel.update { copy(name = it) } })
    Field(
        label = "Server URL",
        value = state.url,
        onValueChange = { viewModel.update { copy(url = it) } },
        placeholder = "http://provider.tv:8080",
        keyboardType = KeyboardType.Uri,
    )
    Field("Username", state.username, { viewModel.update { copy(username = it) } })
    OutlinedTextField(
        value = state.password,
        onValueChange = { viewModel.update { copy(password = it) } },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (showPassword) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.editing) {
        Field(
            label = "Guide (XMLTV) URL (optional)",
            value = state.epgUrl,
            onValueChange = { viewModel.update { copy(epgUrl = it) } },
            placeholder = "Empty: use the provider's guide",
            keyboardType = KeyboardType.Uri,
        )
    }
    Text("Stream format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        XtreamOutput.entries.forEachIndexed { index, output ->
            SegmentedButton(
                selected = state.output == output,
                onClick = { viewModel.update { copy(output = output) } },
                shape = SegmentedButtonDefaults.itemShape(index, XtreamOutput.entries.size),
            ) {
                Text(if (output == XtreamOutput.TS) "MPEG-TS (recommended)" else "HLS")
            }
        }
    }
}

@Composable
private fun JellyfinForm(state: AddSourceUiState, viewModel: AddSourceViewModel) {
    Field("Name", state.name, { viewModel.update { copy(name = it) } })
    Field(
        label = "Server address",
        value = state.url,
        onValueChange = { viewModel.update { copy(url = it) } },
        placeholder = "https://jellyfin.example.com",
        keyboardType = KeyboardType.Uri,
    )
    Text(
        "Use this when your server moves to a new address. To sign in as someone else, add the server again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
}
