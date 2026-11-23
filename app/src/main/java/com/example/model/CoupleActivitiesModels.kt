package com.example.model

import org.json.JSONArray
import org.json.JSONObject

data class DarePrompt(
    val id: String,
    val isDare: Boolean,
    val text: String,
    val spiceLevel: SpiceLevel
)

data class DareSyncState(
    val roundIndex: Int = 0,
    val isDare: Boolean = true,
    val promptId: String = "",
    val promptText: String = "",
    val challengerName: String = "",
    val status: String = "OPEN", // "OPEN", "CHALLENGED", "COMPLETED", "SWAPPED"
    val completedCount: Int = 0
)

data class WavelengthSpectrum(
    val id: String,
    val leftLabel: String,
    val rightLabel: String,
    val leftEmoji: String,
    val rightEmoji: String,
    val spiceLevel: SpiceLevel
)

data class WavelengthSyncState(
    val roundIndex: Int = 0,
    val spectrumIndex: Int = 0,
    val targetPos: Float = 0.5f, // 0.12f .. 0.88f
    val clueWord: String = "",
    val clueAuthor: String = "",
    val guessPos: Float? = null,
    val guesserName: String = "",
    val phase: String = "CLUE", // "CLUE", "GUESS", "REVEAL"
    val lastScore: Int = 0,
    val totalSyncPoints: Int = 0,
    val roundsPlayed: Int = 0
)

data class DateNightIdea(
    val id: String,
    val title: String,
    val emoji: String,
    val vibeNote: String,
    val spiceLevel: SpiceLevel
)

data class SecretWhisperNote(
    val id: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRevealed: Boolean = false
)

data class DateWhisperSyncState(
    val myLikedIds: Set<String> = emptySet(),
    val mySkippedIds: Set<String> = emptySet(),
    val partnerLikedIds: Set<String> = emptySet(),
    val whispers: List<SecretWhisperNote> = emptyList()
) {
    val matchedIds: Set<String>
        get() = myLikedIds.intersect(partnerLikedIds)
}

object CoupleActivitiesDeck {

