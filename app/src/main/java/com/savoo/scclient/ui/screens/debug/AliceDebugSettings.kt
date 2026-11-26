package com.savoo.scclient.ui.screens.debug

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.savoo.scclient.R
import com.savoo.scclient.alice.AliceBle

@Composable
internal fun AliceDebugSettings(ble: AliceBle) {
    val enabled by ble.enabled.collectAsState()
    val key by ble.key.collectAsState()
    var hasPermissions by remember { mutableStateOf(ble.hasPermissions()) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermissions = ble.hasPermissions()
    }
    var code by remember { mutableStateOf("") }
    var invalidCode by remember { mutableStateOf(false) }

    LifecycleResumeEffect(ble) {
        hasPermissions = ble.hasPermissions()
        onPauseOrDispose {}
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.alice_ble_enable),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(checked = enabled, onCheckedChange = { active ->
            ble.setEnabled(active)
            if (active && !ble.hasPermissions()) permissions.launch(ble.requiredPermissions())
            if (!active) {
                code = ""
                invalidCode = false
            }
        })
    }
    if (enabled) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.alice_ble_pair_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasPermissions) {
                TextButton(onClick = { permissions.launch(ble.requiredPermissions()) }) {
                    Text(stringResource(R.string.alice_ble_grant_permission))
                }
            }
            if (key != null) Text(stringResource(R.string.alice_ble_paired))
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it
                    invalidCode = false
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(stringResource(R.string.alice_ble_key)) },
                isError = invalidCode,
                supportingText = if (invalidCode) {
                    { Text(stringResource(R.string.alice_ble_key_error)) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(enabled = code.isNotBlank(), onClick = {
                invalidCode = !ble.saveKey(code)
                if (!invalidCode) code = ""
            }) {
                Text(stringResource(R.string.debug_menu_client_id_save))
            }
        }
    }
}
