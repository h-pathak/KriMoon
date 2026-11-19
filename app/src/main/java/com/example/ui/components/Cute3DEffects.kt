package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.SoundHelper
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import kotlinx.coroutines.launch

/**
 * Modifier that adds interactive 3D perspective depth, ambient breathing tilt,
 * and tactile press bounce. Tapping or dragging tilts the component along X/Y axes with
 * realistic camera distance and responsive spring physics.
 */
fun Modifier.cute3DCard(
    maxTilt: Float = 6f,
    cornerRadius: Dp = 20.dp,
    shadowColor: Color = PhasesPlum.copy(alpha = 0.16f),
    ambientWobble: Boolean = true
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val pressScale = remember { Animatable(1f) }

    // Subtle 3D ambient breathing tilt
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_3d_wobble")
    val ambientAngleX by infiniteTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_rot_x"
    )
    val ambientAngleY by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = -1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_rot_y"
    )

    this
        .shadow(
            elevation = 10.dp,
            shape = RoundedCornerShape(cornerRadius),
            spotColor = shadowColor,
            ambientColor = shadowColor
        )
        .graphicsLayer {
            rotationX = rotX.value + (if (ambientWobble) ambientAngleX else 0f)
            rotationY = rotY.value + (if (ambientWobble) ambientAngleY else 0f)
            scaleX = pressScale.value
            scaleY = pressScale.value
            cameraDistance = 14f * density
        }
        .pointerInput(Unit) {
            detectDragGestures(
                onDragStart = {
                    coroutineScope.launch {
                        pressScale.animateTo(0.97f, spring(stiffness = Spring.StiffnessMediumLow))
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    coroutineScope.launch {
                        rotX.snapTo((rotX.value - dragAmount.y * 0.12f).coerceIn(-maxTilt, maxTilt))
                        rotY.snapTo((rotY.value + dragAmount.x * 0.12f).coerceIn(-maxTilt, maxTilt))
                    }
                },
                onDragEnd = {
                    coroutineScope.launch {
                        launch { rotX.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
                        launch { rotY.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
                        launch { pressScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)) }
                    }
                },
                onDragCancel = {
                    coroutineScope.launch {
                        launch { rotX.animateTo(0f, spring(dampingRatio = 0.6f)) }
                        launch { rotY.animateTo(0f, spring(dampingRatio = 0.6f)) }
                        launch { pressScale.animateTo(1f, spring(dampingRatio = 0.5f)) }
                    }
                }
            )
        }
}

/**
 * Modifier for satisfying tactile press feel: bouncy spring compression + haptic feedback.
 */
fun Modifier.cuteBouncyClick(
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cute_bouncy_scale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = PhasesRose),
            enabled = enabled,
            onClick = {
                haptic.performHapticFeedback(hapticType)
                onClick()
            }
        )
}

/**
 * Ultra-cute 3D "Add" Button for primary creation flows (Add Note, Add Item, Add Medication, Log Period).
 * Features 3D mechanical push depth, ambient pastel glow aura, animated sparkles, celebration particles,
 * and satisfying haptic vibration.
 */
@Composable
fun CuteAddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "✨",
    sparkleEmoji: String = "🌸",
    badgeText: String? = null,
    gradientColors: List<Color> = listOf(PhasesRose, Color(0xFFE0528D), PhasesPlum),
    glowColor: Color = PhasesRose.copy(alpha = 0.5f),
    enabled: Boolean = true,
    playChime: Boolean = true,
    testTag: String = "cute_add_button"
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var buttonCenter by remember { mutableStateOf(Offset.Zero) }

    // Breathing sparkle rotation
    val infiniteTransition = rememberInfiniteTransition(label = "cute_sparkle")
    val sparkleAngle by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_angle"
    )

    // Idle invitation pulse
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_pulse"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else idlePulse,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cute_add_scale"
    )

    val elevation = if (isPressed) 2.dp else 8.dp
    val pressOffset = if (isPressed) 3.5.dp else 0.dp

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                buttonCenter = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .offset(y = pressOffset)
    ) {
        // 3D bottom bevel shadow extrusion for realistic mechanical tactile depth
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 4.dp)
                .background(
                    color = PhasesPlum.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(20.dp)
                )
        )

        // Main button surface
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            shadowElevation = elevation,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White),
                    enabled = enabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (playChime) {
                            SoundHelper.playCuteChime(context)
                        }
                        particleController.spawn(
                            centerX = if (buttonCenter != Offset.Zero) buttonCenter.x else 500f,
                            centerY = if (buttonCenter != Offset.Zero) buttonCenter.y else 700f,
                            emojis = listOf("🌸", "✨", "💖", "🌷", "🎀", "⭐", "💫"),
                            count = 16
                        )
                        onClick()
                    }
                )
                .testTag(testTag)
        ) {
            Box(
                modifier = Modifier
                    .background(Brush.horizontalGradient(gradientColors))
                    .padding(horizontal = 20.dp, vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                // Top white highlight sheen for 3D glass gloss
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 19.sp,
                        modifier = Modifier.graphicsLayer { rotationZ = sparkleAngle }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.4.sp
                        ),
                        color = Color.White
                    )

                    if (!badgeText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color.White.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sparkleEmoji,
                        fontSize = 16.sp,
                        modifier = Modifier.graphicsLayer { rotationZ = -sparkleAngle }
                    )
                }
            }
        }
    }
}

