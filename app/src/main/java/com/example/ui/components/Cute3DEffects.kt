package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.SoundHelper
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import kotlinx.coroutines.launch
import kotlin.math.abs

private class TouchOffsetRef(var offset: Offset = Offset.Unspecified)

/**
 * Scroll-safe, zero-recomposition 3D perspective tilt + pearlescent touch-origin glass sheen modifier.
 *
 * - Reads all animation values strictly inside `graphicsLayer` and `drawWithContent` (draw phase only),
 *   so touching, tilting, scrolling, or clicking causes ZERO Compose recompositions.
 * - Uses a non-consuming PointerEventPass.Initial observer so vertical scrolling in
 *   LazyColumn / verticalScroll is NEVER intercepted or stuttered.
 * - Optionally accepts [onClick] so interactive cards run in a single GPU layer and single pointer loop.
 */
fun Modifier.cute3DCard(
    maxTilt: Float = 4f,
    cornerRadius: Dp = 20.dp,
    shadowColor: Color = PhasesPlum.copy(alpha = 0.14f),
    ambientWobble: Boolean = false,
    accentColor: Color = PhasesRose,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val safeMaxTilt = maxTilt.coerceIn(1.5f, 4.5f)
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val touchRef = remember { TouchOffsetRef() }

    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val pressScale = remember { Animatable(1f) }
    val sheenAlpha = remember { Animatable(0f) }
    val bloomProgress = remember { Animatable(0f) }

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val tiltSpring = remember {
        spring<Float>(dampingRatio = 0.76f, stiffness = Spring.StiffnessMediumLow)
    }
    val scaleSpring = remember {
        spring<Float>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium)
    }

    var base = this
        .pointerInput(safeMaxTilt, enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                var pressOrigin = Offset.Zero
                var releasedForScroll = false
                var tiltActive = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: continue
                    val pos = change.position
                    val w = size.width.coerceAtLeast(1).toFloat()
                    val h = size.height.coerceAtLeast(1).toFloat()

                    val hasPressed = event.changes.any { it.pressed }
                    when {
                        event.type == PointerEventType.Press && hasPressed -> {
                            pressOrigin = pos
                            releasedForScroll = false
                            tiltActive = true
                            touchRef.offset = pos
                            val normX = ((pos.x / w) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                            val normY = ((pos.y / h) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                            val tx = -normY * safeMaxTilt
                            val ty = normX * safeMaxTilt
                            val targetScale = if (onClick != null) 0.968f else 0.988f
                            scope.launch { rotX.animateTo(tx, tiltSpring) }
                            scope.launch { rotY.animateTo(ty, tiltSpring) }
                            scope.launch { pressScale.animateTo(targetScale, scaleSpring) }
                            scope.launch { sheenAlpha.animateTo(0.20f, tween(180, easing = FastOutSlowInEasing)) }
                        }
                        event.type == PointerEventType.Move && hasPressed -> {
                            val dy = abs(pos.y - pressOrigin.y)
                            val dx = abs(pos.x - pressOrigin.x)
                            // Immediately flatten tilt if the user starts scrolling so scrolling stays locked 60/120fps
                            if (dy > 16f && dy > dx * 1.1f) {
                                if (!releasedForScroll) {
                                    releasedForScroll = true
                                    tiltActive = false
                                    scope.launch { rotX.animateTo(0f, tiltSpring) }
                                    scope.launch { rotY.animateTo(0f, tiltSpring) }
                                    scope.launch { pressScale.animateTo(1f, scaleSpring) }
                                    scope.launch { sheenAlpha.animateTo(0f, tween(150)) }
                                }
                            } else if (!releasedForScroll && tiltActive) {
                                touchRef.offset = pos
                                val normX = ((pos.x / w) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                                val normY = ((pos.y / h) - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                                scope.launch { rotX.snapTo(-normY * safeMaxTilt) }
                                scope.launch { rotY.snapTo(normX * safeMaxTilt) }
                            }
                        }
                        !hasPressed || event.type == PointerEventType.Release || event.type == PointerEventType.Exit -> {
                            if (tiltActive) {
                                tiltActive = false
                                scope.launch { rotX.animateTo(0f, tiltSpring) }
                                scope.launch { rotY.animateTo(0f, tiltSpring) }
                                scope.launch { pressScale.animateTo(1f, scaleSpring) }
                                scope.launch { sheenAlpha.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
                            }
                        }
                    }
                    // Intentionally NEVER calling change.consume() so scrolling & clicks stay completely unblocked
                }
            }
        }
        .graphicsLayer {
            rotationX = rotX.value
            rotationY = rotY.value
            val s = pressScale.value
            scaleX = s
            scaleY = s
            cameraDistance = 18f * density
        }
        .shadow(
            elevation = 4.dp,
            shape = shape,
            spotColor = shadowColor,
            ambientColor = shadowColor
        )
        .clip(shape)
        .drawWithContent {
            drawContent()
            val sheen = sheenAlpha.value
            val pt = touchRef.offset
            if (sheen > 0.005f && pt != Offset.Unspecified) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = sheen),
                            accentColor.copy(alpha = sheen * 0.28f),
                            Color.Transparent
                        ),
                        center = pt,
                        radius = size.maxDimension * 0.62f
                    )
                )
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = sheen * 1.4f),
                            Color.Transparent,
                            accentColor.copy(alpha = sheen * 0.75f)
                        )
                    ),
                    cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            val p = bloomProgress.value
            if (p in 0.001f..0.999f) {
                val center = if (pt != Offset.Unspecified) pt else Offset(size.width / 2f, size.height / 2f)
                val maxR = size.maxDimension * 0.85f
                val currentR = (maxR * p).coerceAtLeast(1f)
                val alpha = (1f - p) * 0.26f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = alpha),
                            accentColor.copy(alpha = alpha * 0.42f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = currentR
                    ),
                    radius = currentR,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 1.1f),
                    radius = currentR * 0.72f,
                    center = center,
                    style = Stroke(width = (1.4.dp.toPx() * (1f - p * 0.6f)).coerceAtLeast(0.5f))
                )
            }
        }

    if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        base = base.clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = accentColor.copy(alpha = 0.35f)),
            enabled = enabled,
            onClick = {
                if (hapticType == HapticFeedbackType.LongPress) {
                    AestheticHaptics.gentleThud(context, haptic)
                } else {
                    AestheticHaptics.crispClick(context, haptic)
                }
                scope.launch {
                    bloomProgress.snapTo(0.05f)
                    bloomProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                    )
                    bloomProgress.snapTo(0f)
                }
                onClick()
            }
        )
    }
    base
}

