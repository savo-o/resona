package com.savoo.scclient.ui.screens.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.ui.haptics.rememberHapticTick
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
)

private val pages = listOf(
    OnboardingPage(Icons.Filled.Headphones, R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc),
    OnboardingPage(Icons.Filled.AutoAwesome, R.string.onboarding_mix_title, R.string.onboarding_mix_desc),
    OnboardingPage(Icons.Filled.Lyrics, R.string.onboarding_lyrics_title, R.string.onboarding_lyrics_desc),
    OnboardingPage(Icons.Filled.CloudDownload, R.string.onboarding_offline_title, R.string.onboarding_offline_desc),
    OnboardingPage(Icons.Filled.CloudSync, R.string.onboarding_migrate_title, R.string.onboarding_migrate_desc),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val haptic = rememberHapticTick()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    var contentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { contentVisible = true }
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "onboardingAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        OnboardingOrbsBackground(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha })

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars)
                .graphicsLayer { alpha = contentAlpha },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { haptic(); onFinish() }) {
                    Text(
                        stringResource(R.string.onboarding_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                OnboardingPageContent(pages[page])
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                pages.indices.forEach { index ->
                    PageIndicatorDot(selected = pagerState.currentPage == index)
                    if (index != pages.lastIndex) Spacer(Modifier.width(8.dp))
                }
            }

            val isLastPage = pagerState.currentPage == pages.lastIndex
            Surface(
                onClick = {
                    haptic()
                    if (isLastPage) {
                        onFinish()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isLastPage) stringResource(R.string.onboarding_get_started)
                            else stringResource(R.string.onboarding_next),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PageIndicatorDot(selected: Boolean) {
    val width by animateDpAsState(
        targetValue = if (selected) 24.dp else 8.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "dotWidth",
    )
    Box(
        modifier = Modifier
            .height(8.dp)
            .width(width)
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                CircleShape,
            ),
    )
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    page.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(page.descRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnboardingOrbsBackground(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "onboardingOrbs")
    val angleA by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "orbAngleA",
    )
    val angleB by infinite.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing)),
        label = "orbAngleB",
    )
    val angleC by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing)),
        label = "orbAngleC",
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbBreathe",
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    // Dark schemes use light, high-chroma tones for primary/secondary/tertiary (for contrast against
    // dark surfaces), which turns these blurred blobs into a glaring wash unless heavily dimmed.
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val orbAlpha = if (isDark) 0.22f else 0.5f

    fun softGradient(c: Color) = Brush.radialGradient(
        0f to c,
        0.5f to c.copy(alpha = c.alpha * 0.5f),
        1f to c.copy(alpha = 0f),
    )

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .requiredSize(420.dp)
                .graphicsLayer {
                    scaleX = breathe; scaleY = breathe
                    val rad = Math.toRadians(angleA.toDouble())
                    translationX = (cos(rad) * 220f).toFloat() + 140f
                    translationY = (sin(rad) * 220f).toFloat() - 140f
                }
                .background(softGradient(primary.copy(alpha = orbAlpha)), CircleShape)
                .blur(80.dp, BlurredEdgeTreatment.Unbounded),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .requiredSize(440.dp)
                .graphicsLayer {
                    scaleX = breathe; scaleY = breathe
                    val rad = Math.toRadians(angleB.toDouble())
                    translationX = (cos(rad) * 200f).toFloat() - 120f
                    translationY = (sin(rad) * 200f).toFloat() + 120f
                }
                .background(softGradient(tertiary.copy(alpha = orbAlpha * 0.9f)), CircleShape)
                .blur(90.dp, BlurredEdgeTreatment.Unbounded),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .requiredSize(360.dp)
                .graphicsLayer {
                    scaleX = breathe; scaleY = breathe
                    val rad = Math.toRadians(angleC.toDouble())
                    translationX = (cos(rad) * 180f).toFloat()
                    translationY = (sin(rad) * 180f).toFloat()
                }
                .background(softGradient(secondary.copy(alpha = orbAlpha * 0.8f)), CircleShape)
                .blur(75.dp, BlurredEdgeTreatment.Unbounded),
        )
    }
}
