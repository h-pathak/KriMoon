package com.example.model

import com.example.data.PeriodLog
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Intelligent cycle analytics & prediction engine.
 *
 * Analyzes the user's logged period history with:
 *  1. Recency-weighted moving average intervals (WMA)
 *  2. Linear trend drift detection (identifying lengthening/shortening cycles)
 *  3. Dynamic standard deviation (σ) variance for confidence intervals
 *  4. Logged bleeding duration calibration
 *  5. Upcoming period prediction window calculation (earliest, expected, latest)
 *  6. Calendar view mapping to highlight upcoming predicted windows distinctly
 */
object CycleCalculator {

    private const val MS_PER_DAY = 24L * 60 * 60 * 1000

    private fun safeCoerceIn(value: Int, minVal: Int, maxVal: Int): Int {
        val safeMin = minVal.coerceAtLeast(1)
        val safeMax = maxVal.coerceAtLeast(safeMin)
        return value.coerceIn(safeMin, safeMax)
    }

    private fun startOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun compute(
        periods: List<PeriodLog>,
        defaultCycleLength: Int = 28,
        defaultPeriodLength: Int = 5,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): CycleStatus {
        val today = startOfDayMillis(referenceTimeMillis)
        val sortedPeriods = periods.sortedByDescending { it.startDateMillis }

        if (sortedPeriods.isEmpty()) {
            val expectedNext = today + (defaultCycleLength.toLong() * MS_PER_DAY)
            val earliest = expectedNext - (2L * MS_PER_DAY)
            val latest = expectedNext + (2L * MS_PER_DAY)
            val windowEnd = latest + ((defaultPeriodLength - 1).toLong() * MS_PER_DAY)
            return CycleStatus(
                currentPhase = CyclePhase.MENSTRUAL,
                dayInCycle = 1,
                totalCycleDays = defaultCycleLength,
                periodLength = defaultPeriodLength,
                daysUntilNextPeriod = defaultCycleLength,
                isLate = false,
                daysLate = 0,
                expectedNextDateMillis = expectedNext,
                earliestDateMillis = earliest,
                latestDateMillis = latest,
                averageCycleLength = defaultCycleLength.toDouble(),
                sigmaVarianceDays = 1.8,
                cycleCount = 0,
                lastPeriodStartDateMillis = null,
                nextPhase = CyclePhase.FOLLICULAR,
                daysUntilNextPhase = defaultPeriodLength,
                predictedWindowStartMillis = earliest,
                predictedWindowEndMillis = windowEnd
            )
        }

        // Calculate intervals between logged periods (period i and period i+1)
        val intervals = mutableListOf<Int>()
        for (i in 0 until sortedPeriods.size - 1) {
            val start1 = startOfDayMillis(sortedPeriods[i].startDateMillis)
            val start2 = startOfDayMillis(sortedPeriods[i + 1].startDateMillis)
            val daysDiff = ((start1 - start2) / MS_PER_DAY).toInt()
            if (daysDiff in 15..75) {
                intervals.add(daysDiff)
            }
        }

        val simpleAvg: Double = if (intervals.isNotEmpty()) {
            intervals.average()
        } else {
            defaultCycleLength.toDouble()
        }

        // Weighted Moving Average + Trend Drift for improved prediction accuracy
        val effectiveCycleLength: Int = when {
            intervals.isEmpty() -> defaultCycleLength.coerceIn(20, 50)
            intervals.size == 1 -> intervals.first().coerceIn(18, 60)
            intervals.size == 2 -> {
                val wma = (intervals[0] * 0.65) + (intervals[1] * 0.35)
                wma.roundToInt().coerceIn(18, 60)
            }
            else -> {
                val recent = intervals.take(4)
                val weights = listOf(0.45, 0.25, 0.18, 0.12)
                var wma = 0.0
                for (idx in recent.indices) {
                    wma += recent[idx] * weights[idx]
                }
                val trend = (recent.first() - recent.last()).toDouble() / (recent.size - 1)
                val adjusted = wma + (trend * 0.3)
                adjusted.roundToInt().coerceIn(18, 60)
            }
        }

        val sigma: Double = if (intervals.size >= 2) {
            val variance = intervals.map { (it - simpleAvg) * (it - simpleAvg) }.average()
            sqrt(variance).coerceIn(1.2, 6.0)
        } else {
            1.8
        }

        val mostRecentPeriod = sortedPeriods.first()
        val periodStart = startOfDayMillis(mostRecentPeriod.startDateMillis)
        val daysSinceStart = ((today - periodStart) / MS_PER_DAY).toInt()
        val rawDayInCycle = daysSinceStart + 1

        // Calculate actual logged bleeding duration from completed periods
        val loggedDurations = periods.mapNotNull { p ->
            p.durationDays ?: if (p.endDateMillis != null && p.endDateMillis >= p.startDateMillis) {
                val days = round((startOfDayMillis(p.endDateMillis) - startOfDayMillis(p.startDateMillis)).toDouble() / MS_PER_DAY).toInt() + 1
                if (days in 1..15) days else null
            } else null
        }

        val periodLength = if (loggedDurations.isNotEmpty()) {
            loggedDurations.average().roundToInt().coerceIn(2, effectiveCycleLength / 2)
        } else {
            defaultPeriodLength.coerceIn(2, effectiveCycleLength / 2)
        }

        val isPeriodCurrentlyActive = mostRecentPeriod.endDateMillis == null && daysSinceStart in 0..14
        val activePeriodDays = if (isPeriodCurrentlyActive) rawDayInCycle else 0
        val activePeriodId = if (isPeriodCurrentlyActive) mostRecentPeriod.id else null

        val hasPeriodEnded = mostRecentPeriod.endDateMillis != null && today > startOfDayMillis(mostRecentPeriod.endDateMillis)

        val isLate = rawDayInCycle > effectiveCycleLength
        val daysLate = if (isLate) rawDayInCycle - effectiveCycleLength else 0

        val dayInCycle = rawDayInCycle.coerceAtLeast(1)

        val expectedNextMillis = periodStart + (effectiveCycleLength.toLong() * MS_PER_DAY)
        val windowSpreadDays = (sigma * 1.5).roundToInt().coerceIn(2, 5)
        val earliestMillis = expectedNextMillis - (windowSpreadDays.toLong() * MS_PER_DAY)
        val latestMillis = expectedNextMillis + (windowSpreadDays.toLong() * MS_PER_DAY)
        val predictedWindowEndMillis = latestMillis + ((periodLength - 1).toLong() * MS_PER_DAY)

        val daysUntilNextPeriod = ((expectedNextMillis - today) / MS_PER_DAY).toInt()

        // Ovulation typically occurs ~14 days before next period
        val ovulationDay = safeCoerceIn(effectiveCycleLength - 14, minVal = periodLength + 2, maxVal = effectiveCycleLength - 4)

        val currentPhase: CyclePhase
        val nextPhase: CyclePhase
        val daysUntilNextPhase: Int

        when {
            isPeriodCurrentlyActive || (!hasPeriodEnded && dayInCycle <= periodLength) -> {
                currentPhase = CyclePhase.MENSTRUAL
                nextPhase = CyclePhase.FOLLICULAR
                daysUntilNextPhase = (periodLength + 1 - dayInCycle).coerceAtLeast(1)
            }
            dayInCycle <= (ovulationDay - 3) -> {
                currentPhase = CyclePhase.FOLLICULAR
                nextPhase = CyclePhase.FERTILE
                daysUntilNextPhase = ((ovulationDay - 2) - dayInCycle).coerceAtLeast(1)
            }
            dayInCycle <= (ovulationDay - 1) -> {
                currentPhase = CyclePhase.FERTILE
                nextPhase = CyclePhase.OVULATION
                daysUntilNextPhase = (ovulationDay - dayInCycle).coerceAtLeast(1)
            }
            dayInCycle == ovulationDay -> {
                currentPhase = CyclePhase.OVULATION
                nextPhase = CyclePhase.LUTEAL
                daysUntilNextPhase = 1
            }
            dayInCycle <= (effectiveCycleLength - 4) -> {
                currentPhase = CyclePhase.LUTEAL
                nextPhase = CyclePhase.LATE_LUTEAL
                daysUntilNextPhase = ((effectiveCycleLength - 3) - dayInCycle).coerceAtLeast(1)
            }
            else -> {
                currentPhase = CyclePhase.LATE_LUTEAL
                nextPhase = CyclePhase.MENSTRUAL
                daysUntilNextPhase = maxOf(1, daysUntilNextPeriod)
            }
        }

        return CycleStatus(
            currentPhase = currentPhase,
            dayInCycle = dayInCycle,
            totalCycleDays = effectiveCycleLength,
            periodLength = periodLength,
            daysUntilNextPeriod = daysUntilNextPeriod,
            isLate = isLate,
            daysLate = daysLate,
            expectedNextDateMillis = expectedNextMillis,
            earliestDateMillis = earliestMillis,
            latestDateMillis = latestMillis,
            averageCycleLength = simpleAvg,
            sigmaVarianceDays = sigma,
            cycleCount = intervals.size + 1,
            lastPeriodStartDateMillis = periodStart,
            nextPhase = nextPhase,
            daysUntilNextPhase = daysUntilNextPhase,
            isPeriodCurrentlyActive = isPeriodCurrentlyActive,
            activePeriodDays = activePeriodDays,
            activePeriodId = activePeriodId,
            predictedWindowStartMillis = earliestMillis,
            predictedWindowEndMillis = predictedWindowEndMillis
        )
    }

