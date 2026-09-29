package com.savoo.scclient.ui.screens.recap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.repository.RecapStats
import com.savoo.scclient.ui.components.MorphingArtworkShape
import com.savoo.scclient.ui.components.hiResArtwork
import com.savoo.scclient.ui.components.pathIn
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecapShareCard(stats: RecapStats, accent: Color, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val numbers = NumberFormat.getIntegerInstance(locale)
    val cookie = remember { Morph(MaterialShapes.Cookie12Sided.normalized(), MaterialShapes.Cookie12Sided.normalized()) }
    val clover = remember { Morph(MaterialShapes.Clover4Leaf.normalized(), MaterialShapes.Clover4Leaf.normalized()) }
    val topArtist = stats.topArtists.firstOrNull()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(
                Brush.linearGradient(
                    listOf(lerp(accent, Color.Black, 0.45f), lerp(accent, Color.Black, 0.88f)),
                )
            ),
    ) {
        val s = maxWidth.value / 340f
        fun Int.u(): Dp = (this * s).dp
        fun Int.t() = (this * s).sp

        Canvas(Modifier.fillMaxSize()) {
            val big = size.width * 0.78f
            translate(size.width * 0.52f, -size.width * 0.2f) {
                drawPath(cookie.pathIn(1f, Size(big, big)), color = lerp(accent, Color.White, 0.2f).copy(alpha = 0.45f))
            }
            val small = size.width * 0.42f
            translate(-size.width * 0.14f, size.height * 0.72f) {
                drawPath(clover.pathIn(1f, Size(small, small)), color = accent.copy(alpha = 0.35f))
            }
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                    center = Offset(size.width * 0.3f, size.height * 0.4f),
                    radius = size.width,
                )
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 26.u(), end = 26.u())
                .size(96.u())
                .clip(MorphingArtworkShape(cookie, 1f))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            if (topArtist?.artworkUrl != null) {
                AsyncImage(
                    model = hiResArtwork(topArtist.artworkUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.Headphones, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.u()))
            }
        }

        Column(Modifier.fillMaxSize().padding(22.u())) {
            Text(
                "${stringResource(R.string.app_name).uppercase()}  ${stats.year}",
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Bold,
                fontSize = 11.t(),
                letterSpacing = (2.5f * s).sp,
            )
            Spacer(Modifier.height(30.u()))
            Text(
                stringResource(R.string.recap_kicker_minutes),
                color = Color.White.copy(alpha = 0.75f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.t(),
            )
            Text(
                numbers.format(stats.totals.totalMs / 60_000),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 50.t(),
                lineHeight = 52.t(),
                letterSpacing = (-2 * s).sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(18.u()))
            Row(horizontalArrangement = Arrangement.spacedBy(14.u())) {
                CardList(
                    title = stringResource(R.string.recap_card_top_artists),
                    items = stats.topArtists.take(3).map { it.artistName },
                    scale = s,
                    modifier = Modifier.weight(1f),
                )
                CardList(
                    title = stringResource(R.string.recap_card_top_tracks),
                    items = stats.topTracks.take(3).map { it.title },
                    scale = s,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.u())) {
                stats.topGenres.firstOrNull()?.let { CardChip(it.replaceFirstChar { c -> c.titlecase(locale) }, s, strong = true) }
                stats.listenerTime?.let { CardChip(stringResource(it.labelRes()), s, strong = false) }
            }
        }
    }
}

@Composable
private fun CardList(title: String, items: List<String>, scale: Float, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title.uppercase(),
            color = Color.White.copy(alpha = 0.65f),
            fontWeight = FontWeight.Bold,
            fontSize = (10 * scale).sp,
            letterSpacing = (1.5f * scale).sp,
        )
        Spacer(Modifier.height((6 * scale).dp))
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = (2 * scale).dp)) {
                Text(
                    "${index + 1}",
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Black,
                    fontSize = (13 * scale).sp,
                    modifier = Modifier.width((16 * scale).dp),
                )
                Text(
                    item,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (13 * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CardChip(text: String, scale: Float, strong: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (strong) Color.White else Color.White.copy(alpha = 0.18f))
            .padding(horizontal = (12 * scale).dp, vertical = (7 * scale).dp),
    ) {
        Text(
            text,
            color = if (strong) Color.Black else Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (12 * scale).sp,
            maxLines = 1,
        )
    }
}
