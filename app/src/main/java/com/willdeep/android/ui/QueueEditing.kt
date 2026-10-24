package com.willdeep.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.willdeep.android.R
import com.willdeep.android.mobile.GatewayEnvelope
import com.willdeep.android.mobile.GatewayQueuedMessage
import org.json.JSONObject

internal const val QUEUE_EDIT_ACK_TIMEOUT_MILLIS = 20_000L

data class QueueEditState(
    val message: GatewayQueuedMessage,
    val text: String,
    val commandId: String? = null,
    val error: String? = null,
)

internal fun QueueEditState?.afterReply(id: String?, error: String? = null): QueueEditState? {
    if (id == null || this?.commandId != id) return this
    return if (error == null) null else copy(commandId = null, error = error)
}

internal fun MobileGatewayUiState.queueCommandFailed(id: String?, message: String): MobileGatewayUiState = copy(
    queueEdit = queueEdit.afterReply(id, message),
    errorMessage = message,
    commandStatuses = commandStatuses.markCommandFailed(id, message),
)

internal fun QueueEditState.submission(): GatewayEnvelope? {
    val original = message.text ?: return null
    val session = message.sessionId ?: return null
    if (commandId != null || (text.isBlank() && message.imageCount == 0 && message.textAttachmentCount == 0)) return null
    return GatewayEnvelope(
        type = "queue.update", sessionId = session,
        payload = JSONObject().put("action", "edit").put("message_id", message.id)
            .put("text", text).put("expected_text", original),
    )
}

@Composable
internal fun QueueEditDialog(
    edit: QueueEditState,
    connected: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pending = edit.commandId != null
    AlertDialog(
        onDismissRequest = { if (!pending) onDismiss() },
        title = { Text(stringResource(R.string.queue_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = edit.text, onValueChange = onChange,
                    enabled = !pending, minLines = 3, maxLines = 10,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.queue_edit_text)) },
                )
                Text(stringResource(R.string.queue_edit_preserves_attachments))
                edit.error?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = connected && edit.submission() != null) {
                Text(stringResource(if (pending) R.string.queue_edit_saving else R.string.queue_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !pending) { Text(stringResource(R.string.queue_edit_cancel)) }
        },
    )
}