    val darePrompts: List<DarePrompt> = listOf(
        // SWEET TRUTHS
        DarePrompt("st1", false, "What is the very first tiny detail about me that made your heart melt?", SpiceLevel.SWEET),
        DarePrompt("st2", false, "Which quiet memory of us do you replay when you miss me?", SpiceLevel.SWEET),
        DarePrompt("st3", false, "What song instantly reminds you of me every single time?", SpiceLevel.SWEET),
        DarePrompt("st4", false, "What is one dream Sunday morning you want us to share soon?", SpiceLevel.SWEET),
        DarePrompt("st5", false, "Which habit of mine secretly makes you smile the most?", SpiceLevel.SWEET),
        DarePrompt("st6", false, "When did you feel most proud to call me yours?", SpiceLevel.SWEET),
        // SWEET DARES
        DarePrompt("sd1", true, "Send a 10-second voice note describing your favorite thing about my smile.", SpiceLevel.SWEET),
        DarePrompt("sd2", true, "Write a 3-line mini poem about us right now and send it to me with a warm hug.", SpiceLevel.SWEET),
        DarePrompt("sd3", true, "Give me a brand-new sweet nickname we have to use for the next 24 hours.", SpiceLevel.SWEET),
        DarePrompt("sd4", true, "Plan our next cozy coffee or dessert date down to the exact treat we'll share.", SpiceLevel.SWEET),
        DarePrompt("sd5", true, "Send a selfie making the exact face you make when you see my name pop up.", SpiceLevel.SWEET),
        DarePrompt("sd6", true, "Tell me three promises you want to keep for us this month.", SpiceLevel.SWEET),

        // FLIRTY TRUTHS
        DarePrompt("ft1", false, "What outfit or look of mine makes it hardest for you to focus?", SpiceLevel.FLIRTY),
        DarePrompt("ft2", false, "Have you ever caught yourself staring at me and completely lost track of what I said?", SpiceLevel.FLIRTY),
        DarePrompt("ft3", false, "What's the boldest compliment you've wanted to whisper in my ear?", SpiceLevel.FLIRTY),
        DarePrompt("ft4", false, "Which kiss of ours still lives rent-free in your head?", SpiceLevel.FLIRTY),
        DarePrompt("ft5", false, "If we stole away for a spontaneous weekend tonight, where are you taking me?", SpiceLevel.FLIRTY),
        DarePrompt("ft6", false, "What little tease of mine works on you 100% of the time?", SpiceLevel.FLIRTY),
        // FLIRTY DARES
        DarePrompt("fd1", true, "Describe how you'd greet me at the door tonight in one irresistible sentence.", SpiceLevel.FLIRTY),
        DarePrompt("fd2", true, "Send a dramatic slow-motion wink or blow a kiss over chat right now.", SpiceLevel.FLIRTY),
        DarePrompt("fd3", true, "Pick a song for our next midnight slow dance and tell me why you chose it.", SpiceLevel.FLIRTY),
        DarePrompt("fd4", true, "Rate our chemistry out of 10—and explain what would make it an 11.", SpiceLevel.FLIRTY),
        DarePrompt("fd5", true, "Drop your smoothest pickup line on me as if we just met across a dimly lit lounge.", SpiceLevel.FLIRTY),
        DarePrompt("fd6", true, "Promise me one playful favor I can redeem on our very next date.", SpiceLevel.FLIRTY),

        // AFTER-DARK TRUTHS
        DarePrompt("bt1", false, "What's a midnight scenario with me that always raises your pulse?", SpiceLevel.BOLD),
        DarePrompt("bt2", false, "Where do you love my hands to be when we're alone and the lights are low?", SpiceLevel.BOLD),
        DarePrompt("bt3", false, "Do you prefer taking the lead or letting me set the pace when sparks fly?", SpiceLevel.BOLD),
        DarePrompt("bt4", false, "What's one secret fantasy date night you've never pitched out loud yet?", SpiceLevel.BOLD),
        DarePrompt("bt5", false, "What's the quickest way I can make you blush without even touching you?", SpiceLevel.BOLD),
        DarePrompt("bt6", false, "Slow, lingering tension or spontaneous breathless energy—which wins tonight?", SpiceLevel.BOLD),
        // AFTER-DARK DARES
        DarePrompt("bd1", true, "Tell me in one sweet message exactly what you'd whisper if I were inches away.", SpiceLevel.BOLD),
        DarePrompt("bd2", true, "Give me a 2-word teaser for our next private night in—no extra context allowed.", SpiceLevel.BOLD),
        DarePrompt("bd3", true, "Create a VIP coupon for me with no expiration date and name what it unlocks.", SpiceLevel.BOLD),
        DarePrompt("bd4", true, "Confess the exact moment today you wished I was right next to you.", SpiceLevel.BOLD),
        DarePrompt("bd5", true, "Set the scene for our dream late-night staycation in 3 vivid details.", SpiceLevel.BOLD),
        DarePrompt("bd6", true, "Send a voice note in your lowest, softest late-night voice saying goodnight to me.", SpiceLevel.BOLD)
    )

    fun daresForSpice(spiceLevel: SpiceLevel, isDare: Boolean): List<DarePrompt> {
        val exact = darePrompts.filter { it.spiceLevel == spiceLevel && it.isDare == isDare }
        return if (exact.isNotEmpty()) exact else darePrompts.filter { it.isDare == isDare }
    }

    val wavelengthSpectrums: List<WavelengthSpectrum> = listOf(
        WavelengthSpectrum("w1", "Rainy Cuddles In", "Midnight Rooftop", "🌧️", "🌃", SpiceLevel.SWEET),
        WavelengthSpectrum("w2", "Sunrise Coffee", "2 AM Deep Talks", "☕", "🌙", SpiceLevel.SWEET),
        WavelengthSpectrum("w3", "Handwritten Letter", "Surprise Road Trip", "✉️", "🚗", SpiceLevel.SWEET),
        WavelengthSpectrum("w4", "Forehead Kisses", "Tight Bear Hugs", "🌸", "🧸", SpiceLevel.SWEET),
        WavelengthSpectrum("w5", "Movie Marathon", "Stargazing Blanket", "🎬", "✨", SpiceLevel.SWEET),
        WavelengthSpectrum("w6", "Bake Together", "Candlelit Takeout", "🧁", "🕯️", SpiceLevel.SWEET),
        WavelengthSpectrum("w7", "Cute & Innocent", "Dangerously Flirty", "🎀", "💋", SpiceLevel.FLIRTY),
        WavelengthSpectrum("w8", "Slow Burn Tension", "Instant Spark", "🕯️", "⚡", SpiceLevel.FLIRTY),
        WavelengthSpectrum("w9", "Playful Teasing", "Hopeless Romantic", "😏", "🌹", SpiceLevel.FLIRTY),
        WavelengthSpectrum("w10", "Whispered Secret", "Bold Confession", "🤫", "🔥", SpiceLevel.FLIRTY),
        WavelengthSpectrum("w11", "Velvet Lounge", "Beach at Midnight", "🍸", "🌊", SpiceLevel.FLIRTY),
        WavelengthSpectrum("w12", "Dress to Impress", "Steal My Hoodie", "✨", "🧥", SpiceLevel.FLIRTY),
        WavelengthSpectrum("w13", "Soft & Lingering", "Wild & Breathless", "🥀", "🔥", SpiceLevel.BOLD),
        WavelengthSpectrum("w14", "Dim Candlelight", "Total Darkness", "🕯️", "🖤", SpiceLevel.BOLD),
        WavelengthSpectrum("w15", "Take the Lead", "Surrender Control", "👑", "💫", SpiceLevel.BOLD),
        WavelengthSpectrum("w16", "Sweet Anticipation", "Zero Patience", "⏳", "❤️‍🔥", SpiceLevel.BOLD)
    )

