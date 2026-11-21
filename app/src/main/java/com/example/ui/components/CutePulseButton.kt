package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var tapOrigin by remember { mutableStateOf(Offset.Unspecified) }
    val lightWave = remember { Animatable(0f) }

    // Interactive pulse triggered when clicked / interacted with
    val interactivePulse = remember { Animatable(1f) }

    // Trigger tactile light wave, spring rebound, and refined haptic feedback
    fun handleButtonClick() {
        if (!enabled) return

        AestheticHaptics.crispClick(context, haptic)

        coroutineScope.launch {
            launch {
                lightWave.snapTo(0.05f)
                lightWave.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                lightWave.snapTo(0f)
            }
            interactivePulse.snapTo(1f)
            interactivePulse.animateTo(0.95f, tween(65, easing = FastOutSlowInEasing))
            interactivePulse.animateTo(
                1.03f,
                spring(
                    dampingRatio = 0.68f,
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

    val baseScale = if (isPressed) 0.97f else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        // Main Editorial Primary Button Pill
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            shadowElevation = if (isPressed) 1.dp else 5.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Press) {
                                event.changes.firstOrNull()?.let { tapOrigin = it.position }
                            }
                        }
                    }
                }
                .graphicsLayer {
                    val s = baseScale * interactivePulse.value
                    scaleX = s
                    scaleY = s
                    rotationX = if (isPressed) 2.2f else 0f
                    cameraDistance = 16f * density
                }
                .clip(RoundedCornerShape(18.dp))
                .drawWithContent {
                    drawContent()
                    val p = lightWave.value
                    if (p in 0.01f..0.99f) {
                        val center = if (tapOrigin != Offset.Unspecified) tapOrigin else Offset(size.width / 2f, size.height / 2f)
                        val r = (size.maxDimension * 0.9f * p).coerceAtLeast(1f)
                        val a = (1f - p) * 0.36f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = a), Color.Transparent),
                                center = center,
                                radius = r
                            ),
                            radius = r,
                            center = center
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = a * 1.2f),
                            radius = r * 0.7f,
                            center = center,
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }
                }
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
                // Subtle static diagonal silk highlight (zero idle GPU frames)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.14f),
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.06f)
                                )
                            )
                        )
                )

                // Top highlight hairline
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopCenter)
                        .background(Color.White.copy(alpha = 0.35f))
                )

                // Button content (Icon + Text + Badge)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        maxLines = 1,
                        color = Color.White
                    )

                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.24f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