    fun buildHistoryItems(
        periods: List<PeriodLog>,
        defaultCycleLength: Int = 28,
        defaultPeriodLength: Int = 5,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): List<CycleHistoryItem> {
        val sorted = periods.sortedByDescending { it.startDateMillis }
        val analysis = PeriodAnomalyDetector.analyze(
            periods = periods,
            defaultCycleLength = defaultCycleLength,
            defaultPeriodLength = defaultPeriodLength,
            referenceTimeMillis = referenceTimeMillis
        )
        val anomalyMap = analysis.detectedAnomalies.associateBy { it.periodId }
        val result = mutableListOf<CycleHistoryItem>()

        val today = startOfDayMillis(referenceTimeMillis)

        for (i in sorted.indices) {
            val current = sorted[i]
            val interval: Int? = if (i + 1 < sorted.size) {
                val curDay = startOfDayMillis(current.startDateMillis)
                val prevDay = startOfDayMillis(sorted[i + 1].startDateMillis)
                val diff = ((curDay - prevDay) / MS_PER_DAY).toInt()
                if (diff > 0) diff else null
            } else {
                null
            }

            val duration = current.durationDays ?: if (current.endDateMillis != null && current.endDateMillis >= current.startDateMillis) {
                val d = round((startOfDayMillis(current.endDateMillis) - startOfDayMillis(current.startDateMillis)).toDouble() / MS_PER_DAY).toInt() + 1
                if (d in 1..20) d else null
            } else null

            val daysSinceStart = ((today - startOfDayMillis(current.startDateMillis)) / MS_PER_DAY).toInt()
            val isOngoing = (current.endDateMillis == null && i == 0 && daysSinceStart in 0..14)

            val anomaly = anomalyMap[current.id]

            result.add(
                CycleHistoryItem(
                    periodId = current.id,
                    startDateMillis = current.startDateMillis,
                    endDateMillis = current.endDateMillis,
                    cycleDaysToNext = interval,
                    durationDays = duration,
                    isMostRecent = (i == 0),
                    isOngoing = isOngoing,
                    notes = current.notes,
                    anomalyType = anomaly?.anomalyType ?: AnomalyType.NORMAL,
                    anomalyMessage = anomaly?.message
                )
            )
        }
        return result
    }

