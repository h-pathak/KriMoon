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
 * 3D Ambient floating petals and sparkles drifting across the background with parallax depth.
 */
@Composable
fun FloatingPetalsBackground(
    parallaxOffset: Offset = Offset.Zero,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "petals_drift")

    // Drifts for 4 decorative ambient petals
    val drift1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_1"
    )

    val drift2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_2"
    )

    val drift3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(8400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_3"
    )

    val drift4 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_4"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = parallaxOffset.x * 0.6f
                translationY = parallaxOffset.y * 0.6f
            }
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        if (widthPx > 0 && heightPx > 0) {
            // Petal 1 (🌸)
            val p1Y = (1f - drift1) * heightPx * 0.85f + heightPx * 0.15f
            val p1X = widthPx * 0.12f + sin(drift1 * 6f) * 28f
            val p1Alpha = when {
                drift1 < 0.15f -> drift1 / 0.15f
                drift1 > 0.85f -> (1f - drift1) / 0.15f
                else -> 0.75f
            }
            Text(
                text = "🌸",
                fontSize = 18.sp,
                modifier = Modifier
                    .offset { IntOffset(p1X.roundToInt(), p1Y.roundToInt()) }
                    .graphicsLayer {
                        rotationZ = drift1 * 360f
                        rotationY = drift1 * 180f
                        alpha = p1Alpha * 0.7f
                        scaleX = 0.9f
                        scaleY = 0.9f
                    }
            )

            // Petal 2 (✨)
            val p2Y = (1f - drift2) * heightPx * 0.75f + heightPx * 0.2f
            val p2X = widthPx * 0.78f - cos(drift2 * 5f) * 35f
            val p2Alpha = when {
                drift2 < 0.15f -> drift2 / 0.15f
                drift2 > 0.85f -> (1f - drift2) / 0.15f
                else -> 0.65f
            }
            Text(
                text = "✨",
                fontSize = 16.sp,
                modifier = Modifier
                    .offset { IntOffset(p2X.roundToInt(), p2Y.roundToInt()) }
                    .graphicsLayer {
                        rotationZ = -drift2 * 280f
                        rotationX = drift2 * 140f
                        alpha = p2Alpha * 0.65f
                        scaleX = 0.8f
                        scaleY = 0.8f
                    }
            )

            // Petal 3 (🌸)
            val p3Y = (1f - drift3) * heightPx * 0.9f + heightPx * 0.05f
            val p3X = widthPx * 0.45f + sin(drift3 * 7f) * 30f
            val p3Alpha = when {
                drift3 < 0.15f -> drift3 / 0.15f
                drift3 > 0.85f -> (1f - drift3) / 0.15f
                else -> 0.8f
            }
            Text(
                text = "🌸",
                fontSize = 20.sp,
                modifier = Modifier
                    .offset { IntOffset(p3X.roundToInt(), p3Y.roundToInt()) }
                    .graphicsLayer {
                        rotationZ = drift3 * 240f
                        rotationY = -drift3 * 160f
                        alpha = p3Alpha * 0.65f
                        scaleX = 0.95f
                        scaleY = 0.95f
                    }
            )

            // Petal 4 (💫)
            val p4Y = (1f - drift4) * heightPx * 0.8f + heightPx * 0.1f
            val p4X = widthPx * 0.26f + cos(drift4 * 6f) * 36f
            val p4Alpha = when {
                drift4 < 0.15f -> drift4 / 0.15f
                drift4 > 0.85f -> (1f - drift4) / 0.15f
                else -> 0.7f
            }
            Text(
                text = "💫",
                fontSize = 17.sp,
                modifier = Modifier
                    .offset { IntOffset(p4X.roundToInt(), p4Y.roundToInt()) }
                    .graphicsLayer {
                        rotationZ = -drift4 * 320f
                        alpha = p4Alpha * 0.6f
                        scaleX = 0.85f
                        scaleY = 0.85f
                    }
            )
        }
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

    val p = progress.value
    val curX = particle.startX + particle.targetDx * p
    val curY = particle.startY + particle.targetDy * p
    val alpha = when {
        p < 0.15f -> p / 0.15f
        p > 0.65f -> (1f - p) / 0.35f
        else -> 1f
    }
    val scale = if (p < 0.2f) (p / 0.2f) * particle.maxScale else particle.maxScale * (1f - (p - 0.2f) * 0.35f)
    val rotation = particle.targetRot * p

    Text(
        text = particle.emoji,
        fontSize = 24.sp,
        modifier = Modifier
            .offset { IntOffset(curX.roundToInt(), curY.roundToInt()) }
            .graphicsLayer {
                this.alpha = alpha.coerceIn(0f, 1f)
                this.scaleX = scale
                this.scaleY = scale
                this.rotationZ = rotation
                this.rotationY = rotation * 0.7f
                this.cameraDistance = 12f * density
            }
    )
}
