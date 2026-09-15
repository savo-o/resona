package com.savoo.scclient.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.savoo.scclient.data.repository.ArtworkShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val BLOB_AMPLITUDE = 0.055f
private const val BLOB_BUMPS = 8
private const val BLOB_VERTICES = 128

private val blobPolygon: RoundedPolygon by lazy {
    val baseR = 0.5f / (1f + BLOB_AMPLITUDE)
    val vertices = FloatArray(BLOB_VERTICES * 2)
    for (i in 0 until BLOB_VERTICES) {
        val theta = i.toFloat() / BLOB_VERTICES * 2f * PI.toFloat()
        val r = baseR * (1f + BLOB_AMPLITUDE * sin(BLOB_BUMPS * theta))
        vertices[i * 2] = 0.5f + r * cos(theta)
        vertices[i * 2 + 1] = 0.5f + r * sin(theta)
    }
    RoundedPolygon(vertices, centerX = 0.5f, centerY = 0.5f)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ArtworkShape.polygon(): RoundedPolygon = when (this) {
    ArtworkShape.BLOB -> blobPolygon
    ArtworkShape.CIRCLE -> MaterialShapes.Circle
    ArtworkShape.SQUARE -> MaterialShapes.Square
    ArtworkShape.COOKIE_9 -> MaterialShapes.Cookie9Sided
    ArtworkShape.COOKIE_12 -> MaterialShapes.Cookie12Sided
    ArtworkShape.CLOVER_4 -> MaterialShapes.Clover4Leaf
    ArtworkShape.CLOVER_8 -> MaterialShapes.Clover8Leaf
    ArtworkShape.SUNNY -> MaterialShapes.Sunny
    ArtworkShape.SOFT_BURST -> MaterialShapes.SoftBurst
    ArtworkShape.FLOWER -> MaterialShapes.Flower
    ArtworkShape.PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond
    ArtworkShape.HEART -> MaterialShapes.Heart
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Morph.pathIn(progress: Float, size: Size): Path {
    val path = toPath(progress)
    path.transform(Matrix().apply { scale(size.width, size.height) })
    return path
}

class MorphingArtworkShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morph.pathIn(progress, size))
}

@Composable
fun rememberArtworkShape(shape: ArtworkShape): Shape {
    var from by remember { mutableStateOf(shape) }
    var to by remember { mutableStateOf(shape) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(shape) {
        if (shape == to) return@LaunchedEffect
        from = to
        to = shape
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    val morph = remember(from, to) { Morph(from.polygon().normalized(), to.polygon().normalized()) }
    return MorphingArtworkShape(morph, progress.value)
}

fun Modifier.artworkProgressRing(
    shape: Shape,
    progress: () -> Float,
    color: Color,
    stroke: Dp,
): Modifier = drawWithCache {
    val strokePx = stroke.toPx()
    val inset = strokePx / 2f
    val box = Size(size.width - strokePx, size.height - strokePx)
    val outline = (shape.createOutline(box, layoutDirection, this) as? Outline.Generic)?.path ?: Path()
    val ring = Path().apply { addPath(outline, Offset(inset, inset)) }
    val measure = PathMeasure().apply { setPath(ring, forceClosed = true) }
    val length = measure.length
    val center = Offset(size.width / 2f, size.height / 2f)

    var startDistance = 0f
    var bestAngleGap = Float.MAX_VALUE
    var signedArea = 0f
    val samples = 240
    var previous = measure.getPosition(0f)
    for (i in 0..samples) {
        val distance = length * i / samples
        val point = measure.getPosition(distance)
        val gap = abs(atan2(point.y - center.y, point.x - center.x) + (PI / 2).toFloat())
        if (gap < bestAngleGap) {
            bestAngleGap = gap
            startDistance = distance
        }
        signedArea += previous.x * point.y - point.x * previous.y
        previous = point
    }
    val clockwise = signedArea >= 0f
    val strokeStyle = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val segment = Path()

    fun appendSegment(from: Float, to: Float) {
        if (to > from) measure.getSegment(from, to, segment, startWithMoveTo = true)
    }

    onDrawBehind {
        drawPath(ring, color = color.copy(alpha = 0.25f), style = strokeStyle)
        val span = length * progress().coerceIn(0f, 1f)
        if (span <= 0f || length <= 0f) return@onDrawBehind
        segment.reset()
        if (clockwise) {
            val end = startDistance + span
            if (end <= length) {
                appendSegment(startDistance, end)
            } else {
                appendSegment(startDistance, length)
                appendSegment(0f, end - length)
            }
        } else {
            val begin = startDistance - span
            if (begin >= 0f) {
                appendSegment(begin, startDistance)
            } else {
                appendSegment(length + begin, length)
                appendSegment(0f, startDistance)
            }
        }
        drawPath(segment, color = color, style = strokeStyle)
    }
}
