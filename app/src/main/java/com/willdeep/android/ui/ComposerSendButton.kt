package com.willdeep.android.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.willdeep.android.R

@Composable
internal fun ComposerSendButton(canSend: Boolean, isResponding: Boolean, onSend: () -> Unit) {
    FilledIconButton(
        onClick = onSend,
        enabled = canSend,
        shape = CircleShape,
        modifier = Modifier.size(46.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.outline,
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_send),
            contentDescription = stringResource(
                if (isResponding) R.string.composer_queue_button else R.string.send_button,
            ),
            modifier = Modifier.size(22.dp),
        )
    }
}
