package com.savoo.scclient.ui.screens.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.savoo.scclient.R
import com.savoo.scclient.debug.CrashReporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CrashReportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _report = MutableStateFlow<String?>(null)
    val report = _report.asStateFlow()

    private var checked = false

    fun checkIfNeeded() {
        if (checked) return
        checked = true
        _report.value = CrashReporter.consumePendingReport()
    }

    fun dismiss() {
        _report.value = null
    }

    fun copyToClipboard() {
        val text = _report.value ?: return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Resona crash report", text))
    }
}

@Composable
fun CrashReportHost(viewModel: CrashReportViewModel = hiltViewModel()) {
    val report by viewModel.report.collectAsState()

    LaunchedEffect(Unit) { viewModel.checkIfNeeded() }

    report?.let {
        CrashReportDialog(
            report = it,
            onCopy = { viewModel.copyToClipboard() },
            onDismiss = { viewModel.dismiss() },
        )
    }
}

@Composable
private fun CrashReportDialog(report: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.crash_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.crash_dialog_message), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.heightIn(max = 240.dp),
                ) {
                    SelectionContainer {
                        Text(
                            report,
                            modifier = Modifier.verticalScroll(rememberScrollState()).padding(10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(); onDismiss() }) {
                Text(stringResource(R.string.debug_menu_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.crash_dialog_dismiss))
            }
        },
    )
}
