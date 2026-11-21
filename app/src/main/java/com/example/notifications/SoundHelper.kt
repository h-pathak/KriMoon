package com.example.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.example.R
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

object SoundHelper {
    private const val TAG = "SoundHelper"
    private const val PREFS_NAME = "krimoon_audio_prefs"
    private const val KEY_CELEBRATION_SOUNDS = "celebration_sounds_enabled"
    private const val MIN_SFX_COOLDOWN_MS = 160L

    const val SOUND_CUTE_CHIME = "cute_chime"
    const val SOUND_SPARKLE_BELL = "sparkle_bell"
    const val SOUND_GENTLE_HARP = "gentle_harp"
    const val SOUND_SWEET_BUBBLE = "sweet_bubble"

    /**
     * Selective sound categories so audio enhances key moments without becoming noisy or annoying.
     * - ALARM_OR_PREVIEW: Scheduled medication/notification alerts & explicit sound-picker previews (always plays).
     * - CELEBRATION: Rare meaningful milestones (e.g., 100% hydration goal, couple quiz match). Respects quiet toggle.
     * - GAME_SFX: Optional arcade/couple game sound effects (only when game speaker toggle is explicitly turned on).
     */
    enum class SoundCategory {
        ALARM_OR_PREVIEW,
        CELEBRATION,
        GAME_SFX
    }

    data class SoundOption(
        val key: String,
        val displayName: String,
        val emoji: String,
        val resId: Int
    )

    fun getAvailableSounds(): List<SoundOption> = listOf(
        SoundOption(SOUND_CUTE_CHIME, "Sweet Chime", "🌸", R.raw.cute_chime),
        SoundOption(SOUND_SPARKLE_BELL, "Sparkle Bell", "✨", R.raw.sparkle_bell),
        SoundOption(SOUND_GENTLE_HARP, "Gentle Harp", "🎶", R.raw.gentle_harp),
        SoundOption(SOUND_SWEET_BUBBLE, "Sweet Bubble", "🫧", R.raw.sweet_bubble)
    )

    fun getSoundResId(soundKey: String): Int {
        return when (soundKey) {
            SOUND_SPARKLE_BELL -> R.raw.sparkle_bell
            SOUND_GENTLE_HARP -> R.raw.gentle_harp
            SOUND_SWEET_BUBBLE -> R.raw.sweet_bubble
            else -> R.raw.cute_chime
        }
    }

    fun isCelebrationSoundEnabled(context: Context): Boolean {
        return try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_CELEBRATION_SOUNDS, true)
        } catch (_: Exception) {
            true
        }
    }

    fun setCelebrationSoundEnabled(context: Context, enabled: Boolean) {
        try {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CELEBRATION_SOUNDS, enabled)
                .apply()
        } catch (_: Exception) {
        }
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .build()

    @Volatile
    private var soundPool: SoundPool? = null
    private val soundMap = ConcurrentHashMap<Int, Int>() // resId -> sampleId
    private val pendingPlay = ConcurrentHashMap<Int, Float>() // sampleId -> volume
    private val lastPlayedMillis = AtomicLong(0L)

    @Synchronized
    private fun getOrCreateSoundPool(): SoundPool? {
        if (soundPool == null) {
            try {
                soundPool = SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(audioAttributes)
                    .build().apply {
                        setOnLoadCompleteListener { pool, sampleId, status ->
                            if (status == 0) {
                                val volume = pendingPlay.remove(sampleId)
                                if (volume != null) {
                                    pool.play(sampleId, volume, volume, 1, 0, 1.0f)
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SoundPool", e)
            }
        }
        return soundPool
    }

    /**
     * Plays a specific sound with rate-limiting and calm volume scaling so sounds never stack or annoy.
     */
    fun playSound(
        context: Context,
        soundKey: String,
        volume: Float = 0.48f,
        category: SoundCategory = SoundCategory.GAME_SFX
    ) {
        try {
            if (category == SoundCategory.CELEBRATION && !isCelebrationSoundEnabled(context)) {
                return
            }

            // Debounce rapid consecutive SFX (never block scheduled alarms or explicit previews)
            val now = System.currentTimeMillis()
            if (category != SoundCategory.ALARM_OR_PREVIEW) {
                val prev = lastPlayedMillis.get()
                if (now - prev < MIN_SFX_COOLDOWN_MS) return
                if (!lastPlayedMillis.compareAndSet(prev, now)) return
            }

            val calibratedVolume = when (category) {
                SoundCategory.ALARM_OR_PREVIEW -> volume.coerceIn(0.15f, 0.95f)
                SoundCategory.CELEBRATION -> (volume * 0.55f).coerceIn(0.15f, 0.52f)
                SoundCategory.GAME_SFX -> (volume * 0.50f).coerceIn(0.12f, 0.45f)
            }

            val resId = getSoundResId(soundKey)
            val pool = getOrCreateSoundPool() ?: return
            val appContext = context.applicationContext

            val cachedSampleId = soundMap[resId]
            if (cachedSampleId != null && cachedSampleId > 0) {
                pool.play(cachedSampleId, calibratedVolume, calibratedVolume, 1, 0, 1.0f)
            } else {
                val sampleId = pool.load(appContext, resId, 1)
                if (sampleId > 0) {
                    soundMap[resId] = sampleId
                    pendingPlay[sampleId] = calibratedVolume
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound: $soundKey", e)
        }
    }

    /**
     * Gentle milestone chime (respects Quiet Mode preference and plays at a soft, pleasant volume).
     */
    fun playCelebration(context: Context, soundKey: String = SOUND_GENTLE_HARP) {
        playSound(context, soundKey, volume = 0.72f, category = SoundCategory.CELEBRATION)
    }

    /**
     * Explicit alarm / notification / preview chime (always audible at clear alert level).
     */
    fun playAlarmOrPreview(context: Context, soundKey: String) {
        playSound(context, soundKey, volume = 0.88f, category = SoundCategory.ALARM_OR_PREVIEW)
    }

    /**
     * Backwards-compatible helper routed through soft Celebration policy.
     */
    fun playCuteChime(context: Context) {
        playCelebration(context, SOUND_CUTE_CHIME)
    }

    /**
     * Preloads sound samples into memory for instantaneous playback.
     */
    fun preload(context: Context) {
        try {
            val pool = getOrCreateSoundPool() ?: return
            val appContext = context.applicationContext
            getAvailableSounds().forEach { option ->
                if (!soundMap.containsKey(option.resId)) {
                    val id = pool.load(appContext, option.resId, 1)
                    if (id > 0) {
                        soundMap[option.resId] = id
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
