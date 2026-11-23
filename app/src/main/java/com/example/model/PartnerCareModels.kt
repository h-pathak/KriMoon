package com.example.model

import com.example.data.PeriodLog
import com.example.security.BackendSecurityFirewall

enum class UserGender(val storageValue: String, val label: String) {
    FEMALE("FEMALE", "Female"),
    MALE("MALE", "Male");

    companion object {
        fun fromStorage(raw: String?): UserGender {
            return if (raw.equals("MALE", ignoreCase = true)) MALE else FEMALE
        }
    }
}

/**
 * Zero-knowledge E2EE cycle status snapshot synced live from the female partner's device
 * to her paired male partner's device inside an encrypted HANDSHAKE block.
 *
 * Uses a pure-Kotlin wire codec so it works identically on Android runtime and pure JVM unit tests
 * without depending on Android stubbed org.json classes.
 */
data class PartnerCycleSyncSnapshot(
    val partnerName: String = "",
    val currentPhaseName: String = CyclePhase.FOLLICULAR.name,
    val nextPhaseName: String = CyclePhase.FERTILE.name,
    val dayInCycle: Int = 1,
    val totalCycleDays: Int = 28,
    val periodLength: Int = 5,
    val daysUntilNextPeriod: Int = 28,
    val daysUntilNextPhase: Int = 5,
    val isPeriodCurrentlyActive: Boolean = false,
    val isLate: Boolean = false,
    val daysLate: Int = 0,
    val lastPeriodStartMillis: Long = 0L,
    val syncedAtMillis: Long = 0L
) {
    val currentPhase: CyclePhase
        get() = runCatching { CyclePhase.valueOf(currentPhaseName) }.getOrDefault(CyclePhase.FOLLICULAR)

    val nextPhase: CyclePhase
        get() = runCatching { CyclePhase.valueOf(nextPhaseName) }.getOrDefault(CyclePhase.FERTILE)

    /**
     * Compact deterministic fingerprint used to avoid redundant cloud sync writes while
     * immediately broadcasting whenever her phase, cycle day, active bleeding status,
     * cycle length, or period start changes.
     */
    fun fingerprint(): String {
        return "$currentPhaseName|$nextPhaseName|$dayInCycle|$totalCycleDays|$periodLength|" +
            "$daysUntilNextPeriod|$daysUntilNextPhase|$isPeriodCurrentlyActive|$isLate|$daysLate|$lastPeriodStartMillis"
    }

    /**
     * Projects the synced snapshot forward to [nowMillis] if calendar days have passed since
     * [syncedAtMillis] and [lastPeriodStartMillis] is known, keeping the male partner's view
     * accurate even across midnight before the female partner re-opens the app.
     */
    fun projectToTime(nowMillis: Long = System.currentTimeMillis()): PartnerCycleSyncSnapshot {
        if (lastPeriodStartMillis <= 0L || syncedAtMillis <= 0L) return this
        val msPerDay = 24L * 60L * 60L * 1000L
        val elapsedDays = ((nowMillis - syncedAtMillis) / msPerDay).toInt()
        if (elapsedDays <= 0) return this

        val safePeriodLen = periodLength.coerceIn(2, 14)
        val projectedDayFromStart = (((nowMillis - lastPeriodStartMillis) / msPerDay).toInt() + 1).coerceAtLeast(1)
        val keepPeriodOpen = isPeriodCurrentlyActive && projectedDayFromStart <= safePeriodLen
        val syntheticEndMillis = if (keepPeriodOpen) {
            null
        } else {
            lastPeriodStartMillis + ((safePeriodLen - 1).toLong() * msPerDay)
        }
        val syntheticLog = PeriodLog(
            id = 1L,
            startDateMillis = lastPeriodStartMillis,
            endDateMillis = syntheticEndMillis,
            durationDays = if (keepPeriodOpen) null else safePeriodLen
        )
        val computed = CycleCalculator.compute(
            periods = listOf(syntheticLog),
            defaultCycleLength = totalCycleDays.coerceIn(18, 60),
            defaultPeriodLength = safePeriodLen,
            referenceTimeMillis = nowMillis
        )
        return copy(
            currentPhaseName = computed.currentPhase.name,
            nextPhaseName = computed.nextPhase.name,
            dayInCycle = computed.dayInCycle,
            totalCycleDays = computed.totalCycleDays,
            periodLength = computed.periodLength,
            daysUntilNextPeriod = computed.daysUntilNextPeriod,
            daysUntilNextPhase = computed.daysUntilNextPhase,
            isPeriodCurrentlyActive = computed.isPeriodCurrentlyActive,
            isLate = computed.isLate,
            daysLate = computed.daysLate
        )
    }

    /**
     * Pure-Kotlin JSON serialization (no org.json dependency).
     */
    fun toJsonString(): String {
        val safeName = escapeJson(BackendSecurityFirewall.sanitizeField(partnerName, maxLength = 32))
        val safeCur = escapeJson(currentPhase.name)
        val safeNext = escapeJson(nextPhase.name)
        return buildString {
            append("{")
            append("\"pn\":\"").append(safeName).append("\",")
            append("\"cp\":\"").append(safeCur).append("\",")
            append("\"np\":\"").append(safeNext).append("\",")
            append("\"dc\":").append(dayInCycle.coerceIn(1, 120)).append(",")
            append("\"tc\":").append(totalCycleDays.coerceIn(18, 60)).append(",")
            append("\"pl\":").append(periodLength.coerceIn(2, 14)).append(",")
            append("\"dp\":").append(daysUntilNextPeriod.coerceIn(-60, 120)).append(",")
            append("\"dn\":").append(daysUntilNextPhase.coerceIn(0, 60)).append(",")
            append("\"pa\":").append(isPeriodCurrentlyActive).append(",")
            append("\"lt\":").append(isLate).append(",")
            append("\"dl\":").append(daysLate.coerceIn(0, 90)).append(",")
            append("\"lp\":").append(lastPeriodStartMillis.coerceAtLeast(0L)).append(",")
            append("\"ts\":").append(syncedAtMillis.coerceAtLeast(0L))
            append("}")
        }
    }

    companion object {
        fun fromCycleStatus(
            partnerName: String,
            status: CycleStatus,
            nowMillis: Long = System.currentTimeMillis()
        ): PartnerCycleSyncSnapshot {
            return PartnerCycleSyncSnapshot(
                partnerName = BackendSecurityFirewall.sanitizeField(partnerName, maxLength = 32).ifBlank { "Partner" },
                currentPhaseName = status.currentPhase.name,
                nextPhaseName = status.nextPhase.name,
                dayInCycle = status.dayInCycle.coerceIn(1, 120),
                totalCycleDays = status.totalCycleDays.coerceIn(18, 60),
                periodLength = status.periodLength.coerceIn(2, 14),
                daysUntilNextPeriod = status.daysUntilNextPeriod.coerceIn(-60, 120),
                daysUntilNextPhase = status.daysUntilNextPhase.coerceIn(0, 60),
                isPeriodCurrentlyActive = status.isPeriodCurrentlyActive,
                isLate = status.isLate,
                daysLate = status.daysLate.coerceIn(0, 90),
                lastPeriodStartMillis = status.lastPeriodStartDateMillis ?: 0L,
                syncedAtMillis = nowMillis
            )
        }

        fun fromJsonString(raw: String?): PartnerCycleSyncSnapshot? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            if (trimmed == "{}" || !trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
            return runCatching {
                val strFields = extractJsonStrings(trimmed)
                val numFields = extractJsonNumbers(trimmed)
                val boolFields = extractJsonBooleans(trimmed)

                val curRaw = strFields["cp"] ?: return null
                val curPhase = runCatching { CyclePhase.valueOf(curRaw) }.getOrNull() ?: return null
                val nextRaw = strFields["np"] ?: defaultNextPhase(curPhase).name
                val nextPhase = runCatching { CyclePhase.valueOf(nextRaw) }.getOrDefault(defaultNextPhase(curPhase))
                val ts = numFields["ts"] ?: 0L
                if (ts <= 0L && !numFields.containsKey("dc")) return null

                PartnerCycleSyncSnapshot(
                    partnerName = BackendSecurityFirewall.sanitizeField(strFields["pn"].orEmpty(), maxLength = 32),
                    currentPhaseName = curPhase.name,
                    nextPhaseName = nextPhase.name,
                    dayInCycle = (numFields["dc"]?.toInt() ?: 1).coerceIn(1, 120),
                    totalCycleDays = (numFields["tc"]?.toInt() ?: 28).coerceIn(18, 60),
                    periodLength = (numFields["pl"]?.toInt() ?: 5).coerceIn(2, 14),
                    daysUntilNextPeriod = (numFields["dp"]?.toInt() ?: 14).coerceIn(-60, 120),
                    daysUntilNextPhase = (numFields["dn"]?.toInt() ?: 1).coerceIn(0, 60),
                    isPeriodCurrentlyActive = boolFields["pa"] ?: false,
                    isLate = boolFields["lt"] ?: false,
                    daysLate = (numFields["dl"]?.toInt() ?: 0).coerceIn(0, 90),
                    lastPeriodStartMillis = (numFields["lp"] ?: 0L).coerceAtLeast(0L),
                    syncedAtMillis = ts.coerceAtLeast(0L)
                )
            }.getOrNull()
        }

        fun defaultNextPhase(phase: CyclePhase): CyclePhase = when (phase) {
            CyclePhase.MENSTRUAL -> CyclePhase.FOLLICULAR
            CyclePhase.FOLLICULAR -> CyclePhase.FERTILE
            CyclePhase.FERTILE -> CyclePhase.OVULATION
            CyclePhase.OVULATION -> CyclePhase.LUTEAL
            CyclePhase.LUTEAL -> CyclePhase.LATE_LUTEAL
            CyclePhase.LATE_LUTEAL -> CyclePhase.MENSTRUAL
        }

        private fun escapeJson(value: String): String {
            return buildString(value.length + 4) {
                for (ch in value) {
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> if (ch.code >= 0x20) append(ch)
                    }
                }
            }
        }

        private fun unescapeJson(value: String): String {
            val sb = StringBuilder(value.length)
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    when (val n = value[i + 1]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(n)
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }

        private val STR_PAIR_REGEX = Regex("\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        private val NUM_PAIR_REGEX = Regex("\"([a-zA-Z0-9_]+)\"\\s*:\\s*(-?\\d+)")
        private val BOOL_PAIR_REGEX = Regex("\"([a-zA-Z0-9_]+)\"\\s*:\\s*(true|false)")

        private fun extractJsonStrings(json: String): Map<String, String> {
            val map = HashMap<String, String>()
            STR_PAIR_REGEX.findAll(json).forEach { m ->
                map[m.groupValues[1]] = unescapeJson(m.groupValues[2])
            }
            return map
        }

        private fun extractJsonNumbers(json: String): Map<String, Long> {
            val map = HashMap<String, Long>()
            NUM_PAIR_REGEX.findAll(json).forEach { m ->
                m.groupValues[2].toLongOrNull()?.let { map[m.groupValues[1]] = it }
            }
            return map
        }

        private fun extractJsonBooleans(json: String): Map<String, Boolean> {
            val map = HashMap<String, Boolean>()
            BOOL_PAIR_REGEX.findAll(json).forEach { m ->
                map[m.groupValues[1]] = m.groupValues[2] == "true"
            }
            return map
        }
    }
}