/**
 * Refined, non-cliché tactile click modifier (zero-recomposition draw-phase animation):
 * - Magnetic spring press compression (0.962f)
 * - Touch-origin expanding satin light ring & radial glass bloom
 * - Differentiated native haptic click via AestheticHaptics
 * Never intercepts parent vertical scrolling.
 */
fun Modifier.cuteBouncyClick(
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    enabled: Boolean = true,
    accentColor: Color = PhasesRose,
    onClick: () -> Unit
): Modifier = composed {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    val tapRef = remember { TouchOffsetRef() }
    val scaleAnim = remember { Animatable(1f) }
    val bloomProgress = remember { Animatable(0f) }
    val scaleSpring = remember {
        spring<Float>(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium)
    }

    this
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                var pressedDown = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val hasPressed = event.changes.any { it.pressed }
                    if (event.type == PointerEventType.Press && hasPressed) {
                        event.changes.firstOrNull()?.let { tapRef.offset = it.position }
                        pressedDown = true
                        scope.launch { scaleAnim.animateTo(0.962f, scaleSpring) }
                    } else if (pressedDown && (!hasPressed || event.type == PointerEventType.Release || event.type == PointerEventType.Exit)) {
                        pressedDown = false
                        scope.launch { scaleAnim.animateTo(1f, scaleSpring) }
                    }
                }
            }
        }
        .graphicsLayer {
            val s = scaleAnim.value
            scaleX = s
            scaleY = s
        }
        .drawWithContent {
            drawContent()
            val p = bloomProgress.value
            if (p in 0.001f..0.999f) {
                val pt = tapRef.offset
                val center = if (pt != Offset.Unspecified) pt else Offset(size.width / 2f, size.height / 2f)
                val maxR = size.maxDimension * 0.88f
                val currentR = (maxR * p).coerceAtLeast(1f)
                val alpha = (1f - p) * 0.28f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = alpha),
                            accentColor.copy(alpha = alpha * 0.45f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = currentR
                    ),
                    radius = currentR,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 1.15f),
                    radius = currentR * 0.72f,
                    center = center,
                    style = Stroke(width = (1.5.dp.toPx() * (1f - p * 0.6f)).coerceAtLeast(0.5f))
                )
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true, color = accentColor.copy(alpha = 0.35f)),
            enabled = enabled,
            onClick = {
                if (hapticType == HapticFeedbackType.LongPress) {
                    AestheticHaptics.gentleThud(context, haptic)
                } else {
                    AestheticHaptics.crispClick(context, haptic)
                }
                scope.launch {
                    bloomProgress.snapTo(0.05f)
                    bloomProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                    )
                    bloomProgress.snapTo(0f)
                }
                onClick()
            }
        )
}

