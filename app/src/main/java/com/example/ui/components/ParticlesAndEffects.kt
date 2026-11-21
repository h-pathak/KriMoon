package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PhasesLine
import com.example.ui.theme.PhasesPlum
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

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
        val now = System.currentTimeMillis()
        for (i in 0 until count) {
            val emoji = emojis[Random.nextInt(emojis.size)]
            val angle = (i.toFloat() / count.toFloat()) * 2f * Math.PI.toFloat() + (Random.nextFloat() - 0.5f) * 0.7f
            val distance = 70f + Random.nextFloat() * 110f
            val targetDx = cos(angle) * distance
            val targetDy = sin(angle) * distance - (50f + Random.nextFloat() * 70f) // float upward
            val targetRot = (Random.nextFloat() - 0.5f) * 360f
            val maxScale = 0.85f + Random.nextFloat() * 0.55f
            val duration = 850 + Random.nextInt(250)

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
 * Subtle ambient watercolor light orbs (blush, lilac, and soft cream) creating a calm editorial atmosphere
 * without floating emojis cluttering or overlapping foreground typography.
 */
@Composable
fun FloatingPetalsBackground(
    parallaxOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier
) {
    val blush = com.example.ui.theme.PhasesRose.copy(alpha = 0.08f)
    val lilac = com.example.ui.theme.PhasesLavender.copy(alpha = 0.07f)
    val sage = com.example.ui.theme.PhasesSage.copy(alpha = 0.06f)

    androidx.compose.foundation.Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(blush, Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.10f),
                radius = size.minDimension * 0.65f
            ),
            radius = size.minDimension * 0.65f,
            center = Offset(size.width * 0.88f, size.height * 0.10f)
        )
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(lilac, Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.48f),
                radius = size.minDimension * 0.58f
            ),
            radius = size.minDimension * 0.58f,
            center = Offset(size.width * 0.12f, size.height * 0.48f)
        )
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(sage, Color.Transparent),
                center = Offset(size.width * 0.82f, size.height * 0.86f),
                radius = size.minDimension * 0.52f
            ),
            radius = size.minDimension * 0.52f,
            center = Offset(size.width * 0.82f, size.height * 0.86f)
        )
    }
}

/**
 * Overlay that renders all active particles with fluid physics and 3D rotation.
 */
@Composable
fun ParticleBurstOverlay(
    controller: ParticleController = LocalParticleController.current,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        controller.activeParticles.forEach { particle ->
            SingleParticleView(
                particle = particle,
                onFinished = { controller.removeParticle(particle) }
            )
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
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, PhasesLine),
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
        modifier = Modifier
            .offset {
                val p = progress.value
                val curX = particle.startX + particle.targetDx * p
                val curY = particle.startY + particle.targetDy * p
                IntOffset(curX.roundToInt(), curY.roundToInt())
            }
            .graphicsLayer {
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
                this.alpha = alpha.coerceIn(0f, 1f)
                this.scaleX = scale
                this.scaleY = scale
                this.rotationZ = rotation
                this.rotationY = rotation * 0.7f
                this.cameraDistance = 12f * density
            }
    )
}
