@file:Suppress("ktlint:standard:function-naming")

package app.logdate.ui.streak

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.logdate.ui.platform.rememberSystemReduceMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * Whether campfires animate. Screenshot scenes turn this off so the flicker holds still long
 * enough to capture a stable frame.
 */
val LocalCampfireAnimationEnabled = staticCompositionLocalOf { true }

/**
 * A small illustrated campfire that shows where the user's journaling streak stands.
 *
 * A burning fire flickers above two crossed logs and grows taller with [size]. Embers glow on the
 * logs with a couple of rising sparks. A fire that went out leaves charred logs and a thin line of
 * smoke, and an unlit fire is just the logs, ready to go. Motion stops when the system asks for
 * reduced motion.
 *
 * @param waitingForToday Draws a lower flame for a fire that nothing has been added to today.
 * @param contentDescription Spoken description of the fire, or `null` when a parent describes it.
 */
@Composable
fun Campfire(
    phase: CampfirePhase,
    size: CampfireSize?,
    modifier: Modifier = Modifier,
    waitingForToday: Boolean = false,
    contentDescription: String? = null,
) {
    val reduceMotion by rememberSystemReduceMotion()
    val animationEnabled = LocalCampfireAnimationEnabled.current && !LocalInspectionMode.current
    val progress = rememberCampfireProgress(animate = animationEnabled && !reduceMotion && phase != CampfirePhase.UNLIT)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val semanticsModifier =
        if (contentDescription != null) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }

    Canvas(modifier = modifier.then(semanticsModifier)) {
        drawCampfire(
            phase = phase,
            fireSize = size,
            waitingForToday = waitingForToday,
            progress = progress.value,
            onSurface = onSurface,
        )
    }
}

/** A 0..1 loop that drives the flicker, or a constant 0 when the fire should hold still. */
@Composable
private fun rememberCampfireProgress(animate: Boolean): State<Float> {
    if (!animate) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "campfire")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FLICKER_PERIOD_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "campfireFlicker",
    )
}

private fun DrawScope.drawCampfire(
    phase: CampfirePhase,
    fireSize: CampfireSize?,
    waitingForToday: Boolean,
    progress: Float,
    onSurface: Color,
) {
    val unit = size.minDimension
    val centerX = size.width / 2f
    val groundY = size.height - unit * 0.14f
    val logCenterY = groundY - unit * 0.07f
    val logTopY = groundY - unit * 0.13f
    val angle = progress * 2f * PI.toFloat()

    drawOval(
        color = onSurface.copy(alpha = 0.08f),
        topLeft = Offset(centerX - unit * 0.36f, groundY - unit * 0.035f),
        size = Size(unit * 0.72f, unit * 0.07f),
    )

    val flameHeight = unit * flameHeightFraction(fireSize) * if (waitingForToday) WAITING_FLAME_SCALE else 1f
    when (phase) {
        CampfirePhase.BURNING -> drawGlow(Offset(centerX, logTopY - flameHeight * 0.3f), flameHeight * 0.9f, 0.32f)
        CampfirePhase.EMBERS -> drawGlow(Offset(centerX, logTopY), unit * 0.36f, 0.42f + 0.08f * sin(angle))
        CampfirePhase.OUT, CampfirePhase.UNLIT -> Unit
    }

    val charred = phase == CampfirePhase.OUT
    drawLog(Offset(centerX, logCenterY), unit, degrees = 14f, grainAtEnd = true, charred = charred)
    drawLog(Offset(centerX, logCenterY), unit, degrees = -14f, grainAtEnd = false, charred = charred)

    when (phase) {
        CampfirePhase.BURNING -> drawFlame(centerX, logTopY + unit * 0.02f, flameHeight, angle)
        CampfirePhase.EMBERS -> drawEmbers(centerX, logTopY, unit, angle, progress)
        CampfirePhase.OUT -> drawSmoke(centerX, logTopY, unit, angle, onSurface)
        CampfirePhase.UNLIT -> Unit
    }
}

private fun flameHeightFraction(fireSize: CampfireSize?): Float =
    when (fireSize) {
        null, CampfireSize.SPARK -> 0.34f
        CampfireSize.SMALL -> 0.44f
        CampfireSize.CAMPFIRE -> 0.54f
        CampfireSize.BONFIRE -> 0.62f
        CampfireSize.BEACON -> 0.7f
    }

