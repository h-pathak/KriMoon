package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Non-state mutable reference for layout coordinates read only inside tap/click handlers
 * (e.g., particle burst spawn origin). Avoids triggering Compose recompositions on every
 * LazyColumn scroll frame inside `onGloballyPositioned`.
 */
class OffsetHolder(var value: Offset = Offset.Zero)

data class ParticleItem(
    val id: Long,
    val emoji: String,
    val startX: Float,
    val startY: Float,
    val targetDx: Float,
    val targetDy: Float,
    val targetRot: Float,
    val maxScale: Float,
    val durationMs: Int
)

class ParticleController {
    companion object {
        private const val MAX_ACTIVE_PARTICLES = 24
    }

    val activeParticles = mutableStateListOf<ParticleItem>()
    var toastMessage by mutableStateOf<String?>(null)
    var toastIcon by mutableStateOf("🌸")
    private var toastJob: kotlinx.coroutines.Job? = null

    fun spawn(
        centerX: Float,
        centerY: Float,
        emojis: List<String> = listOf("🌸", "✨", "💖", "💫", "🫧", "🌷"),
        count: Int = 12
    ) {
        val clampedCount = count.coerceIn(1, 14)
        val overflow = (activeParticles.size + clampedCount) - MAX_ACTIVE_PARTICLES
        if (overflow > 0) {
            repeat(overflow.coerceAtMost(activeParticles.size)) {
                activeParticles.removeAt(0)
            }
        }
        val now = System.currentTimeMillis()
        for (i in 0 until clampedCount) {
            val emoji = emojis[Random.nextInt(emojis.size)]
            val angle = (i.toFloat() / clampedCount.toFloat()) * 2f * Math.PI.toFloat() + (Random.nextFloat() - 0.5f) * 0.7f
            val distance = 70f + Random.nextFloat() * 110f
            val targetDx = cos(angle) * distance
            val targetDy = sin(angle) * distance - (50f + Random.nextFloat() * 70f) // float upward
            val targetRot = (Random.nextFloat() - 0.5f) * 360f
            val maxScale = 0.85f + Random.nextFloat() * 0.55f
            val duration = 750 + Random.nextInt(220)

            activeParticles.add(
                ParticleItem(
                    id = now + i + Random.nextLong(100000),
                    emoji = emoji,
                    startX = centerX,
                    startY = centerY,
                    targetDx = targetDx,
                    targetDy = targetDy,
                    targetRot = targetRot,
                    maxScale = maxScale,
                    durationMs = duration
                )
            )
        }
    }

    fun showToast(
        coroutineScope: kotlinx.coroutines.CoroutineScope,
        message: String,
        icon: String = "🌸"
    ) {
        toastJob?.cancel()
        toastMessage = message
        toastIcon = icon
        toastJob = coroutineScope.launch {
            delay(2600)
            toastMessage = null
        }
    }

    fun removeParticle(item: ParticleItem) {
        activeParticles.remove(item)
    }
}

val LocalParticleController = compositionLocalOf { ParticleController() }

/**
 * Dreamy soft-girl ambient watercolor orbs (blush, lilac, buttercream & matcha)
 * paired with subtle, low-alpha cute margin emojis.
 *
 * Performance-engineered for locked 60/120fps scrolling:
 * - Zero idle infinite animation loops (0 GPU frames/sec when idle).
 * - Zero `BoxWithConstraints` subcomposition overhead.
 * - Radial gradient shaders are cached once per viewport resize via `drawWithCache`.
 */