    fun computePhaseForDate(
        targetMillis: Long,
        periods: List<PeriodLog>,
        defaultCycleLength: Int = 28,
        defaultPeriodLength: Int = 5
    ): Pair<CalendarCyclePhase, Int> {
        val targetDay = startOfDayMillis(targetMillis)

        // Check if targetDay falls within any recorded period window with exact start and end dates
        for (period in periods) {
            val pStart = startOfDayMillis(period.startDateMillis)
            val pEnd = if (period.endDateMillis != null && period.endDateMillis >= period.startDateMillis) {
                startOfDayMillis(period.endDateMillis)
            } else if (period.durationDays != null) {
                pStart + ((period.durationDays - 1).toLong() * MS_PER_DAY)
            } else {
                pStart + ((defaultPeriodLength - 1).toLong() * MS_PER_DAY)
            }
            if (targetDay in pStart..pEnd) {
                val dayInBleed = round((targetDay - pStart).toDouble() / MS_PER_DAY).toInt() + 1
                return Pair(CalendarCyclePhase.MENSTRUATION, dayInBleed)
            }
        }

        val status = compute(periods, defaultCycleLength, defaultPeriodLength, referenceTimeMillis = targetMillis)
        val effectiveCycleLength = status.totalCycleDays.coerceAtLeast(20)
        val periodLength = status.periodLength

        val sortedAsc = periods.map { startOfDayMillis(it.startDateMillis) }.distinct().sorted()

        if (sortedAsc.isEmpty()) {
            val today = startOfDayMillis(System.currentTimeMillis())
            val diffFromToday = ((targetDay - today) / MS_PER_DAY).toInt()
            val normalizedDay = ((diffFromToday % effectiveCycleLength) + effectiveCycleLength) % effectiveCycleLength + 1
            val ovDay = safeCoerceIn(effectiveCycleLength - 14, minVal = periodLength + 2, maxVal = effectiveCycleLength - 3)
            val phase = when {
                normalizedDay <= periodLength -> CalendarCyclePhase.MENSTRUATION
                normalizedDay <= (ovDay - 3) -> CalendarCyclePhase.FOLLICULAR
                normalizedDay <= (ovDay + 1) -> CalendarCyclePhase.OVULATORY
                else -> CalendarCyclePhase.LUTEAL
            }
            return Pair(phase, normalizedDay)
        }

        val mostRecentStart = sortedAsc.last()
        val earliestStart = sortedAsc.first()

        val dayInCycle: Int
        val cycleLengthForDate: Int

        when {
            targetDay >= mostRecentStart -> {
                val diffDays = ((targetDay - mostRecentStart) / MS_PER_DAY).toInt()
                val cycleIndex = diffDays / effectiveCycleLength
                val cycleStart = mostRecentStart + (cycleIndex.toLong() * effectiveCycleLength * MS_PER_DAY)
                dayInCycle = (((targetDay - cycleStart) / MS_PER_DAY).toInt() + 1).coerceAtLeast(1)
                cycleLengthForDate = effectiveCycleLength
            }
            targetDay < earliestStart -> {
                val diffDays = ((earliestStart - targetDay) / MS_PER_DAY).toInt()
                val cyclesBack = ((diffDays - 1) / effectiveCycleLength) + 1
                val projectedCycleStart = earliestStart - (cyclesBack.toLong() * effectiveCycleLength * MS_PER_DAY)
                dayInCycle = (((targetDay - projectedCycleStart) / MS_PER_DAY).toInt() + 1).coerceAtLeast(1)
                cycleLengthForDate = effectiveCycleLength
            }
            else -> {
                var intervalStart = earliestStart
                var intervalLen = effectiveCycleLength
                for (i in 0 until sortedAsc.size - 1) {
                    if (targetDay >= sortedAsc[i] && targetDay < sortedAsc[i + 1]) {
                        intervalStart = sortedAsc[i]
                        val actualDiff = ((sortedAsc[i + 1] - sortedAsc[i]) / MS_PER_DAY).toInt()
                        if (actualDiff in 18..60) {
                            intervalLen = actualDiff
                        }
                        break
                    }
                }
                dayInCycle = (((targetDay - intervalStart) / MS_PER_DAY).toInt() + 1).coerceAtLeast(1)
                cycleLengthForDate = intervalLen
            }
        }

        val ovDay = safeCoerceIn(cycleLengthForDate - 14, minVal = periodLength + 2, maxVal = cycleLengthForDate - 3)
        val phase = when {
            dayInCycle <= periodLength -> CalendarCyclePhase.MENSTRUATION
            dayInCycle <= (ovDay - 3) -> CalendarCyclePhase.FOLLICULAR
            dayInCycle <= (ovDay + 1) -> CalendarCyclePhase.OVULATORY
            else -> CalendarCyclePhase.LUTEAL
        }

        return Pair(phase, dayInCycle)
    }