/**
 * Editorial satin-glass action button for primary creation flows.
 * Features subtle 3D press tilt, touch-origin light sweep, and crisp native haptic click
 * (no cliché floating emoji spam).
 */
@Composable
fun CuteAddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    sparkleEmoji: String = "",
    badgeText: String? = null,
    gradientColors: List<Color> = listOf(PhasesRose, Color(0xFFD84E86), PhasesPlum),
    glowColor: Color = PhasesRose.copy(alpha = 0.4f),
    enabled: Boolean = true,
    playChime: Boolean = false,
    testTag: String = "cute_add_button"
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val sweepProgress = remember { Animatable(0f) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.965f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cute_add_scale"
    )

    val elevation = if (isPressed) 1.dp else 5.dp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationX = if (isPressed) 2.2f else 0f
                cameraDistance = 16f * density
            }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            shadowElevation = elevation,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .drawWithContent {
                    drawContent()
                    val s = sweepProgress.value
                    if (s in 0.01f..0.99f) {
                        val x = size.width * (s * 1.6f - 0.3f)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = (1f - s) * 0.42f),
                                    Color.Transparent
                                ),
                                start = Offset(x - size.width * 0.35f, 0f),
                                end = Offset(x + size.width * 0.35f, size.height)
                            )
                        )
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White),
                    enabled = enabled,
                    onClick = {
                        AestheticHaptics.crispClick(context, haptic)
                        if (playChime) {
                            SoundHelper.playCelebration(context)
                        }
                        scope.launch {
                            sweepProgress.snapTo(0.02f)
                            sweepProgress.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                            sweepProgress.snapTo(0f)
                        }
                        onClick()
                    }
                )
                .testTag(testTag)
        ) {
            Box(
                modifier = Modifier
                    .background(Brush.horizontalGradient(gradientColors))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Top satin specular rim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.65f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon.isNotBlank()) {
                        Text(
                            text = icon,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        ),
                        maxLines = 1,
                        color = Color.White
                    )

                    if (!badgeText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = Color.White.copy(alpha = 0.24f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (sparkleEmoji.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sparkleEmoji,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Refined glass-sphere Floating Action Button with subtle halo and crisp haptic response.
 */
@Composable
fun CuteAddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "+",
    haloColor: Color = PhasesRose,
    testTag: String = "cute_add_fab"
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "fab_scale"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(haloColor.copy(alpha = 0.18f), CircleShape)
        )

        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            shadowElevation = if (isPressed) 2.dp else 7.dp,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    val s = scaleState.value
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(color = Color.White),
                    onClick = {
                        AestheticHaptics.crispClick(context, haptic)
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
                Box(
                    modifier = Modifier
                        .size(36.dp, 16.dp)
                        .align(Alignment.TopCenter)
                        .offset(y = 2.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.45f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                Text(
                    text = icon,
                    fontSize = 24.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

/**
 * Tactile option/filter chip with soft haptic tick and silky press compression.
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
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else if (selected) 1.02f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chip_scale"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) selectedColor else unselectedColor,
        border = BorderStroke(
            if (selected) 1.4.dp else 1.dp,
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
                    AestheticHaptics.softTick(context, haptic)
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
 * Tactile Quick-Action Bubble with subtle 3D press tilt and crisp haptic feedback.
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
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.2.dp, glowColor.copy(alpha = 0.4f)),
        modifier = modifier
            .cute3DCard(maxTilt = 3f, cornerRadius = 20.dp, shadowColor = glowColor.copy(alpha = 0.22f))
            .cuteBouncyClick(accentColor = glowColor, onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glowColor.copy(alpha = 0.14f),
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
 * Refined 3D empty state card for empty notes, medications, shopping lists, or history.
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
        border = BorderStroke(1.2.dp, PhasesRose.copy(alpha = 0.32f)),
        modifier = modifier
            .fillMaxWidth()
            .cute3DCard(maxTilt = 3.5f, cornerRadius = 24.dp)
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
                icon = "",
                sparkleEmoji = "",
                testTag = "${testTag}_button"
            )
        }
    }
}
