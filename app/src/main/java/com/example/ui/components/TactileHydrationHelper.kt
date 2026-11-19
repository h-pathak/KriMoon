package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Specialized tactile haptic feedback engine for hydration and liquid tracking.
 * Delivers delicate, organic sensations mimicking water droplets, fluid ripples,
 * bottle clicks, and celebratory milestone waves.
 */
class TactileHydrationHelper(
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

    /**
     * Subtle liquid ripple when logging a glass or sip:
     * Plays a double micro-pulse simulating a drop landing and surface settling.
     */
    fun onDropSip(isGoalReached: Boolean = false) {
        if (isGoalReached) {
            onGoalCelebration()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Waveform: 0ms delay, 10ms light drop, 25ms gap, 16ms settle
                val timings = longArrayOf(0, 10, 25, 16)
                val amplitudes = intArrayOf(0, 80, 0, 140)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect)
                return
            } catch (_: Exception) {}
        }
        performMicroPulse(14, 110, HapticFeedbackType.TextHandleMove)
    }

    /**
     * Goal achieved celebration cascade:
     * 3 ascending rhythmic waves that feel like celebratory ripples expanding.
     */
    fun onGoalCelebration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val timings = longArrayOf(0, 12, 40, 18, 40, 28)
                val amplitudes = intArrayOf(0, 100, 0, 170, 0, 240)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vibrator?.vibrate(effect)
                return
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
                return
            } catch (_: Exception) {}
        }

        performMicroPulse(26, 200, HapticFeedbackType.LongPress)
    }

    /**
     * Interactive cup tap feedback.
     */
    fun onCupTapped(isNowFilled: Boolean) {
        if (isNowFilled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                    return
                } catch (_: Exception) {}
            }
            performMicroPulse(15, 130, HapticFeedbackType.TextHandleMove)
        } else {
            // Soft retraction tick
            performMicroPulse(8, 60, HapticFeedbackType.TextHandleMove)
        }
    }

    /**
     * Sloshing fluid wave vibration when tilting or interacting with the 3D tumbler.
     */
    fun onWaveSlosh() {
        performMicroPulse(7, 45, HapticFeedbackType.TextHandleMove)
    }

    /**
     * Quick preset volume feedback (+1 Glass, +Bottle, +Sip).
     */
    fun onPresetLogged(glasses: Int) {
        when {
            glasses >= 2 -> {
                // Heavier double surge for a full bottle
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        val timings = longArrayOf(0, 12, 30, 20)
                        val amplitudes = intArrayOf(0, 110, 0, 180)
                        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                        vibrator?.vibrate(effect)
                        return
                    } catch (_: Exception) {}
                }
                performMicroPulse(20, 160, HapticFeedbackType.LongPress)
            }
            else -> onDropSip(false)
        }
    }

    /**
     * Step detent when adjusting the daily water goal.
     */
    fun onGoalAdjusted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                return
            } catch (_: Exception) {}
        }
        performMicroPulse(9, 70, HapticFeedbackType.TextHandleMove)
    }

    /**
     * Deduction warning micro-tick when decrementing water.
     */
    fun onDeduct() {
        performMicroPulse(10, 80, HapticFeedbackType.TextHandleMove)
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
