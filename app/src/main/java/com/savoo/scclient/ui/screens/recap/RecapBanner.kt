package com.savoo.scclient.ui.screens.recap

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savoo.scclient.R
import com.savoo.scclient.data.repository.RecapStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecapDebugState {
    private val _forceBanner = MutableStateFlow(false)
    val forceBanner = _forceBanner.asStateFlow()

    fun setForceBanner(value: Boolean) {
        _forceBanner.value = value
    }
}

@Composable
fun RecapBanner(
    stats: RecapStats,
    onOpen: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
) {
    val accent = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "recapBannerPress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(32.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen),
    ) {
        RecapBackdrop(accent = accent, slide = 0, modifier = Modifier.matchParentSize())
        Column(Modifier.padding(start = 22.dp, end = 8.dp, top = 8.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_recap_kicker, stats.year),
                    color = Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f).padding(top = 14.dp, bottom = if (onDismiss == null) 14.dp else 0.dp),
                )
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.home_recap_dismiss),
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Text(
                stats.year.toString(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 56.sp,
                lineHeight = 58.sp,
                letterSpacing = (-2).sp,
            )
            Text(
                stringResource(R.string.home_recap_desc),
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 14.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.recap_local_note),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 14.dp),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onOpen,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_recap_open), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