    fun spectrumFor(index: Int, spiceLevel: SpiceLevel): WavelengthSpectrum {
        val pool = wavelengthSpectrums.filter { it.spiceLevel == spiceLevel }.ifEmpty { wavelengthSpectrums }
        return pool[((index % pool.size) + pool.size) % pool.size]
    }

    val dateNightIdeas: List<DateNightIdea> = listOf(
        DateNightIdea("dn1", "Stargazing & Warm Blankets", "🌌", "Hot cocoa, lo-fi playlist, zero phones", SpiceLevel.SWEET),
        DateNightIdea("dn2", "Homemade Pasta & Slow Dance", "🍝", "Cook together in the kitchen with candles", SpiceLevel.SWEET),
        DateNightIdea("dn3", "Bookstore Pick-For-Each-Other", "📚", "Secretly choose a book & write a note inside", SpiceLevel.SWEET),
        DateNightIdea("dn4", "Golden Hour Picnic", "🧺", "Strawberries, pastries, Polaroids at sunset", SpiceLevel.SWEET),
        DateNightIdea("dn5", "Late-Night Ice Cream Drive", "🍦", "Windows down, favorite songs on repeat", SpiceLevel.SWEET),
        DateNightIdea("dn6", "Cozy Paint & Sip Night", "🎨", "Swap canvases every 10 minutes", SpiceLevel.SWEET),
        DateNightIdea("dn7", "Candlelit Rooftop Mocktails", "🥂", "Dress up like it's our very first date", SpiceLevel.FLIRTY),
        DateNightIdea("dn8", "Midnight Photo Booth Crawl", "📸", "Keep the cutest strip in our phone cases", SpiceLevel.FLIRTY),
        DateNightIdea("dn9", "Secret Mystery Itinerary", "🗝️", "One partner plans every stop, total surprise", SpiceLevel.FLIRTY),
        DateNightIdea("dn10", "Jazz Bar & Slow Sway", "🎷", "Velvet lighting, close whispers, zero rush", SpiceLevel.FLIRTY),
        DateNightIdea("dn11", "Rainy Hotel Staycation", "🏨", "Room service, plush robes, city skyline", SpiceLevel.FLIRTY),
        DateNightIdea("dn12", "Sunset Beach Walk", "🌊", "Barefoot in the sand until the stars appear", SpiceLevel.FLIRTY),
        DateNightIdea("dn13", "After-Dark Spa & Massage", "🕯️", "Warm aromatherapy oils & slow music", SpiceLevel.BOLD),
        DateNightIdea("dn14", "No-Phones Velvet Night In", "🖤", "Silk sheets, strawberries, Truth or Dare", SpiceLevel.BOLD),
        DateNightIdea("dn15", "Midnight City Overlook", "🌃", "Parked under the lights with the windows fogged", SpiceLevel.BOLD),
        DateNightIdea("dn16", "Dress-Code Dinner at Home", "🌹", "Formal attire, dim lamps, lingering glances", SpiceLevel.BOLD),
        DateNightIdea("dn17", "Private Cinema Pillow Fort", "🎞️", "Projector on the ceiling & cozy snacks", SpiceLevel.BOLD),
        DateNightIdea("dn18", "Spontaneous Weekend Escape", "✨", "Pack one bag and disappear for 48 hours", SpiceLevel.BOLD)
    )

    val quickWhisperStarters: List<String> = listOf(
        "Thinking about you right now ♥",
        "You looked unreal the last time I saw you.",
        "Counting down until I can steal a hug.",
        "Save me a slow dance tonight.",
        "Just wanted to remind you you're my favorite.",
        "Can't stop smiling thinking about our next date."
    )

