package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tactile feedback manager crafted specifically for physical, elastic stress-reliever manipulation.
 * Produces organic, subtle haptic patterns across dragging, stretching, tension limits,
 * release snapping, spring bouncing, and celebratory pops.
 */
class TactileStressHelper(
    private val context: Context,
    private val composeHaptic: HapticFeedback? = null
) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private var lastStretchTickDistance = 0f

    /**
     * Subtle micro-tick on grab/touch-down: confirms finger contact.
     */
    fun onTouchGrab() {
        performMicroPulse(durationMs = 8, amplitude = 40, fallback = HapticFeedbackType.TextHandleMove)
    }

    /**
     * Progressive elastic stretch detents:
     * As user pulls the mochi/flower farther, generates gentle tactile detents.
     * The ticks slightly scale in crispness as tension builds up.
     */
    fun onDragStretch(currentDistancePx: Float, maxDistancePx: Float) {
        val step = 28f
        val delta = abs(currentDistancePx - lastStretchTickDistance)
        if (delta >= step) {
            lastStretchTickDistance = currentDistancePx
            val tensionRatio = (currentDistancePx / maxDistancePx).coerceIn(0f, 1f)
            val amplitude = (35 + (tensionRatio * 75)).roundToInt().coerceIn(30, 140)
            val duration = if (tensionRatio > 0.6f) 10L else 7L
            performMicroPulse(durationMs = duration, amplitude = amplitude, fallback = HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Elastic limit detent: triggered when user reaches the rubber-band stretch threshold.
     */
    fun onMaxTensionReached() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                return
            } catch (_: Exception) {}
        }
        performMicroPulse(durationMs = 18, amplitude = 170, fallback = HapticFeedbackType.LongPress)
    }

    /**
     * Tactile snap when the finger releases the stretched stress-reliever.
     */
    fun onElasticRelease(velocityLength: Float) {
        lastStretchTickDistance = 0f
        if (velocityLength > 500f) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    return
                } catch (_: Exception) {}
            }
            performMicroPulse(durationMs = 16, amplitude = 160, fallback = HapticFeedbackType.TextHandleMove)
        } else {
            performMicroPulse(durationMs = 10, amplitude = 90, fallback = HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Decaying spring bounce tactile cues as the object oscillates back and forth.
     */
    fun onSpringBounce(bounceNumber: Int) {
        val (dur, amp) = when (bounceNumber) {
            1 -> 12L to 110
            2 -> 8L to 65
            else -> 6L to 35
        }
        performMicroPulse(durationMs = dur, amplitude = amp, fallback = HapticFeedbackType.TextHandleMove)
    }

    /**
     * Crisp double-pop sensation for squish celebration.
     */
    fun onSquishPop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                return
            } catch (_: Exception) {}
        }
        performMicroPulse(durationMs = 22, amplitude = 180, fallback = HapticFeedbackType.LongPress)
    }

    /**
     * High-speed spinner ratchet ticks.
     */
    fun onSpinTick() {
        performMicroPulse(durationMs = 6, amplitude = 50, fallback = HapticFeedbackType.TextHandleMove)
    }

    /**
     * Crisp, resonant mechanical ratchet tick for full square card spinning.
     */
    fun onCardSpinTick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return
            } catch (_: Exception) {}
        }
        performMicroPulse(durationMs = 8, amplitude = 75, fallback = HapticFeedbackType.TextHandleMove)
    }

    /**
     * Gentle breathing rhythm pulse.
     */
    fun onBreathPulse() {
        performMicroPulse(durationMs = 25, amplitude = 60, fallback = HapticFeedbackType.TextHandleMove)
    }

    fun reset() {
        lastStretchTickDistance = 0f
    }

    private fun performMicroPulse(durationMs: Long, amplitude: Int, fallback: HapticFeedbackType) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(
                        durationMs.coerceAtLeast(1L),
                        amplitude.coerceIn(1, 255)
                    )
                    vibrator.vibrate(effect)
                    return
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                    return
                }
            }
        } catch (_: Exception) {}

        try {
            composeHaptic?.performHapticFeedback(fallback)
        } catch (_: Exception) {}
    }
}

/**
 * Refined, tiered haptic feedback engine for everyday UI interactions.
 * Differentiates light navigation ticks, crisp tactile clicks, warm double confirmations,
 * and gentle magnetic thuds without harsh buzzes.
 */
object AestheticHaptics {
    private fun resolveVibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    /** Feather-light tick for tab switches, chips, and subtle selection changes. */
    fun softTick(context: Context, fallback: HapticFeedback? = null) {
        val vib = resolveVibrator(context)
        try {
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    return
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(7L, 55))
                    return
                }
            }
        } catch (_: Exception) {}
        try { fallback?.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
    }

    /** Crisp mechanical click for cards, primary CTAs, and toggle actions. */
    fun crispClick(context: Context, fallback: HapticFeedback? = null) {
        val vib = resolveVibrator(context)
        try {
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    return
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(11L, 110))
                    return
                }
            }
        } catch (_: Exception) {}
        try { fallback?.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
    }

    /** Warm rhythmic double-pulse for approvals, matches, completions, and reveals. */
    fun warmConfirm(context: Context, fallback: HapticFeedback? = null) {
        val vib = resolveVibrator(context)
        try {
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0L, 10L, 42L, 16L),
                            intArrayOf(0, 95, 0, 155),
                            -1
                        )
                    )
                    return
                }
            }
        } catch (_: Exception) {}
        try { fallback?.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
    }

    /** Soft magnetic detent for locking choices or snapping cards/dials. */
    fun gentleThud(context: Context, fallback: HapticFeedback? = null) {
        val vib = resolveVibrator(context)
        try {
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                    return
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(16L, 145))
                    return
                }
            }
        } catch (_: Exception) {}
        try { fallback?.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
    }
}