@Composable
fun FloatingPetalsBackground(
    parallaxOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier
) {
    val blush = com.example.ui.theme.PhasesRose.copy(alpha = 0.10f)
    val lilac = com.example.ui.theme.PhasesLavender.copy(alpha = 0.09f)
    val butter = com.example.ui.theme.KriMoonButterTint.copy(alpha = 0.45f)
    val sage = com.example.ui.theme.PhasesSage.copy(alpha = 0.07f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val c1 = Offset(size.width * 0.88f, size.height * 0.10f)
                val r1 = (size.minDimension * 0.68f).coerceAtLeast(1f)
                val b1 = Brush.radialGradient(listOf(blush, Color.Transparent), center = c1, radius = r1)

                val c2 = Offset(size.width * 0.10f, size.height * 0.44f)
                val r2 = (size.minDimension * 0.60f).coerceAtLeast(1f)
                val b2 = Brush.radialGradient(listOf(lilac, Color.Transparent), center = c2, radius = r2)

                val c3 = Offset(size.width * 0.18f, size.height * 0.14f)
                val r3 = (size.minDimension * 0.44f).coerceAtLeast(1f)
                val b3 = Brush.radialGradient(listOf(butter, Color.Transparent), center = c3, radius = r3)

                val c4 = Offset(size.width * 0.84f, size.height * 0.86f)
                val r4 = (size.minDimension * 0.54f).coerceAtLeast(1f)
                val b4 = Brush.radialGradient(listOf(sage, Color.Transparent), center = c4, radius = r4)

                onDrawBehind {
                    drawCircle(brush = b1, radius = r1, center = c1)
                    drawCircle(brush = b2, radius = r2, center = c2)
                    drawCircle(brush = b3, radius = r3, center = c3)
                    drawCircle(brush = b4, radius = r4, center = c4)
                }
            }
    ) {
        Text(
            text = "🌸",
            fontSize = 18.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 44.dp)
                .graphicsLayer {
                    rotationZ = -10f
                    alpha = 0.28f
                }
        )
        Text(
            text = "✨",
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 22.dp, top = 74.dp)
                .graphicsLayer {
                    rotationZ = 8f
                    alpha = 0.30f
                }
        )
        Text(
            text = "🌙",
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp, bottom = 96.dp)
                .graphicsLayer {
                    rotationZ = -8f
                    alpha = 0.24f
                }
        )
        Text(
            text = "🩷",
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp, top = 40.dp)
                .graphicsLayer {
                    rotationZ = 10f
                    alpha = 0.24f
                }
        )
        Text(
            text = "🫧",
            fontSize = 17.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 128.dp)
                .graphicsLayer {
                    rotationZ = 6f
                    alpha = 0.25f
                }
        )
        Text(
            text = "🌷",
            fontSize = 17.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp)
                .graphicsLayer {
                    rotationZ = -9f
                    alpha = 0.26f
                }
        )
    }
}

/**
 * Overlay that renders all active burst particles (GPU layer-only transforms, zero layout passes)
 * and cute toast confirmations.
 */
@Composable
fun ParticleBurstOverlay(
    controller: ParticleController = LocalParticleController.current,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        controller.activeParticles.forEach { particle ->
            key(particle.id) {
                SingleParticleView(
                    particle = particle,
                    onFinished = { controller.removeParticle(particle) }
                )
            }
        }

        // Cute Affirmative Toast
        AnimatedVisibility(
            visible = controller.toastMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn() + scaleIn(initialScale = 0.85f),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(
                    1.2.dp,
                    com.example.ui.theme.PhasesRose.copy(alpha = 0.42f)
                ),
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(text = controller.toastIcon, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = controller.toastMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "✨", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SingleParticleView(
    particle: ParticleItem,
    onFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(particle.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = particle.durationMs,
                easing = FastOutSlowInEasing
            )
        )
        onFinished()
    }

    Text(
        text = particle.emoji,
        fontSize = 24.sp,
        modifier = Modifier.graphicsLayer {
            val p = progress.value
            val alpha = when {
                p < 0.15f -> p / 0.15f
                p > 0.65f -> (1f - p) / 0.35f
                else -> 1f
            }
            val scale = if (p < 0.2f) {
                (p / 0.2f) * particle.maxScale
            } else {
                particle.maxScale * (1f - (p - 0.2f) * 0.35f)
            }
            val rotation = particle.targetRot * p
            translationX = particle.startX + particle.targetDx * p
            translationY = particle.startY + particle.targetDy * p
            this.alpha = alpha.coerceIn(0f, 1f)
            this.scaleX = scale
            this.scaleY = scale
            this.rotationZ = rotation
        }
    )
}
