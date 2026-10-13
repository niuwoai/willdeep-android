package com.willdeep.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.willdeep.android.R
import com.willdeep.android.mobile.COMPUTER_PLATFORMS
import com.willdeep.android.mobile.MAX_COMPUTER_ALIAS_LENGTH

@Composable
internal fun remoteComputerIdentityText(computer: RemoteMacSummary): String {
    return stringResource(
        R.string.remote_computer_identity,
        computerPlatformLabel(computer.computerPlatform),
        computer.originalName,
        computer.id.take(6),
    )
}

@Composable
private fun computerPlatformLabel(platform: String): String = stringResource(
    when (platform) {
        "macos" -> R.string.remote_computer_macos
        "linux" -> R.string.remote_computer_linux
        "windows" -> R.string.remote_computer_windows
        else -> R.string.remote_computer_unknown
    },
)

@Composable
internal fun RemoteComputerIdentityDialog(
    computer: RemoteMacSummary,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var alias by rememberSaveable(computer.id) { mutableStateOf(computer.alias) }
    var platform by rememberSaveable(computer.id) { mutableStateOf(computer.computerPlatform) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.remote_computer_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(remoteComputerIdentityText(computer), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it.take(MAX_COMPUTER_ALIAS_LENGTH) },
                    label = { Text(stringResource(R.string.remote_computer_alias)) },
                    placeholder = { Text(stringResource(R.string.remote_computer_alias_example)) },
                    supportingText = { Text(stringResource(R.string.remote_computer_local_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.remote_computer_platform))
                COMPUTER_PLATFORMS.forEach { option ->
                    FilterChip(
                        selected = platform == option,
                        onClick = { platform = option },
                        label = { Text(computerPlatformLabel(option)) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(alias.trim(), platform) }) {
                Text(stringResource(R.string.remote_computer_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.remote_computer_cancel))
            }
        },
    )
}
