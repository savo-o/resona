package com.savoo.scclient.ui.screens.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.savoo.scclient.R
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.player.PlayerController
import kotlinx.coroutines.launch

@UnstableApi
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramMusicSheet(controller: PlayerController, track: Track, onDismiss: () -> Unit) {
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var complete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = { if (!sending) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.telegram_music_profile), style = MaterialTheme.typography.headlineSmall)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium)
                    Text(track.user.username, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.telegram_music_profile_description))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (complete) Text(stringResource(R.string.telegram_music_done), color = MaterialTheme.colorScheme.primary)
            Button(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !sending,
                onClick = {
                    if (complete) onDismiss()
                    else {
                        sending = true
                        error = null
                        scope.launch {
                            try {
                                controller.saveToTelegram(track)
                                complete = true
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                sending = false
                            }
                        }
                    }
                },
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else Text(stringResource(if (complete) R.string.telegram_music_close else R.string.telegram_music_send))
            }
        }
    }
}
