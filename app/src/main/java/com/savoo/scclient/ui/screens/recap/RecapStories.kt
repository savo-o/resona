package com.savoo.scclient.ui.screens.recap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.repository.ListenerTime
import com.savoo.scclient.data.repository.RecapStats
import com.savoo.scclient.ui.components.captureInto
import com.savoo.scclient.ui.components.hiResArtwork
import com.savoo.scclient.ui.components.rememberArtworkColor
import com.savoo.scclient.ui.components.rememberCaptureLayer
import com.savoo.scclient.ui.components.shareImage
import com.savoo.scclient.ui.haptics.rememberHapticTick
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.Month
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class RecapSlide { INTRO, MINUTES, TOP_ARTIST, TOP_TRACKS, GENRES, RHYTHM, DISCOVERY, SKIPPED, SUMMARY }

private const val SLIDE_MS = 7_000

private val fallbackAccents = listOf(
    Color(0xFF7C4DFF),
    Color(0xFFFF4F8B),
    Color(0xFF00BFA5),
    Color(0xFFFF8F00),
    Color(0xFF2979FF),
    Color(0xFFAEEA00),
    Color(0xFFE040FB),
    Color(0xFFFF5252),
    Color(0xFF00B8D4),
)

fun RecapStats.slides(): List<RecapSlide> = buildList {
    add(RecapSlide.INTRO)
    add(RecapSlide.MINUTES)
    if (topArtists.isNotEmpty()) add(RecapSlide.TOP_ARTIST)
    if (topTracks.isNotEmpty()) add(RecapSlide.TOP_TRACKS)
    if (topGenres.isNotEmpty()) add(RecapSlide.GENRES)
    if (listenerTime != null) add(RecapSlide.RHYTHM)
    if (discoveries.isNotEmpty()) add(RecapSlide.DISCOVERY)
    if (mostSkipped.isNotEmpty()) add(RecapSlide.SKIPPED)
    add(RecapSlide.SUMMARY)
}

@Composable
fun RecapStoriesDialog(stats: RecapStats, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        RecapStories(stats = stats, onClose = onDismiss)
    }
}

@Composable
fun RecapStories(stats: RecapStats, onClose: () -> Unit) {
    val slides = remember(stats) { stats.slides() }
    val haptic = rememberHapticTick()
    var current by rememberSaveable { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(true) }
    val progress = remember(current) { Animatable(0f) }

    fun goTo(index: Int) {
        val target = index.coerceIn(0, slides.lastIndex)
        if (target == current) return
        haptic()
        forward = target > current
        current = target
    }

    LaunchedEffect(progress, paused) {
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * SLIDE_MS).toInt()
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (current < slides.lastIndex) goTo(current + 1)
    }

    val topTrackColor = rememberArtworkColor(stats.topTracks.firstOrNull()?.artworkUrl)
    val topArtistColor = rememberArtworkColor(stats.topArtists.firstOrNull()?.artworkUrl)
    val discoveryColor = rememberArtworkColor(stats.discoveries.firstOrNull()?.artworkUrl)
    val skippedColor = rememberArtworkColor(stats.mostSkipped.firstOrNull()?.artworkUrl)
    val slide = slides[current]
    val fallback = fallbackAccents[slide.ordinal % fallbackAccents.size]
    val targetAccent = when (slide) {
        RecapSlide.INTRO, RecapSlide.TOP_TRACKS -> topTrackColor
        RecapSlide.TOP_ARTIST, RecapSlide.SUMMARY -> topArtistColor
        RecapSlide.DISCOVERY -> discoveryColor
        RecapSlide.SKIPPED -> skippedColor
        else -> null
    } ?: fallback
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = spring(stiffness = Spring.StiffnessVeryLow),
        label = "recapAccent",
    )
    val summaryAccent = topArtistColor ?: fallbackAccents[RecapSlide.SUMMARY.ordinal % fallbackAccents.size]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(slides.size) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    paused = true
                    val up = waitForUpOrCancellation()
                    paused = false
                    if (up != null && up.uptimeMillis - down.uptimeMillis < 250) {
                        up.consume()
                        if (down.position.x < size.width * 0.3f) goTo(current - 1) else goTo(current + 1)
                    }
                }
            },
    ) {
        RecapBackdrop(accent = accent, slide = current)

        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                slides.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.28f)),
                    ) {
                        val fill = when {
                            index < current -> 1f
                            index == current -> progress.value
                            else -> 0f
                        }
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fill)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.app_name).uppercase(),
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 3.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recap_close), tint = Color.White)
                }
            }

            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    val direction = if (forward) 1 else -1
                    (
                        fadeIn(tween(320, delayMillis = 60)) +
                            scaleIn(spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.9f) +
                            slideInHorizontally(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 6 * direction }
                        ) togetherWith (fadeOut(tween(180)) + scaleOut(tween(220), targetScale = 1.06f))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "recapSlide",
            ) { index ->
                Box(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp)) {
                    when (slides[index]) {
                        RecapSlide.INTRO -> IntroSlide(stats)
                        RecapSlide.MINUTES -> MinutesSlide(stats)
                        RecapSlide.TOP_ARTIST -> TopArtistSlide(stats)
                        RecapSlide.TOP_TRACKS -> TopTracksSlide(stats)
                        RecapSlide.GENRES -> GenresSlide(stats)
                        RecapSlide.RHYTHM -> RhythmSlide(stats, accent)
                        RecapSlide.DISCOVERY -> DiscoverySlide(stats)
                        RecapSlide.SKIPPED -> SkippedSlide(stats)
                        RecapSlide.SUMMARY -> SummarySlide(stats, summaryAccent, onReplay = { goTo(0) })
                    }
                }
            }
        }
    }
}

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

