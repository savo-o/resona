package com.savoo.scclient.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.data.model.UnavailableReason

@Composable
fun TrackUnavailableDialog(
    trackTitle: String,
    reason: UnavailableReason,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.track_unavailable)) },
        text = {
            Column {
                Text(
                    stringResource(
                        when (reason) {
                            UnavailableReason.DELETED -> R.string.track_skipped_deleted
                            UnavailableReason.DRM -> R.string.track_skipped_drm
                        },
                        trackTitle,
                    )
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.track_unavailable_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.track_dialog_ok))
            }
        },
    )
}
