package com.savoo.scclient.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.data.model.UpdateChannel
import com.savoo.scclient.data.repository.UpdateCheckResult

/** Shown for both the automatic and manual update-check flows - the wording differs by channel,
 * per-channel copy is intentionally kept out of [com.savoo.scclient.data.repository.UpdateRepository] since it's presentation, not data. */
@Composable
fun UpdateAvailableDialog(
    result: UpdateCheckResult.Available,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (result.channel == UpdateChannel.CANARY) {
                        R.string.update_dialog_title_canary
                    } else {
                        R.string.update_dialog_title_release
                    }
                )
            )
        },
        text = {
            if (result.channel == UpdateChannel.CANARY) {
                Text(stringResource(R.string.update_dialog_message_canary))
            } else {
                Text(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    text = stringResource(
                        R.string.update_dialog_message_release,
                        result.versionLabel,
                        result.changelog?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.update_dialog_no_changelog),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.htmlUrl)))
                onDismiss()
            }) {
                Text(stringResource(R.string.update_dialog_open_github))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_dialog_later))
            }
        },
    )
}