@Composable
private fun formatNumber(value: Long): String = NumberFormat.getIntegerInstance(locale()).format(value)

private fun minutes(ms: Long): Long = ms / 60_000

@Composable
private fun Kicker(text: String, delayMs: Int = 0, modifier: Modifier = Modifier) {
    val reveal = rememberReveal(delayMs)
    Text(
        text.uppercase(locale()),
        color = Color.White.copy(alpha = 0.78f),
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 2.sp,
        modifier = modifier.revealed(reveal, offset = 12.dp),
    )
}

@Composable
private fun Headline(text: String, delayMs: Int, fontSize: Int = 44, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val reveal = rememberReveal(delayMs)
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.Black,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.05f).sp,
        letterSpacing = (-1).sp,
        textAlign = textAlign,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.revealed(reveal),
    )
}

@Composable
private fun Caption(text: String, delayMs: Int, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val reveal = rememberReveal(delayMs)
    Text(
        text,
        color = Color.White.copy(alpha = 0.85f),
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        textAlign = textAlign,
        modifier = modifier.revealed(reveal, offset = 16.dp),
    )
}

@Composable
private fun Pill(text: String, delayMs: Int, strong: Boolean = false) {
    val reveal = rememberReveal(delayMs)
    Box(
        modifier = Modifier
            .revealed(reveal, offset = 20.dp, fromScale = 0.6f)
            .clip(CircleShape)
            .background(if (strong) Color.White else Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            color = if (strong) Color.Black else Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IntroSlide(stats: RecapStats) {
    val dateFormat = DateTimeFormatter.ofPattern("d MMMM", locale())
    val since = Instant.ofEpochMilli(stats.periodStart).atZone(ZoneId.systemDefault()).toLocalDate()
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Kicker(stringResource(R.string.recap_kicker_intro))
        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center) {
            MorphArtwork(
                url = stats.topTracks.firstOrNull()?.artworkUrl,
                from = MaterialShapes.Circle,
                to = MaterialShapes.Cookie12Sided,
                size = 250.dp,
                delayMs = 150,
                spin = true,
                modifier = Modifier.graphicsLayer { alpha = 0.55f },
            )
            Row {
                stats.year.toString().forEachIndexed { index, digit ->
                    val reveal = rememberReveal(350 + index * 110)
                    Text(
                        digit.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 96.sp,
                        letterSpacing = (-4).sp,
                        modifier = Modifier.revealed(reveal, offset = 70.dp, fromScale = 0.5f),
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Caption(
            if (stats.startedMidYear) {
                stringResource(R.string.recap_intro_since, since.format(dateFormat))
            } else {
                stringResource(R.string.recap_intro_full_year)
            },
            delayMs = 900,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        val noteReveal = rememberReveal(1300)
        Text(
            stringResource(R.string.recap_local_note),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.revealed(noteReveal, offset = 10.dp),
        )
    }
}

@Composable
private fun MinutesSlide(stats: RecapStats) {
    val totalMinutes = minutes(stats.totals.totalMs)
    val counted by rememberCountUp(totalMinutes, delayMs = 250)
    val numberReveal = rememberReveal(150)
    val hours = totalMinutes / 60
    val days = hours / 24
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker(stringResource(R.string.recap_kicker_minutes))
        Spacer(Modifier.height(8.dp))
        Text(
            formatNumber(counted),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 84.sp,
            lineHeight = 88.sp,
            letterSpacing = (-3).sp,
            maxLines = 1,
            modifier = Modifier.revealed(numberReveal, offset = 40.dp, fromScale = 0.7f),
        )
        Spacer(Modifier.height(12.dp))
        Caption(
            if (days >= 1) {
                pluralStringResource(R.plurals.recap_days_nonstop, days.toInt(), formatNumber(days))
            } else {
                pluralStringResource(R.plurals.recap_hours_nonstop, hours.toInt(), formatNumber(hours))
            },
            delayMs = 1500,
        )
        Spacer(Modifier.height(36.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(pluralStringResource(R.plurals.recap_plays, stats.totals.playCount, formatNumber(stats.totals.playCount.toLong())), 1900, strong = true)
            Pill(pluralStringResource(R.plurals.recap_tracks, stats.totals.trackCount, formatNumber(stats.totals.trackCount.toLong())), 2050)
            Pill(pluralStringResource(R.plurals.recap_artists, stats.totals.artistCount, formatNumber(stats.totals.artistCount.toLong())), 2200)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopArtistSlide(stats: RecapStats) {
    val top = stats.topArtists.first()
    val topMinutes = minutes(top.totalMs)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker(stringResource(R.string.recap_kicker_top_artist))
        Spacer(Modifier.height(20.dp))
        MorphArtwork(
            url = top.artworkUrl,
            from = MaterialShapes.Circle,
            to = MaterialShapes.Cookie12Sided,
            size = 210.dp,
            delayMs = 100,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(22.dp))
        Headline(top.artistName, delayMs = 450)
        Spacer(Modifier.height(6.dp))
        Caption(pluralStringResource(R.plurals.recap_minutes_together, topMinutes.toInt(), formatNumber(topMinutes)), delayMs = 650)
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.topArtists.drop(1).forEachIndexed { index, artist ->
                RankRow(
                    rank = index + 2,
                    title = artist.artistName,
                    subtitle = null,
                    trailing = stringResource(R.string.recap_minutes_short, formatNumber(minutes(artist.totalMs))),
                    artworkUrl = artist.artworkUrl,
                    round = true,
                    delayMs = 900 + index * 120,
                )
            }
        }
    }
}

@Composable
private fun TopTracksSlide(stats: RecapStats) {
    val top = stats.topTracks.first()
    val heroReveal = rememberReveal(200)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker(stringResource(R.string.recap_kicker_top_tracks))
        Spacer(Modifier.height(22.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .revealed(heroReveal, offset = 40.dp, fromScale = 0.8f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkThumb(top.artworkUrl, size = 96, round = false)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("1", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(
                    top.title,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    lineHeight = 25.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(top.artistName, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(
                    pluralStringResource(R.plurals.recap_plays, top.playCount, formatNumber(top.playCount.toLong())),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.topTracks.drop(1).forEachIndexed { index, track ->
                RankRow(
                    rank = index + 2,
                    title = track.title,
                    subtitle = track.artistName,
                    trailing = formatNumber(track.playCount.toLong()),
                    artworkUrl = track.artworkUrl,
                    round = false,
                    delayMs = 550 + index * 120,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GenresSlide(stats: RecapStats) {
    val top = stats.topGenres.first().replaceFirstChar { it.titlecase(locale()) }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Kicker(stringResource(R.string.recap_kicker_genres))
        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center) {
            MorphArtwork(
                url = null,
                from = MaterialShapes.Flower,
                to = MaterialShapes.SoftBurst,
                size = 260.dp,
                delayMs = 100,
                spin = true,
                placeholder = Icons.Filled.MusicNote,
                modifier = Modifier.graphicsLayer { alpha = 0.5f },
            )
            Headline(top, delayMs = 350, fontSize = 52, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Caption(stringResource(R.string.recap_genres_caption), delayMs = 700, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.topGenres.drop(1).forEachIndexed { index, genre ->
                Pill(genre, delayMs = 950 + index * 150)
            }
        }
    }
}

@Composable
private fun RhythmSlide(stats: RecapStats, accent: Color) {
    val time = stats.listenerTime ?: ListenerTime.NIGHT
    val peakHour = stats.hourlyMs.withIndex().maxBy { it.value }.index
    val maxMonth = stats.monthlyMs.max().coerceAtLeast(1L)
    val topMonth = stats.topMonth
    val loc = locale()
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Kicker(stringResource(R.string.recap_kicker_rhythm))
        Spacer(Modifier.height(10.dp))
        Headline(stringResource(time.labelRes()), delayMs = 200)
        Spacer(Modifier.height(10.dp))
        Caption(stringResource(R.string.recap_peak_hour, "%02d:00".format(peakHour)), delayMs = 450)
        Spacer(Modifier.height(36.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(170.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            stats.monthlyMs.forEachIndexed { index, ms ->
                val grow = rememberReveal(650 + index * 55)
                val highlighted = index + 1 == topMonth
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val fraction = (ms.toFloat() / maxMonth).coerceIn(0.04f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .fillMaxHeight(fraction * 0.86f)
                            .graphicsLayer {
                                scaleY = grow.value.coerceAtLeast(0f)
                                transformOrigin = TransformOrigin(0.5f, 1f)
                            }
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(if (highlighted) Color.White else lerp(accent, Color.White, 0.35f).copy(alpha = 0.55f)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Month.of(index + 1).getDisplayName(TextStyle.NARROW_STANDALONE, loc),
                        color = Color.White.copy(alpha = if (highlighted) 1f else 0.6f),
                        fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (topMonth != null) {
            Spacer(Modifier.height(20.dp))
            val monthName = Month.of(topMonth).getDisplayName(TextStyle.FULL_STANDALONE, loc).replaceFirstChar { it.titlecase(loc) }
            Caption(stringResource(R.string.recap_top_month, monthName), delayMs = 1400)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DiscoverySlide(stats: RecapStats) {
    val discovery = stats.discoveries.first()
    val first = Instant.ofEpochMilli(discovery.firstPlayedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val sinceMinutes = minutes(discovery.totalMs)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Kicker(stringResource(R.string.recap_kicker_discovery))
        Spacer(Modifier.height(24.dp))
        MorphArtwork(
            url = discovery.artworkUrl,
            from = MaterialShapes.Circle,
            to = MaterialShapes.Sunny,
            size = 230.dp,
            delayMs = 100,
            spin = true,
        )
        Spacer(Modifier.height(26.dp))
        Headline(discovery.artistName, delayMs = 450, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Caption(
            stringResource(R.string.recap_first_heard, first.format(DateTimeFormatter.ofPattern("d MMMM", locale()))),
            delayMs = 700,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Pill(pluralStringResource(R.plurals.recap_minutes_since, sinceMinutes.toInt(), formatNumber(sinceMinutes)), 950, strong = true)
    }
}

@Composable
private fun SkippedSlide(stats: RecapStats) {
    val track = stats.mostSkipped.first()
    val wobble = rememberReveal(250)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Kicker(stringResource(R.string.recap_kicker_skipped))
        Spacer(Modifier.height(28.dp))
        Box(
            Modifier.graphicsLayer {
                val value = wobble.value
                alpha = value.coerceIn(0f, 1f)
                rotationZ = -8f * value
                translationX = (1f - value) * 300f
            },
        ) {
            ArtworkThumb(track.artworkUrl, size = 190, round = false)
        }
        Spacer(Modifier.height(28.dp))
        Headline(track.title, delayMs = 500, fontSize = 34, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Caption(track.artistName, delayMs = 600, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Pill(pluralStringResource(R.plurals.recap_skipped_times, track.skipCount, formatNumber(track.skipCount.toLong())), 850, strong = true)
    }
}

@Composable
private fun SummarySlide(stats: RecapStats, accent: Color, onReplay: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val captureLayer = rememberCaptureLayer()
    var sharing by remember { mutableStateOf(false) }
    val cardReveal = rememberReveal(150)
    val buttonsReveal = rememberReveal(600)
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Kicker(stringResource(R.string.recap_kicker_summary, stats.year))
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .revealed(cardReveal, offset = 60.dp, fromScale = 0.75f)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            Box(Modifier.captureInto(captureLayer)) {
                RecapShareCard(stats = stats, accent = accent)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.revealed(buttonsReveal, offset = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onReplay,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recap_replay))
            }
            Button(
                enabled = !sharing,
                onClick = {
                    sharing = true
                    scope.launch {
                        runCatching {
                            val bitmap = captureLayer.toImageBitmap().asAndroidBitmap()
                            shareImage(context, bitmap, "resona_recap_${stats.year}")
                        }
                        sharing = false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                if (sharing) {
                    SharingIndicator()
                } else {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recap_share))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SharingIndicator() {
    LoadingIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
}

@Composable
private fun RankRow(
    rank: Int,
    title: String,
    subtitle: String?,
    trailing: String,
    artworkUrl: String?,
    round: Boolean,
    delayMs: Int,
) {
    val reveal = rememberReveal(delayMs)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val value = reveal.value
                alpha = value.coerceIn(0f, 1f)
                translationX = (1f - value) * 120f
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$rank",
            color = Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            modifier = Modifier.width(22.dp),
        )
        ArtworkThumb(artworkUrl, size = 42, round = round)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(trailing, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun ArtworkThumb(url: String?, size: Int, round: Boolean) {
    val shape = if (round) CircleShape else RoundedCornerShape((size * 0.22f).dp)
    Box(
        modifier = Modifier.size(size.dp).clip(shape).background(Color.White.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = hiResArtwork(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.4f).dp))
        }
    }
}

fun ListenerTime.labelRes(): Int = when (this) {
    ListenerTime.MORNING -> R.string.recap_listener_morning
    ListenerTime.DAY -> R.string.recap_listener_day
    ListenerTime.EVENING -> R.string.recap_listener_evening
    ListenerTime.NIGHT -> R.string.recap_listener_night
}