    /**
     * Calculates the upcoming prediction window for future cycles based on logged period history.
     * Accurately projects multiple future cycles (up to horizonCycles) based on weighted moving
     * average, standard deviation variance, and calibrated duration.
     */
    fun computeUpcomingPredictionWindows(
        periods: List<PeriodLog>,
        defaultCycleLength: Int = 28,
        defaultPeriodLength: Int = 5,
        horizonCycles: Int = 6,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): List<PredictionWindow> {
        val todayStart = startOfDayMillis(referenceTimeMillis)
        val sortedAsc = periods.map { startOfDayMillis(it.startDateMillis) }.distinct().sorted()
        val status = compute(periods, defaultCycleLength, defaultPeriodLength, referenceTimeMillis)
        val cycleLen = status.totalCycleDays
        val periodLen = status.periodLength
        val sigmaDays = status.sigmaVarianceDays.roundToInt().coerceIn(1, 5)

        val anchorMillis = if (sortedAsc.isNotEmpty()) {
            sortedAsc.last()
        } else {
            todayStart - (defaultCycleLength.toLong() * MS_PER_DAY)
        }

        val windows = mutableListOf<PredictionWindow>()
        val daysSinceAnchor = ((todayStart - anchorMillis) / MS_PER_DAY).toInt()
        val startCycleIndex = if (daysSinceAnchor > cycleLen) {
            (daysSinceAnchor / cycleLen).coerceAtLeast(1)
        } else {
            1
        }

        var count = 0
        var c = startCycleIndex
        while (count < horizonCycles && c < startCycleIndex + 12) {
            val expectedStart = anchorMillis + (c.toLong() * cycleLen * MS_PER_DAY)
            // Uncertainty expands slightly with further cycles (count * 1.2)
            val windowSpread = (sigmaDays + (count * 1.2).roundToInt()).coerceIn(2, 8)
            val earliestStart = expectedStart - (windowSpread.toLong() * MS_PER_DAY)
            val latestStart = expectedStart + (windowSpread.toLong() * MS_PER_DAY)
            val windowEnd = latestStart + ((periodLen - 1).toLong() * MS_PER_DAY)

            if (windowEnd >= todayStart - (2L * MS_PER_DAY)) {
                windows.add(
                    PredictionWindow(
                        cycleIndex = count + 1,
                        earliestDateMillis = earliestStart,
                        expectedDateMillis = expectedStart,
                        latestDateMillis = latestStart,
                        windowEndDateMillis = windowEnd,
                        durationDays = periodLen
                    )
                )
                count++
            }
            c++
        }
        return windows
    }

