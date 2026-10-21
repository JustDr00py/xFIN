package com.xfin.feature.onboarding

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xfin.core.designsystem.component.LocalIsTv
import com.xfin.core.designsystem.component.MinimalScrollToFocus
import com.xfin.core.designsystem.component.PasswordVisibilityButton
import com.xfin.core.designsystem.component.rememberTvTextField
import com.xfin.core.designsystem.component.tvTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddJellyfinScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddJellyfinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Jellyfin") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            MinimalScrollToFocus {
                Column(
                    modifier = Modifier
                        .widthIn(max = if (LocalIsTv.current) 720.dp else 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AnimatedContent(state.server, contentKey = { it?.url }, label = "jellyfinStep") { server ->
                        if (server == null) ServerStep(state, viewModel) else SignInStep(state, viewModel)
                    }
                    state.error?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerStep(state: AddJellyfinState, viewModel: AddJellyfinViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Enter your server's address. You can paste the URL from your browser.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val tvField1 = rememberTvTextField()
        OutlinedTextField(
            value = state.address,
            onValueChange = viewModel::setAddress,
            label = { Text("Server address") },
            placeholder = { Text("https://jellyfin.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { viewModel.connect() }),
            readOnly = tvField1.readOnly,
            modifier = Modifier.fillMaxWidth().tvTextField(tvField1),
        )
        Button(
            onClick = viewModel::connect,
            enabled = state.address.isNotBlank() && !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun SignInStep(state: AddJellyfinState, viewModel: AddJellyfinViewModel) {
    val server = state.server ?: return
    var showPassword by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Jellyfin ${server.version} · ${server.url.substringAfter("://")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = viewModel::changeServer) { Text("Change") }
        }

        val code = state.quickConnect
        if (code != null) {
            QuickConnectPanel(code.code, onCancel = viewModel::cancelQuickConnect)
            return@Column
        }

        val tvField2 = rememberTvTextField()
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::setUsername,
            label = { Text("Username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
            readOnly = tvField2.readOnly,
            modifier = Modifier.fillMaxWidth().tvTextField(tvField2),
        )
        val tvField3 = rememberTvTextField()
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::setPassword,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.signIn() }),
            trailingIcon = { PasswordVisibilityButton(showPassword, { showPassword = !showPassword }, tvField3) },
            readOnly = tvField3.readOnly,
            modifier = Modifier.fillMaxWidth().tvTextField(tvField3),
        )
        Button(
            onClick = viewModel::signIn,
            enabled = state.username.isNotBlank() && !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Sign in")
            }
        }
        if (server.quickConnectEnabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text("  or  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.weight(1f))
            }
            OutlinedButton(onClick = viewModel::startQuickConnect, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Use Quick Connect")
            }
        }
    }
}

@Composable
private fun QuickConnectPanel(code: String, onCancel: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Text(
            "On a device that's already signed in, open Jellyfin › Settings › Quick Connect and enter:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            code,
            fontSize = 44.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 8.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            Text("Waiting for approval…", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel) { Text("Use a password instead") }
    }
}