/**
 * 3D Floating Action Button with pulsing pastel halo, rotating plus icon,
 * bouncy physics, and haptic feedback.
 */
@Composable
fun CuteAddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "➕",
    haloColor: Color = PhasesRose,
    testTag: String = "cute_add_fab"
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val particleController = LocalParticleController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var fabCenter by remember { mutableStateOf(Offset.Zero) }

    val infiniteTransition = rememberInfiniteTransition(label = "fab_spin")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_scale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fab_scale"
    )

    Box(
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                fabCenter = Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
            },
        contentAlignment = Alignment.Center
    ) {
        // Pulsing ambient halo
        Box(
            modifier = Modifier
                .size(66.dp)
                .graphicsLayer {
                    scaleX = haloScale
                    scaleY = haloScale
                    alpha = haloAlpha
                }
                .background(haloColor, CircleShape)
        )

        // 3D Bottom drop shadow
        Box(
            modifier = Modifier
                .size(56.dp)
                .offset(y = 3.dp)
                .background(PhasesPlum.copy(alpha = 0.4f), CircleShape)
        )

        // Main sphere button
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = if (isPressed) 2.dp else 8.dp,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        SoundHelper.playCuteChime(context)
                        particleController.spawn(
                            centerX = if (fabCenter != Offset.Zero) fabCenter.x else 500f,
                            centerY = if (fabCenter != Offset.Zero) fabCenter.y else 700f,
                            emojis = listOf("🌸", "💊", "✨", "💖", "🌷", "⭐"),
                            count = 18
                        )
                        onClick()
                    }
                )
                .testTag(testTag)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFFF85A2),
                                PhasesRose,
                                PhasesPlum
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Top spherical specular highlight
                Box(
                    modifier = Modifier
                        .size(36.dp, 18.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = 2.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.55f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                Text(
                    text = icon,
                    fontSize = 24.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Tactile 3D option/filter chip with built-in haptic feedback,
 * spring press squeeze, soft selected glow aura, and cute checkmark / emoji.
 */
@Composable
fun CuteOptionChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    selectedColor: Color = PhasesRose,
    unselectedColor: Color = MaterialTheme.colorScheme.surface,
    testTag: String = "cute_option_chip"
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else if (selected) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_scale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) selectedColor else unselectedColor,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) selectedColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = if (selected) 4.dp else 1.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = selectedColor),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (emoji != null) {
                Text(text = emoji, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                ),
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
            )
            if (selected) {
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "✓",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 3D Quick-Action Bubble for the Home Screen.
 * Provides instant 1-tap logging for busy users with cute tactile popping animation.
 */
@Composable
fun CuteQuickActionBubble(
    emoji: String,
    label: String,
    subLabel: String,
    gradientColors: List<Color>,
    glowColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "quick_action_bubble"
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bubble_scale"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, glowColor.copy(alpha = 0.4f)),
        shadowElevation = if (isPressed) 2.dp else 7.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = glowColor),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 3D Sphere Emoji Badge
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(4.dp, CircleShape, spotColor = glowColor)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(gradientColors)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 20.sp)
                }

                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = glowColor
                    )
                }
            }
        }
    }
}

/**
 * Friendly, heartwarming 3D empty state card for empty notes, medications,
 * shopping lists, or history. Encourages the user with gentle words and a direct "+ Add" action.
 */
@Composable
fun CuteEmptyStateCard(
    emoji: String,
    title: String,
    description: String,
    buttonText: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "cute_empty_state_card"
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.2.dp, PhasesRose.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 4f, cornerRadius = 24.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(PhasesRose.copy(alpha = 0.12f), CircleShape)
                    .border(1.5.dp, PhasesRose.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 32.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            CuteAddButton(
                text = buttonText,
                onClick = onButtonClick,
                icon = "✨",
                sparkleEmoji = "🌸",
                testTag = "${testTag}_button"
            )
        }
    }
}
