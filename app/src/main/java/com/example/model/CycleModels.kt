package com.example.model

import androidx.compose.ui.graphics.Color

enum class CyclePhase(
    val title: String,
    val emoji: String,
    val shortName: String,
    val description: String,
    val accentColor: Color,
    val containerColor: Color,
    val onContainerColor: Color
) {
    MENSTRUAL(
        title = "Menstrual phase",
        emoji = "🌸",
        shortName = "Menstrual",
        description = "Rest, release, warm tea & cozy blankets. Be gentle with yourself today.",
        accentColor = Color(0xFFD4629A),
        containerColor = Color(0xFFFBEAF4),
        onContainerColor = Color(0xFF6E204A)
    ),
    FOLLICULAR(
        title = "Follicular phase",
        emoji = "🌱",
        shortName = "Follicular",
        description = "Energy rising, fresh ideas, new beginnings. A great time to start something.",
        accentColor = Color(0xFF5E9C68),
        containerColor = Color(0xFFE4F2E6),
        onContainerColor = Color(0xFF26542D)
    ),
    FERTILE(
        title = "Fertile window",
        emoji = "✨",
        shortName = "Fertile",
        description = "High vitality, confidence, creativity peaking. You're magnetic.",
        accentColor = Color(0xFFE6723D),
        containerColor = Color(0xFFFEEEE5),
        onContainerColor = Color(0xFF6B2A0D)
    ),
    OVULATION(
        title = "Ovulation phase",
        emoji = "🌿",
        shortName = "Ovulation",
        description = "Peak connection, express yourself, share your warmth with the world.",
        accentColor = Color(0xFF8B62D6),
        containerColor = Color(0xFFF0E6FF),
        onContainerColor = Color(0xFF3E1D78)
    ),
    LUTEAL(
        title = "Luteal phase",
        emoji = "🌙",
        shortName = "Luteal",
        description = "Focus, wrap up projects, organize your space. Slowing down naturally.",
        accentColor = Color(0xFF7C3FA6),
        containerColor = Color(0xFFF0E6FF),
        onContainerColor = Color(0xFF3F195A)
    ),
    LATE_LUTEAL(
        title = "Late luteal · PMS",
        emoji = "☕",
        shortName = "PMS window",
        description = "Nourish, say no to extra demands, embrace quiet evenings & comfort.",
        accentColor = Color(0xFFD49120),
        containerColor = Color(0xFFFDF3DC),
        onContainerColor = Color(0xFF5C3B05)
    )
}

data class CycleStatus(
    val currentPhase: CyclePhase,
    val dayInCycle: Int,
    val totalCycleDays: Int,
    val periodLength: Int,
    val daysUntilNextPeriod: Int,
    val isLate: Boolean,
    val daysLate: Int,
    val expectedNextDateMillis: Long,
    val earliestDateMillis: Long,
    val latestDateMillis: Long,
    val averageCycleLength: Double,
    val sigmaVarianceDays: Double,
    val cycleCount: Int,
    val lastPeriodStartDateMillis: Long?,
    val nextPhase: CyclePhase,
    val daysUntilNextPhase: Int,
    val isPeriodCurrentlyActive: Boolean = false,
    val activePeriodDays: Int = 0,
    val activePeriodId: Long? = null,
    val predictedWindowStartMillis: Long = earliestDateMillis,
    val predictedWindowEndMillis: Long = latestDateMillis + (periodLength.toLong() * 24L * 60 * 60 * 1000)
)

data class CycleHistoryItem(
    val periodId: Long,
    val startDateMillis: Long,
    val endDateMillis: Long? = null,
    val cycleDaysToNext: Int? = null,
    val durationDays: Int? = null,
    val isMostRecent: Boolean = false,
    val isOngoing: Boolean = false,
    val notes: String = "",
    val anomalyType: AnomalyType = AnomalyType.NORMAL,
    val anomalyMessage: String? = null
)

enum class CalendarCyclePhase(
    val title: String,
    val shortName: String,
    val dotColor: Color,
    val containerColor: Color,
    val onContainerColor: Color,
    val emoji: String,
    val description: String,
    val wellnessTip: String
) {
    MENSTRUATION(
        title = "Menstruation",
        shortName = "Menstruation",
        dotColor = Color(0xFFE11D48), // Vibrant crimson / rose
        containerColor = Color(0xFFFEE2E2),
        onContainerColor = Color(0xFF9F1239),
        emoji = "🩸",
        description = "Uterine lining sheds as progesterone and estrogen dip. A natural time for renewal.",
        wellnessTip = "Rest, hydrate with warm herbal teas, gentle stretching, and cozy comfort."
    ),
    FOLLICULAR(
        title = "Follicular Phase",
        shortName = "Follicular",
        dotColor = Color(0xFF10B981), // Spring emerald green
        containerColor = Color(0xFFD1FAE5),
        onContainerColor = Color(0xFF065F46),
        emoji = "🌱",
        description = "Estrogen levels rise and follicles develop. Physical energy and curiosity surge.",
        wellnessTip = "Great time to brainstorm, start new habits, and engage in upbeat cardio."
    ),
    OVULATORY(
        title = "Ovulatory Phase",
        shortName = "Ovulatory",
        dotColor = Color(0xFF8B5CF6), // Radiant violet / purple
        containerColor = Color(0xFFEDE9FE),
        onContainerColor = Color(0xFF5B21B6),
        emoji = "✨",
        description = "LH surge triggers ovulation. Peak fertility, social magnetism, and confidence.",
        wellnessTip = "Ideal for social gatherings, presentations, strength training, and collaborative work."
    ),
    LUTEAL(
        title = "Luteal Phase",
        shortName = "Luteal",
        dotColor = Color(0xFFF59E0B), // Warm golden amber
        containerColor = Color(0xFFFEF3C7),
        onContainerColor = Color(0xFF92400E),
        emoji = "🌙",
        description = "Progesterone peaks then gently declines into the PMS / reflection window.",
        wellnessTip = "Focus on details, complete pending tasks, eat nutrient-dense meals, and prioritize sleep."
    );

    companion object {
        fun fromCyclePhase(phase: CyclePhase): CalendarCyclePhase {
            return when (phase) {
                CyclePhase.MENSTRUAL -> MENSTRUATION
                CyclePhase.FOLLICULAR -> FOLLICULAR
                CyclePhase.FERTILE, CyclePhase.OVULATION -> OVULATORY
                CyclePhase.LUTEAL, CyclePhase.LATE_LUTEAL -> LUTEAL
            }
        }
    }
}

data class CalendarDayData(
    val dateMillis: Long,
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val phase: CalendarCyclePhase,
    val dayInCycle: Int,
    val totalCycleDays: Int,
    val isPeriodStart: Boolean,
    val isPeriodDay: Boolean,
    val isHistoricalLogged: Boolean = false,
    val isPredicted: Boolean = false,
    val isInPredictedWindow: Boolean = false,
    val isPredictedPeriodStart: Boolean = false
)

