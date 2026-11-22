package com.savoo.scclient.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R

@Composable
internal fun ClientIdActionsDialog(
    clientId: String,
    busy: Boolean,
    onRefresh: () -> Unit,
    onClear: () -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    val value = input.trim()
    val valid = value.matches(Regex("[a-zA-Z0-9]{32}"))
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_client_id_advanced)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.settings_client_id_current), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            clientId.ifBlank { stringResource(R.string.settings_client_id_empty) },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(
                        enabled = clientId.isNotBlank(),
                        onClick = { clipboard.setText(AnnotatedString(clientId)) },
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.settings_client_id_copy))
                    }
                }
                if (busy) CircularProgressIndicator()
                TextButton(onClick = onRefresh, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_client_id))
                }
                TextButton(onClick = onClear, enabled = !busy && clientId.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_client_id_delete))
                }
                Text(
                    stringResource(R.string.settings_client_id_delete_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.settings_client_id_manual)) },
                    singleLine = true,
                    enabled = !busy,
                    isError = value.isNotEmpty() && !valid,
                    supportingText = { Text(stringResource(R.string.settings_client_id_format)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = { onSave(value); input = "" },
                    enabled = !busy && valid,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_client_id_save))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