    fun generateMonthCalendarDays(
        year: Int,
        month: Int,
        periods: List<PeriodLog>,
        defaultCycleLength: Int = 28,
        defaultPeriodLength: Int = 5,
        todayMillis: Long = System.currentTimeMillis()
    ): List<CalendarDayData> {
        val todayStart = startOfDayMillis(todayMillis)

        val firstDayCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dayOfWeek = firstDayCal.get(Calendar.DAY_OF_WEEK)
        val leadDays = dayOfWeek - 1

        val cursorCal = Calendar.getInstance().apply {
            timeInMillis = firstDayCal.timeInMillis - (leadDays.toLong() * MS_PER_DAY)
        }

        val periodStartSet = periods.map { startOfDayMillis(it.startDateMillis) }.toSet()
        val predictionWindows = computeUpcomingPredictionWindows(
            periods = periods,
            defaultCycleLength = defaultCycleLength,
            defaultPeriodLength = defaultPeriodLength,
            horizonCycles = 6,
            referenceTimeMillis = todayMillis
        )

        val daysList = mutableListOf<CalendarDayData>()
        for (i in 0 until 42) {
            val currentMillis = cursorCal.timeInMillis
            val curYear = cursorCal.get(Calendar.YEAR)
            val curMonth = cursorCal.get(Calendar.MONTH)
            val curDayOfMonth = cursorCal.get(Calendar.DAY_OF_MONTH)

            val isCurrentMonth = (curYear == year && curMonth == month)
            val isToday = (currentMillis == todayStart)

            val (phase, dayInCycle) = computePhaseForDate(
                targetMillis = currentMillis,
                periods = periods,
                defaultCycleLength = defaultCycleLength,
                defaultPeriodLength = defaultPeriodLength
            )

            val isPeriodStart = periodStartSet.contains(currentMillis)
            val isPeriodDay = phase == CalendarCyclePhase.MENSTRUATION

            val isHistoricalLogged = periods.any { p ->
                val pStart = startOfDayMillis(p.startDateMillis)
                val pEnd = if (p.endDateMillis != null && p.endDateMillis >= p.startDateMillis) {
                    startOfDayMillis(p.endDateMillis)
                } else if (p.durationDays != null) {
                    pStart + ((p.durationDays - 1).toLong() * MS_PER_DAY)
                } else {
                    pStart + ((defaultPeriodLength - 1).toLong() * MS_PER_DAY)
                }
                currentMillis in pStart..pEnd
            }

            // Check if current date falls within any calculated future prediction window
            val matchingWindow = predictionWindows.find { w ->
                currentMillis in w.earliestDateMillis..w.windowEndDateMillis && currentMillis >= todayStart
            }
            val isInPredictedWindow = matchingWindow != null && !isHistoricalLogged
            val isPredictedPeriodStart = matchingWindow != null && currentMillis == matchingWindow.expectedDateMillis

            val isPredicted = (currentMillis > todayStart) || (isPeriodDay && !isHistoricalLogged) || isInPredictedWindow

            daysList.add(
                CalendarDayData(
                    dateMillis = currentMillis,
                    year = curYear,
                    month = curMonth,
                    dayOfMonth = curDayOfMonth,
                    isCurrentMonth = isCurrentMonth,
                    isToday = isToday,
                    phase = phase,
                    dayInCycle = dayInCycle,
                    totalCycleDays = defaultCycleLength,
                    isPeriodStart = isPeriodStart,
                    isPeriodDay = isPeriodDay,
                    isHistoricalLogged = isHistoricalLogged,
                    isPredicted = isPredicted,
                    isInPredictedWindow = isInPredictedWindow,
                    isPredictedPeriodStart = isPredictedPeriodStart
                )
            )

            cursorCal.add(Calendar.DAY_OF_MONTH, 1)
        }

        return daysList
    }
}

data class PredictionWindow(
    val cycleIndex: Int,
    val earliestDateMillis: Long,
    val expectedDateMillis: Long,
    val latestDateMillis: Long,
    val windowEndDateMillis: Long,
    val durationDays: Int
)
