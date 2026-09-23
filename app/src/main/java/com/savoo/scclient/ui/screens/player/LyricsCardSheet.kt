package com.savoo.scclient.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.model.Track
import com.savoo.scclient.ui.components.captureInto
import com.savoo.scclient.ui.components.rememberCaptureLayer
import com.savoo.scclient.ui.components.shareImage
import com.savoo.scclient.ui.haptics.rememberHapticTick
import kotlinx.coroutines.launch

enum class LyricsCardStyle { ARTWORK, COLOR, DARK }

private const val MAX_CARD_LINES = 4

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsCardSheet(
    lines: List<String>,
    initialIndex: Int,
    track: Track,
    accent: Color,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticTick()
    val captureLayer = rememberCaptureLayer()
    var isSharing by remember { mutableStateOf(false) }
    val hasArtwork = !track.artworkUrl.isNullOrBlank()
    var style by rememberSaveable { mutableStateOf(if (hasArtwork) LyricsCardStyle.ARTWORK else LyricsCardStyle.COLOR) }
    val start = initialIndex.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
    var selected by remember(lines) {
        mutableStateOf(listOfNotNull((start until lines.size).firstOrNull { lines[it].isNotBlank() }))
    }
    val selectedText = selected.sorted().map { lines[it].trim() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
            ) {
                Box(Modifier.captureInto(captureLayer)) {
                    LyricsCard(
                        lines = selectedText,
                        title = track.title,
                        artist = track.user.fullName?.takeIf { it.isNotBlank() } ?: track.user.username,
                        artworkUrl = track.artworkUrl,
                        accent = accent,
                        style = style,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val styles = LyricsCardStyle.entries.filter { hasArtwork || it != LyricsCardStyle.ARTWORK }
            ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                styles.forEachIndexed { index, option ->
                    val shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        styles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    }
                    ToggleButton(
                        checked = style == option,
                        onCheckedChange = { checked -> if (checked) { haptic(); style = option } },
                        modifier = Modifier.weight(1f),
                        shapes = shapes,
                    ) {
                        Text(
                            stringResource(
                                when (option) {
                                    LyricsCardStyle.ARTWORK -> R.string.lyrics_card_style_artwork
                                    LyricsCardStyle.COLOR -> R.string.lyrics_card_style_color
                                    LyricsCardStyle.DARK -> R.string.lyrics_card_style_dark
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.lyrics_card_pick_lines, MAX_CARD_LINES),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))

            val listState = rememberLazyListState()
            LaunchedEffect(lines) { listState.scrollToItem((start - 1).coerceAtLeast(0)) }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                itemsIndexed(lines) { index, line ->
                    if (line.isBlank()) return@itemsIndexed
                    val isSelected = index in selected
                    val enabled = isSelected || selected.size < MAX_CARD_LINES
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                haptic()
                                selected = if (isSelected) selected - index else selected + index
                            }
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            line.trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                                enabled -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !isSharing && selected.isNotEmpty(),
                onClick = {
                    isSharing = true
                    scope.launch {
                        runCatching {
                            val bitmap = captureLayer.toImageBitmap().asAndroidBitmap()
                            shareImage(context, bitmap, "resona_lyrics")
                        }
                        isSharing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
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

@Composable
fun LyricsCard(
    lines: List<String>,
    title: String,
    artist: String,
    artworkUrl: String?,
    accent: Color,
    style: LyricsCardStyle,
    modifier: Modifier = Modifier,
) {
    val artwork = artworkUrl?.replace("-large", "-t500x500")
    val text = lines.joinToString("\n")
    val accentIsLight = accent.luminance() > 0.45f
    val background: Brush = when (style) {
        LyricsCardStyle.ARTWORK -> Brush.verticalGradient(listOf(Color.Black, Color.Black))
        LyricsCardStyle.COLOR -> Brush.linearGradient(listOf(accent, lerp(accent, Color.Black, 0.45f)))
        LyricsCardStyle.DARK -> Brush.linearGradient(listOf(Color(0xFF151515), Color(0xFF050505)))
    }
    val footerColor = when (style) {
        LyricsCardStyle.COLOR -> if (accentIsLight) Color.Black else Color.White
        else -> Color.White
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(background),
    ) {
        val scale = maxWidth.value / 340f
        val fontSize = lyricsCardFontSize(text.length) * scale

        if (style == LyricsCardStyle.ARTWORK && artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.15f),
                            0.55f to Color.Black.copy(alpha = 0.35f),
                            1f to Color.Black.copy(alpha = 0.8f),
                        )
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding((22 * scale).dp),
        ) {
            if (style == LyricsCardStyle.DARK) {
                Text(
                    "“",
                    color = accent,
                    fontSize = (64 * scale).sp,
                    lineHeight = (64 * scale).sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.height((40 * scale).dp),
                )
            }
            Spacer(Modifier.weight(1f))
            when (style) {
                LyricsCardStyle.ARTWORK -> HighlightedLyrics(
                    text = text,
                    textColor = Color.Black,
                    barColor = Color.White,
                    fontSize = fontSize,
                    barPadding = (6 * scale).dp,
                )
                LyricsCardStyle.COLOR -> HighlightedLyrics(
                    text = text,
                    textColor = if (accentIsLight) Color.White else lerp(accent, Color.Black, 0.7f),
                    barColor = if (accentIsLight) Color.Black else Color.White,
                    fontSize = fontSize,
                    barPadding = (6 * scale).dp,
                )
                LyricsCardStyle.DARK -> Text(
                    text,
                    color = Color.White,
                    fontSize = fontSize * 1.1f,
                    lineHeight = fontSize * 1.1f * 1.3f,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (style != LyricsCardStyle.ARTWORK) {
                    Box(
                        modifier = Modifier
                            .size((40 * scale).dp)
                            .clip(RoundedCornerShape((8 * scale).dp))
                            .background(footerColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (artwork != null) {
                            AsyncImage(
                                model = artwork,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = footerColor,
                                modifier = Modifier.size((20 * scale).dp),
                            )
                        }
                    }
                    Spacer(Modifier.width((10 * scale).dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.uppercase(),
                        color = footerColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = (12 * scale).sp,
                        letterSpacing = (1.2 * scale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "“$title”",
                        color = footerColor.copy(alpha = 0.85f),
                        fontSize = (12 * scale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width((10 * scale).dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = footerColor.copy(alpha = 0.7f),
                        modifier = Modifier.size((13 * scale).dp),
                    )
                    Spacer(Modifier.width((4 * scale).dp))
                    Text(
                        stringResource(R.string.app_name),
                        color = footerColor.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (11 * scale).sp,
                    )
                }
            }
        }
    }
}

private fun lyricsCardFontSize(length: Int): TextUnit = when {
    length <= 40 -> 24.sp
    length <= 80 -> 21.sp
    length <= 140 -> 18.sp
    else -> 15.sp
}

@Composable
private fun HighlightedLyrics(
    text: String,
    textColor: Color,
    barColor: Color,
    fontSize: TextUnit,
    barPadding: Dp,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        color = textColor,
        fontSize = fontSize,
        lineHeight = fontSize * 1.55f,
        fontWeight = FontWeight.ExtraBold,
        style = MaterialTheme.typography.titleLarge,
        onTextLayout = { layout = it },
        modifier = Modifier
            .drawBehind {
                val result = layout ?: return@drawBehind
                val pad = barPadding.toPx()
                for (line in 0 until result.lineCount) {
                    val left = result.getLineLeft(line)
                    val right = result.getLineRight(line)
                    if (right - left < 1f) continue
                    val top = result.getLineTop(line)
                    val bottom = result.getLineBottom(line)
                    val inset = (bottom - top) * 0.12f
                    drawRect(
                        color = barColor,
                        topLeft = Offset(left, top + inset),
                        size = Size(right - left + pad * 2, bottom - top - inset * 2),
                    )
                }
            }
            .padding(horizontal = barPadding),
    )
}
