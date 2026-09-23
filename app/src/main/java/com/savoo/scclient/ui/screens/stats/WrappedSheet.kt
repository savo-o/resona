package com.savoo.scclient.ui.screens.stats

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.data.local.ArtistListenStat
import com.savoo.scclient.data.local.TrackListenStat
import com.savoo.scclient.ui.components.captureInto
import com.savoo.scclient.ui.components.rememberCaptureLayer
import com.savoo.scclient.ui.components.shareImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WrappedSheet(
    totalHours: Int,
    totalPlays: Int,
    topGenre: String?,
    topArtists: List<ArtistListenStat>,
    topTracks: List<TrackListenStat>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberCaptureLayer()
    var isSharing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.captureInto(graphicsLayer)) {
                WrappedShareCard(
                    totalHours = totalHours,
                    totalPlays = totalPlays,
                    topGenre = topGenre,
                    topArtists = topArtists,
                    topTracks = topTracks,
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                enabled = !isSharing,
                onClick = {
                    isSharing = true
                    scope.launch {
                        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                        shareImage(context, bitmap, "resona_wrapped")
                        isSharing = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                if (isSharing) {
                    LoadingIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.wrapped_share))
                }
            }
        }
    }
}
