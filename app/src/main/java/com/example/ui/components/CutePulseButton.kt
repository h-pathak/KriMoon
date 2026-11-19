package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pulsing glowing interactive button for primary actions (Log Period, Log Water).
 * Uses Kotlin Compose Animatable for subtle breathing animations and snappy,
 * responsive bounce pulses upon interaction, coupled with LocalHapticFeedback
 * to make button taps feel tactile, alive, and ultra-satisfying.
 */
@Composable
fun CutePulseButton(
    onClick: () -> Unit,
    text: String,
    icon: String,
    badgeText: String? = null,
    gradientColors: List<Color>,
    glowColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = "cute_pulse_button",
    height: Dp = 54.dp,
    enabled: Boolean = true,
    pulseSpeedMs: Int = 1800
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // 1. Subtle continuous ambient breathing pulse powered by Compose Animatable
    val ambientPulse = remember { Animatable(1f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            val halfDuration = (pulseSpeedMs / 2).coerceAtLeast(400)
            while (isActive) {
                ambientPulse.animateTo(
                    targetValue = 1.028f,
                    animationSpec = tween(durationMillis = halfDuration, easing = FastOutSlowInEasing)
                )
                ambientPulse.animateTo(
                    targetValue = 1.0f,
                    animationSpec = tween(durationMillis = halfDuration, easing = FastOutSlowInEasing)
                )
            }
        } else {
            ambientPulse.snapTo(1f)
        }
    }

    // 2. Interactive pulse triggered when clicked / interacted with
    val interactivePulse = remember { Animatable(1f) }

    // Glow aura transitions
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseSpeedMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.02f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseSpeedMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseSpeedMs + 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    // Trigger pulse animation and haptic feedback
    fun handleButtonClick() {
        if (!enabled) return

        // Provide tactile confirmation to user
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        // Subtle Animatable responsive pulse: quick squeeze then lively spring rebound
        coroutineScope.launch {
            interactivePulse.snapTo(1f)
            interactivePulse.animateTo(0.93f, tween(65, easing = FastOutSlowInEasing))
            interactivePulse.animateTo(
                1.06f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            interactivePulse.animateTo(
                1.0f,
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }

        onClick()
    }

    // Active scale combined from ambient Animatable and interactive Animatable
    val baseScale = if (isPressed) 0.95f else ambientPulse.value
    val currentScale = baseScale * interactivePulse.value

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height + 12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft pulsing radiant glow aura behind button
        Box(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .height(height)
                .graphicsLayer {
                    scaleX = (if (isPressed) 0.94f else glowScale) * interactivePulse.value
                    scaleY = (if (isPressed) 0.94f else glowScale * 1.15f) * interactivePulse.value
                    alpha = if (enabled) glowAlpha else 0f
                }
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent)
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
        )

        // Main Cute Button Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            shadowElevation = if (isPressed) 2.dp else 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer {
                    scaleX = currentScale
                    scaleY = currentScale
                }
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White),
                    enabled = enabled,
                    onClick = { handleButtonClick() }
                )
                .testTag(testTag)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .background(
                        Brush.horizontalGradient(gradientColors)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Diagonal light sheen/shimmer glint
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = shimmerOffset * 300f
                            alpha = 0.25f
                        }
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Top highlight border sheen for 3D depth
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .align(Alignment.TopCenter)
                        .background(Color.White.copy(alpha = 0.45f))
                )

                // Button content (Icon + Text + Badge)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 20.sp,
                        modifier = Modifier.graphicsLayer {
                            scaleX = if (isPressed) 0.9f else 1.05f
                            scaleY = if (isPressed) 0.9f else 1.05f
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        color = Color.White
                    )

                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.28f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