private fun DrawScope.drawGlow(
    center: Offset,
    radius: Float,
    alpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(listOf(GlowColor.copy(alpha = alpha), Color.Transparent), center, radius),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawLog(
    center: Offset,
    unit: Float,
    degrees: Float,
    grainAtEnd: Boolean,
    charred: Boolean,
) {
    val length = unit * 0.66f
    val thickness = unit * 0.12f
    val bark = if (charred) CharredBarkColor else BarkColor
    val grain = if (charred) CharredGrainColor else GrainColor
    rotate(degrees = degrees, pivot = center) {
        drawRoundRect(
            color = bark,
            topLeft = Offset(center.x - length / 2f, center.y - thickness / 2f),
            size = Size(length, thickness),
            cornerRadius = CornerRadius(thickness / 2f),
        )
        val grainCenter =
            Offset(
                x = if (grainAtEnd) center.x + length / 2f - thickness / 2f else center.x - length / 2f + thickness / 2f,
                y = center.y,
            )
        drawCircle(color = grain, radius = thickness * 0.4f, center = grainCenter)
        drawCircle(color = bark, radius = thickness * 0.2f, center = grainCenter, style = Stroke(width = unit * 0.01f))
    }
}

private fun DrawScope.drawFlame(
    centerX: Float,
    baseY: Float,
    height: Float,
    angle: Float,
) {
    val width = height * 0.64f
    val layers =
        listOf(
            FlameLayer(OuterFlameColor, heightScale = 1f, widthScale = 1f, phaseOffset = 0f),
            FlameLayer(MiddleFlameColor, heightScale = 0.72f, widthScale = 0.7f, phaseOffset = 1.3f),
            FlameLayer(InnerFlameColor, heightScale = 0.44f, widthScale = 0.42f, phaseOffset = 2.6f),
        )
    layers.forEach { layer ->
        val stretch = 1f + 0.05f * sin(2f * angle + layer.phaseOffset)
        val sway = sin(angle + layer.phaseOffset) * width * 0.07f
        drawPath(
            path =
                flamePath(
                    centerX = centerX,
                    baseY = baseY,
                    width = width * layer.widthScale,
                    height = height * layer.heightScale * stretch,
                    sway = sway,
                ),
            color = layer.color,
        )
    }
}

/** A teardrop with a rounded base centered on [centerX] and a tip that leans by [sway]. */
private fun flamePath(
    centerX: Float,
    baseY: Float,
    width: Float,
    height: Float,
    sway: Float,
): Path {
    val half = width / 2f
    val tipX = centerX + sway
    val tipY = baseY - height
    val shoulderY = baseY - height * 0.22f
    return Path().apply {
        moveTo(tipX, tipY)
        cubicTo(tipX + half * 0.2f, tipY + height * 0.35f, centerX + half, baseY - height * 0.5f, centerX + half, shoulderY)
        cubicTo(centerX + half, baseY + half * 0.15f, centerX - half, baseY + half * 0.15f, centerX - half, shoulderY)
        cubicTo(centerX - half, baseY - height * 0.5f, tipX - half * 0.2f, tipY + height * 0.35f, tipX, tipY)
        close()
    }
}

private fun DrawScope.drawEmbers(
    centerX: Float,
    logTopY: Float,
    unit: Float,
    angle: Float,
    progress: Float,
) {
    EMBER_OFFSETS.forEachIndexed { index, offset ->
        val center = Offset(centerX + offset * unit, logTopY - unit * 0.01f)
        val pulse = 0.8f + 0.2f * sin(angle + index * 1.7f)
        drawOval(
            color = EmberColor.copy(alpha = pulse),
            topLeft = Offset(center.x - unit * 0.07f, center.y - unit * 0.045f),
            size = Size(unit * 0.14f, unit * 0.09f),
        )
        drawCircle(color = EmberCoreColor.copy(alpha = pulse), radius = unit * 0.026f, center = center)
    }
    repeat(SPARK_COUNT) { index ->
        val rise = (progress + index.toFloat() / SPARK_COUNT) % 1f
        val center =
            Offset(
                x = centerX + sin(rise * 6f + index * 2f) * unit * 0.06f,
                y = logTopY - rise * unit * 0.42f,
            )
        drawCircle(color = EmberCoreColor.copy(alpha = 1f - rise), radius = unit * 0.02f, center = center)
    }
}

private fun DrawScope.drawSmoke(
    centerX: Float,
    logTopY: Float,
    unit: Float,
    angle: Float,
    onSurface: Color,
) {
    val height = unit * 0.46f
    val path =
        Path().apply {
            moveTo(centerX, logTopY)
            for (step in 1..SMOKE_STEPS) {
                val t = step.toFloat() / SMOKE_STEPS
                val x = centerX + sin(t * 3f * PI.toFloat() + angle) * unit * 0.05f * t
                lineTo(x, logTopY - t * height)
            }
        }
    drawPath(
        path = path,
        brush =
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, onSurface.copy(alpha = 0.32f)),
                startY = logTopY - height,
                endY = logTopY,
            ),
        style = Stroke(width = unit * 0.022f, cap = StrokeCap.Round),
    )
}

private data class FlameLayer(
    val color: Color,
    val heightScale: Float,
    val widthScale: Float,
    val phaseOffset: Float,
)

private const val FLICKER_PERIOD_MILLIS = 1_600
private const val WAITING_FLAME_SCALE = 0.78f
private const val SPARK_COUNT = 2
private const val SMOKE_STEPS = 16
private val EMBER_OFFSETS = listOf(-0.15f, -0.05f, 0.05f, 0.15f)

private val BarkColor = Color(0xFF8B5A3C)
private val GrainColor = Color(0xFFDDB083)
private val CharredBarkColor = Color(0xFF5E5A57)
private val CharredGrainColor = Color(0xFF8E8883)
private val OuterFlameColor = Color(0xFFFF7A1A)
private val MiddleFlameColor = Color(0xFFFFA630)
private val InnerFlameColor = Color(0xFFFFE08A)
private val GlowColor = Color(0xFFFF9A3C)
private val EmberColor = Color(0xFFE8531F)
private val EmberCoreColor = Color(0xFFFFB347)
