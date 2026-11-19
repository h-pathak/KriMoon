package com.example.notifications

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.example.R
import java.util.concurrent.ConcurrentHashMap

object SoundHelper {
    private const val TAG = "SoundHelper"

    const val SOUND_CUTE_CHIME = "cute_chime"
    const val SOUND_SPARKLE_BELL = "sparkle_bell"
    const val SOUND_GENTLE_HARP = "gentle_harp"
    const val SOUND_SWEET_BUBBLE = "sweet_bubble"

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

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .build()

    @Volatile
    private var soundPool: SoundPool? = null
    private val soundMap = ConcurrentHashMap<Int, Int>() // resId -> sampleId
    private val pendingPlay = ConcurrentHashMap<Int, Float>() // sampleId -> volume

    @Synchronized
    private fun getOrCreateSoundPool(): SoundPool? {
        if (soundPool == null) {
            try {
                soundPool = SoundPool.Builder()
                    .setMaxStreams(8)
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
     * Plays a specific notification sound by key with zero latency and no MediaPlayer state errors.
     */
    fun playSound(context: Context, soundKey: String, volume: Float = 1.0f) {
        try {
            val resId = getSoundResId(soundKey)
            val pool = getOrCreateSoundPool() ?: return
            val appContext = context.applicationContext

            val cachedSampleId = soundMap[resId]
            if (cachedSampleId != null && cachedSampleId > 0) {
                pool.play(cachedSampleId, volume, volume, 1, 0, 1.0f)
            } else {
                val sampleId = pool.load(appContext, resId, 1)
                if (sampleId > 0) {
                    soundMap[resId] = sampleId
                    pendingPlay[sampleId] = volume
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound: $soundKey", e)
        }
    }

    /**
     * Plays the custom cute chime sound using SoundPool so it is responsive and clearly audible.
     */
    fun playCuteChime(context: Context) {
        playSound(context, SOUND_CUTE_CHIME)
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
        } catch (_: Exception) {}
    }
}