    fun dareStateToJson(state: DareSyncState): String = JSONObject().apply {
        put("ri", state.roundIndex)
        put("id", state.isDare)
        put("pid", state.promptId)
        put("pt", state.promptText)
        put("cn", state.challengerName)
        put("st", state.status)
        put("cc", state.completedCount)
    }.toString()

    fun dareStateFromJson(json: String): DareSyncState = runCatching {
        val o = JSONObject(json.ifBlank { "{}" })
        DareSyncState(
            roundIndex = o.optInt("ri", 0).coerceAtLeast(0),
            isDare = o.optBoolean("id", true),
            promptId = o.optString("pid", ""),
            promptText = o.optString("pt", ""),
            challengerName = o.optString("cn", ""),
            status = o.optString("st", "OPEN"),
            completedCount = o.optInt("cc", 0).coerceAtLeast(0)
        )
    }.getOrDefault(DareSyncState())

    fun wavelengthStateToJson(state: WavelengthSyncState): String = JSONObject().apply {
        put("ri", state.roundIndex)
        put("si", state.spectrumIndex)
        put("tp", state.targetPos.toDouble())
        put("cw", state.clueWord)
        put("ca", state.clueAuthor)
        if (state.guessPos != null) put("gp", state.guessPos.toDouble())
        put("gn", state.guesserName)
        put("ph", state.phase)
        put("ls", state.lastScore)
        put("ts", state.totalSyncPoints)
        put("rp", state.roundsPlayed)
    }.toString()

    fun wavelengthStateFromJson(json: String): WavelengthSyncState = runCatching {
        val o = JSONObject(json.ifBlank { "{}" })
        WavelengthSyncState(
            roundIndex = o.optInt("ri", 0).coerceAtLeast(0),
            spectrumIndex = o.optInt("si", 0).coerceAtLeast(0),
            targetPos = o.optDouble("tp", 0.5).toFloat().coerceIn(0.12f, 0.88f),
            clueWord = o.optString("cw", ""),
            clueAuthor = o.optString("ca", ""),
            guessPos = if (o.has("gp")) o.optDouble("gp", 0.5).toFloat().coerceIn(0f, 1f) else null,
            guesserName = o.optString("gn", ""),
            phase = o.optString("ph", "CLUE"),
            lastScore = o.optInt("ls", 0),
            totalSyncPoints = o.optInt("ts", 0).coerceAtLeast(0),
            roundsPlayed = o.optInt("rp", 0).coerceAtLeast(0)
        )
    }.getOrDefault(WavelengthSyncState())

    fun dateWhisperStateToJson(state: DateWhisperSyncState): String = JSONObject().apply {
        put("ml", JSONArray(state.myLikedIds.toList()))
        put("ms", JSONArray(state.mySkippedIds.toList()))
        put("pl", JSONArray(state.partnerLikedIds.toList()))
        val wArr = JSONArray()
        state.whispers.takeLast(24).forEach { w ->
            wArr.put(
                JSONObject().apply {
                    put("id", w.id)
                    put("sn", w.senderName)
                    put("tx", w.text)
                    put("ts", w.timestamp)
                    put("rv", w.isRevealed)
                }
            )
        }
        put("wh", wArr)
    }.toString()

    fun dateWhisperStateFromJson(json: String): DateWhisperSyncState = runCatching {
        val o = JSONObject(json.ifBlank { "{}" })
        fun parseSet(key: String): Set<String> {
            val arr = o.optJSONArray(key) ?: return emptySet()
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "")
                if (s.isNotBlank()) set.add(s)
            }
            return set
        }
        val whispers = mutableListOf<SecretWhisperNote>()
        val wArr = o.optJSONArray("wh")
        if (wArr != null) {
            for (i in 0 until wArr.length()) {
                val wo = wArr.optJSONObject(i) ?: continue
                whispers.add(
                    SecretWhisperNote(
                        id = wo.optString("id", "w_$i"),
                        senderName = wo.optString("sn", "Partner"),
                        text = wo.optString("tx", ""),
                        timestamp = wo.optLong("ts", 0L),
                        isRevealed = wo.optBoolean("rv", false)
                    )
                )
            }
        }
        DateWhisperSyncState(
            myLikedIds = parseSet("ml"),
            mySkippedIds = parseSet("ms"),
            partnerLikedIds = parseSet("pl"),
            whispers = whispers
        )
    }.getOrDefault(DateWhisperSyncState())
}