data class PartnerCareAction(
    val category: String,
    val title: String,
    val detail: String
)

data class PartnerPhaseCareGuide(
    val phase: CyclePhase,
    val displayTitle: String,
    val statusHeadline: String,
    val energyLabel: String,
    val moodSummary: String,
    val careRightNow: List<PartnerCareAction>,
    val avoidList: List<String>,
    val prepForNextHeadline: String,
    val prepForNextActions: List<String>
)

object PartnerCareGuideProvider {

    fun guideFor(phase: CyclePhase): PartnerPhaseCareGuide = when (phase) {
        CyclePhase.MENSTRUAL -> PartnerPhaseCareGuide(
            phase = CyclePhase.MENSTRUAL,
            displayTitle = "Menstrual Phase",
            statusHeadline = "Comfort, warmth & zero friction",
            energyLabel = "Low · Recovery mode",
            moodSummary = "Cramping, fatigue, and lower energy are normal right now. Taking small logistical burdens off her plate makes the biggest difference.",
            careRightNow = listOf(
                PartnerCareAction("Comfort", "Warmth on standby", "Bring a heating pad, warm water bottle, or hot ginger/chamomile tea before she has to ask."),
                PartnerCareAction("Food", "Iron & comfort meals", "Handle dinner or order her go-to warm meal. Iron-rich food and dark chocolate help fight fatigue."),
                PartnerCareAction("Support", "Take over chores", "Quietly handle errands, dishes, or heavy tasks so she can actually rest guilt-free."),
                PartnerCareAction("Connection", "Low-pressure company", "Offer a back rub, cozy movie night, or quiet space depending on her vibe.")
            ),
            avoidList = listOf(
                "Avoid packing the schedule with high-energy outings or late nights",
                "Don't dismiss cramps or ask 'are you sure it's that bad?'"
            ),
            prepForNextHeadline = "Follicular Phase coming up next",
            prepForNextActions = listOf(
                "Expect her energy and mood to bounce back noticeably over the next few days",
                "Start brainstorming a fun outing, fresh restaurant spot, or active date once she feels ready",
                "Keep a lighthearted, upbeat vibe as she shifts out of recovery mode"
            )
        )

        CyclePhase.FOLLICULAR -> PartnerPhaseCareGuide(
            phase = CyclePhase.FOLLICULAR,
            displayTitle = "Follicular Phase",
            statusHeadline = "Rising energy & fresh plans",
            energyLabel = "Rising · Upbeat & curious",
            moodSummary = "Estrogen is climbing, bringing back optimism, mental clarity, and motivation. She's usually up for trying new things and socializing.",
            careRightNow = listOf(
                PartnerCareAction("Plans", "Suggest something new", "Great time for a coffee walk, trying a new spot, or planning a weekend mini-adventure."),
                PartnerCareAction("Conversation", "Match her curiosity", "Bounce ideas around, talk future plans, or start a shared project or game together."),
                PartnerCareAction("Activity", "Active dates work well", "Walks, workouts together, bowling, or exploring a neighborhood fit her rising energy."),
                PartnerCareAction("Appreciation", "Notice the momentum", "Hype up her goals and projects — she'll feel energized by genuine encouragement.")
            ),
            avoidList = listOf(
                "Avoid being stuck in a boring routine if she's eager to get out",
                "Don't brush off her enthusiasm when she pitches new ideas"
            ),
            prepForNextHeadline = "Fertile Window up next",
            prepForNextActions = listOf(
                "Confidence and social energy are about to peak — plan a great date night",
                "Bring your A-game with compliments and attentive presence",
                "If you're planning romantic time together, lock in a stress-free evening ahead"
            )
        )

        CyclePhase.FERTILE -> PartnerPhaseCareGuide(
            phase = CyclePhase.FERTILE,
            displayTitle = "Fertile Window",
            statusHeadline = "High energy & peak connection",
            energyLabel = "High · Social & confident",
            moodSummary = "Vitality, warmth, and emotional closeness are ramping up. She's typically expressive, affectionate, and enjoys feeling prioritized.",
            careRightNow = listOf(
                PartnerCareAction("Romance", "Plan a proper date", "Book a dinner, dress sharp, and make intentional time for just the two of you."),
                PartnerCareAction("Presence", "Be fully engaged", "Put your phone down during conversations — deep eye contact and active listening hit hardest now."),
                PartnerCareAction("Affection", "Lean into compliments", "Be vocal about what you admire in her; she'll love playful flirting and warmth."),
                PartnerCareAction("Play", "Keep it fun", "Great window for Couple Lounge games, playful banter, or spontaneous evening plans.")
            ),
            avoidList = listOf(
                "Avoid being distracted or emotionally distant during quality time",
                "Don't leave date plans last-minute if you can easily organize something thoughtful"
            ),
            prepForNextHeadline = "Ovulation Peak is right around the corner",
            prepForNextActions = listOf(
                "Energy peaks right at ovulation — ideal window for memorable shared experiences",
                "Keep hydration and balanced meals easy if she gets mild mid-cycle aches",
                "Prepare to transition smoothly into a calmer pace once the luteal phase begins right after"
            )
        )

        CyclePhase.OVULATION -> PartnerPhaseCareGuide(
            phase = CyclePhase.OVULATION,
            displayTitle = "Ovulation Phase",
            statusHeadline = "Peak vitality & closeness",
            energyLabel = "Peak · Radiant & expressive",
            moodSummary = "This is the high point of the cycle for communication, confidence, and physical energy. Make the most of the upbeat connection.",
            careRightNow = listOf(
                PartnerCareAction("Connection", "Quality time first", "Carve out uninterrupted time together — dinner, a sunset drive, or a deep late-night talk."),
                PartnerCareAction("Check-in", "Watch for mild twinges", "A few women feel brief ovulation cramps or bloating; a quick check-in shows you're paying attention."),
                PartnerCareAction("Energy", "Match her pace", "Say yes to social plans, active dates, or playful challenges together."),
                PartnerCareAction("Touch", "Warm physical affection", "Hugs, holding hands, and genuine compliments go a long way today.")
            ),
            avoidList = listOf(
                "Avoid taking high energy for granted or zoning out on screens",
                "Don't forget that her energy will gradually wind down in a couple days"
            ),
            prepForNextHeadline = "Luteal Phase starts next",
            prepForNextActions = listOf(
                "Progesterone will start rising soon, shifting her from high-social to cozy/focused",
                "Start keeping evenings calmer and avoid overbooking next week's calendar",
                "Stock up on her favorite snacks and soothing teas before the shift kicks in"
            )
        )

        CyclePhase.LUTEAL -> PartnerPhaseCareGuide(
            phase = CyclePhase.LUTEAL,
            displayTitle = "Luteal Phase",
            statusHeadline = "Steady calm & thoughtful grounding",
            energyLabel = "Moderate · Winding down",
            moodSummary = "Progesterone rises and her nervous system starts favoring calm, predictability, and deeper rest over constant stimulation.",
            careRightNow = listOf(
                PartnerCareAction("Environment", "Keep things calm", "Favor cozy home nights, relaxed dinners, and predictable plans over chaotic crowds."),
                PartnerCareAction("Nutrition", "Steady meals & snacks", "Blood sugar dips hit harder now — keep hearty snacks or warm meals handy before hunger spikes."),
                PartnerCareAction("Listening", "Listen before fixing", "If she vents, validate how she feels first instead of jumping straight into problem-solving."),
                PartnerCareAction("Sleep", "Protect her rest", "Help wind down earlier in the evening so she gets deeper, uninterrupted sleep.")
            ),
            avoidList = listOf(
                "Avoid springing stressful surprises or sudden schedule changes",
                "Don't take quieter moods personally — she's naturally conserving energy"
            ),
            prepForNextHeadline = "Late Luteal (PMS Window) is next",
            prepForNextActions = listOf(
                "Patience, reassurance, and comfort items will matter most in the next few days",
                "Stock up now on dark chocolate, herbal tea, magnesium-rich snacks, and pain relief",
                "Keep major heavy debates or unnecessary friction off the table next week"
            )
        )

        CyclePhase.LATE_LUTEAL -> PartnerPhaseCareGuide(
            phase = CyclePhase.LATE_LUTEAL,
            displayTitle = "Late Luteal · PMS Window",
            statusHeadline = "Extra patience, reassurance & comfort",
            energyLabel = "Low · Sensitive & tender",
            moodSummary = "Hormones drop right before her period, which can trigger fatigue, bloating, cravings, and heightened sensitivity. Calm reassurance is your superpower here.",
            careRightNow = listOf(
                PartnerCareAction("Patience", "Stay calm & steady", "Keep your tone warm and grounded. Reassure her without getting defensive if emotions run high."),
                PartnerCareAction("Cravings", "Surprise comfort drop", "Grab her favorite treat, warm tea, or comfort food without making it a big deal."),
                PartnerCareAction("Relief", "Reduce mental load", "Make simple decisions (like what's for dinner) so she doesn't have to juggle extra choices."),
                PartnerCareAction("Reassurance", "Send a Thinking-of-You nudge", "A quick warm message or Couple Lounge tap lets her feel supported without pressure.")
            ),
            avoidList = listOf(
                "Never blame her feelings on 'just PMS' — validate first, always",
                "Avoid picking avoidable arguments or pushing intense late-night schedules"
            ),
            prepForNextHeadline = "Menstrual Phase begins soon",
            prepForNextActions = listOf(
                "Have a heating pad, comfortable blankets, and period essentials ready at home",
                "Plan a restful first couple days of her period with easy meals and zero pressure",
                "Check in gently so she knows you've got her back when day 1 arrives"
            )
        )
    }
}
