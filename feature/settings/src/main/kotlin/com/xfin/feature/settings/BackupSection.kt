package com.xfin.feature.settings

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.xfin.core.designsystem.component.rememberTvTextField
import com.xfin.core.designsystem.component.tvTextField
import com.xfin.data.sources.backup.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val MIN_PASSWORD = 6

/** "Back up" and "Restore" rows, with their password prompts and result dialog. */
@Composable
internal fun BackupSection(viewModel: SettingsViewModel) {
    val state by viewModel.backupState.collectAsStateWithLifecycle()
    var askBackupPassword by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf<String?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var pickerMissing by remember { mutableStateOf(false) }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = backupPassword
        backupPassword = null
        if (uri != null && password != null) viewModel.backup(uri, password)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }
    val working = state as? BackupUiState.Working

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BackupRow(
            icon = Icons.Rounded.SaveAlt,
            title = "Back up to a file",
            subtitle = "Sources and logins, favorites, history, hidden groups and watch progress, protected by a password",
            enabled = working == null,
            onClick = { askBackupPassword = true },
        )
        BackupRow(
            icon = Icons.Rounded.Restore,
            title = "Restore from a file",
            subtitle = working?.message ?: "Adds what's in the backup; nothing on this device is removed",
            enabled = working == null,
            busy = working != null,
            onClick = {
                try {
                    openFile.launch(arrayOf("*/*"))
                } catch (e: ActivityNotFoundException) {
                    pickerMissing = true
                }
            },
        )
    }

    if (askBackupPassword) {
        PasswordDialog(
            title = "Choose a backup password",
            message = "The backup holds your provider passwords, so it's encrypted. You'll need this password to restore it.",
            confirm = true,
            onDismiss = { askBackupPassword = false },
            onConfirm = { password ->
                askBackupPassword = false
                backupPassword = password
                try {
                    createFile.launch("xfin-backup-${LocalDate.now()}.xfin")
                } catch (e: ActivityNotFoundException) {
                    backupPassword = null
                    pickerMissing = true
                }
            },
        )
    }

    restoreUri?.let { uri ->
        PasswordDialog(
            title = "Backup password",
            message = "Enter the password you chose when you made this backup.",
            confirm = false,
            onDismiss = { restoreUri = null },
            onConfirm = { password ->
                restoreUri = null
                viewModel.restore(uri, password)
            },
        )
    }

    when (val s = state) {
        is BackupUiState.Done -> ResultDialog("Done", s.message, viewModel::dismissBackupResult)
        is BackupUiState.Failed -> ResultDialog("Couldn't finish", s.message, viewModel::dismissBackupResult)
        else -> Unit
    }
    if (pickerMissing) {
        ResultDialog("No file picker", "This device has no app for choosing files.") { pickerMissing = false }
    }
}

@Composable
private fun BackupRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    busy: Boolean = false,
) {
    ListItem(
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = if (busy) {
            { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    message: String,
    confirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val error = when {
        !confirm -> null
        password.isNotEmpty() && password.length < MIN_PASSWORD -> "Use at least $MIN_PASSWORD characters"
        repeat.isNotEmpty() && repeat != password -> "The passwords don't match"
        else -> null
    }
    val valid = if (confirm) password.length >= MIN_PASSWORD && repeat == password else password.isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PasswordField("Password", password) { password = it }
                if (confirm) PasswordField("Repeat password", repeat) { repeat = it }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }, enabled = valid) { Text(if (confirm) "Choose file" else "Restore") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    val tvField = rememberTvTextField()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        readOnly = tvField.readOnly,
        modifier = Modifier.fillMaxWidth().tvTextField(tvField),
    )
}

@Composable
private fun ResultDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Restore on its own, for the welcome screen of a fresh install (Settings isn't reachable yet). */
@HiltViewModel
class RestoreViewModel @Inject constructor(private val backups: BackupRepository) : ViewModel() {
    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun restore(uri: Uri, password: String) {
        if (_state.value is BackupUiState.Working) return
        _state.value = BackupUiState.Working("Restoring…")
        viewModelScope.launch {
            _state.value = try {
                val result = backups.restore(uri, password)
                BackupUiState.Done(
                    "Restored ${result.sourcesAdded} source${if (result.sourcesAdded == 1) "" else "s"}. " +
                        "Channels and guides are loading in the background.",
                )
            } catch (e: Exception) {
                BackupUiState.Failed(e.message ?: "Something went wrong")
            }
        }
    }

    fun dismiss() {
        _state.value = BackupUiState.Idle
    }
}

/** "Restore from a backup": pick the file, enter its password, then [onRestored] once it's in. */
@Composable
fun RestoreFromBackupButton(onRestored: () -> Unit, modifier: Modifier = Modifier, viewModel: RestoreViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickerMissing by remember { mutableStateOf(false) }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pickedUri = uri }

    TextButton(
        onClick = {
            try {
                openFile.launch(arrayOf("*/*"))
            } catch (e: ActivityNotFoundException) {
                pickerMissing = true
            }
        },
        enabled = state !is BackupUiState.Working,
        modifier = modifier,
    ) {
        if (state is BackupUiState.Working) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Restoring…")
        } else {
            Icon(Icons.Rounded.Restore, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Restore from a backup")
        }
    }

    pickedUri?.let { uri ->
        PasswordDialog(
            title = "Backup password",
            message = "Enter the password you chose when you made this backup.",
            confirm = false,
            onDismiss = { pickedUri = null },
            onConfirm = { password ->
                pickedUri = null
                viewModel.restore(uri, password)
            },
        )
    }
    when (val s = state) {
        is BackupUiState.Done -> ResultDialog("Done", s.message) {
            viewModel.dismiss()
            onRestored()
        }
        is BackupUiState.Failed -> ResultDialog("Couldn't finish", s.message, viewModel::dismiss)
        else -> Unit
    }
    if (pickerMissing) {
        ResultDialog("No file picker", "This device has no app for choosing files.") { pickerMissing = false }
    }
}
