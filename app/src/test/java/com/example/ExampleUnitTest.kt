package com.example

import androidx.compose.ui.graphics.Color
import com.example.data.PeriodLog
import com.example.model.AnomalyType
import com.example.model.CalendarCyclePhase
import com.example.model.CycleCalculator
import com.example.model.PeriodAnomalyDetector
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun `calendar cycle phases have four distinct colors`() {
        val phases = CalendarCyclePhase.values()
        assertEquals(4, phases.size)

        val colors = phases.map { it.dotColor }.toSet()
        assertEquals("Each of the 4 phases must have a distinct color", 4, colors.size)

        assertTrue(phases.contains(CalendarCyclePhase.MENSTRUATION))
        assertTrue(phases.contains(CalendarCyclePhase.FOLLICULAR))
        assertTrue(phases.contains(CalendarCyclePhase.OVULATORY))
        assertTrue(phases.contains(CalendarCyclePhase.LUTEAL))
    }

    @Test
    fun `computePhaseForDate correctly identifies phases across 28 day cycle`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis

        val period = PeriodLog(id = 1, startDateMillis = startMillis, notes = "October start")
        val periods = listOf(period)

        // Day 1: Menstruation
        val (phaseDay1, day1) = CycleCalculator.computePhaseForDate(startMillis, periods, 28, 5)
        assertEquals(CalendarCyclePhase.MENSTRUATION, phaseDay1)
        assertEquals(1, day1)

        // Day 7: Follicular
        val day7Millis = startMillis + (6L * 24 * 60 * 60 * 1000)
        val (phaseDay7, day7) = CycleCalculator.computePhaseForDate(day7Millis, periods, 28, 5)
        assertEquals(CalendarCyclePhase.FOLLICULAR, phaseDay7)
        assertEquals(7, day7)

        // Day 14: Ovulatory
        val day14Millis = startMillis + (13L * 24 * 60 * 60 * 1000)
        val (phaseDay14, day14) = CycleCalculator.computePhaseForDate(day14Millis, periods, 28, 5)
        assertEquals(CalendarCyclePhase.OVULATORY, phaseDay14)
        assertEquals(14, day14)

        // Day 20: Luteal
        val day20Millis = startMillis + (19L * 24 * 60 * 60 * 1000)
        val (phaseDay20, day20) = CycleCalculator.computePhaseForDate(day20Millis, periods, 28, 5)
        assertEquals(CalendarCyclePhase.LUTEAL, phaseDay20)
        assertEquals(20, day20)
    }

    @Test
    fun `generateMonthCalendarDays generates 42 days for complete grid`() {
        val days = CycleCalculator.generateMonthCalendarDays(
            year = 2026,
            month = Calendar.OCTOBER,
            periods = emptyList(),
            defaultCycleLength = 28,
            defaultPeriodLength = 5
        )
        assertEquals(42, days.size)
        // Ensure every day has a non-null phase and valid dot color
        days.forEach { day ->
            assertNotNull(day.phase)
            assertNotNull(day.phase.dotColor)
            assertTrue(day.dayInCycle >= 1)
        }
    }

    @Test
    fun `detects unusually long historical cycle interval`() {
        val baseTime = 1700000000000L // arbitrary fixed timestamp
        val dayMs = 24L * 60 * 60 * 1000

        // Period 1 (latest): baseTime
        // Period 2: baseTime - 40 days (anomalously long interval of 40 days)
        // Period 3: baseTime - 68 days (normal interval of 28 days)
        // Period 4: baseTime - 96 days (normal interval of 28 days)
        val p1 = PeriodLog(id = 1, startDateMillis = baseTime)
        val p2 = PeriodLog(id = 2, startDateMillis = baseTime - (40L * dayMs))
        val p3 = PeriodLog(id = 3, startDateMillis = baseTime - (68L * dayMs))
        val p4 = PeriodLog(id = 4, startDateMillis = baseTime - (96L * dayMs))

        val analysis = PeriodAnomalyDetector.analyze(
            periods = listOf(p1, p2, p3, p4),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = baseTime + (5L * dayMs)
        )

        val longAnomaly = analysis.detectedAnomalies.find { it.anomalyType == AnomalyType.UNUSUALLY_LONG }
        assertNotNull("Should detect 40-day interval as unusually long", longAnomaly)
        assertEquals(40, longAnomaly?.cycleLengthDays)
    }

    @Test
    fun `detects unusually short historical cycle interval`() {
        val baseTime = 1700000000000L
        val dayMs = 24L * 60 * 60 * 1000

        // Interval of 20 days (unusually short) followed by 28 days intervals
        val p1 = PeriodLog(id = 1, startDateMillis = baseTime)
        val p2 = PeriodLog(id = 2, startDateMillis = baseTime - (20L * dayMs))
        val p3 = PeriodLog(id = 3, startDateMillis = baseTime - (48L * dayMs))
        val p4 = PeriodLog(id = 4, startDateMillis = baseTime - (76L * dayMs))

        val analysis = PeriodAnomalyDetector.analyze(
            periods = listOf(p1, p2, p3, p4),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = baseTime + (5L * dayMs)
        )

        val shortAnomaly = analysis.detectedAnomalies.find { it.anomalyType == AnomalyType.UNUSUALLY_SHORT }
        assertNotNull("Should detect 20-day interval as unusually short", shortAnomaly)
        assertEquals(20, shortAnomaly?.cycleLengthDays)
    }

    @Test
    fun `detects ongoing current cycle delayed anomaly`() {
        val baseTime = 1700000000000L
        val dayMs = 24L * 60 * 60 * 1000

        // Most recent period was 36 days ago
        val p1 = PeriodLog(id = 1, startDateMillis = baseTime)
        val p2 = PeriodLog(id = 2, startDateMillis = baseTime - (28L * dayMs))

        val analysis = PeriodAnomalyDetector.analyze(
            periods = listOf(p1, p2),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = baseTime + (36L * dayMs) // today is Day 37
        )

        assertNotNull("Should detect current cycle as unusually delayed", analysis.currentCycleAnomaly)
        assertEquals(AnomalyType.UNUSUALLY_LONG, analysis.currentCycleAnomaly?.anomalyType)
        assertTrue(analysis.currentCycleAnomaly?.deviationDays ?: 0 > 0)
    }

    @Test
    fun `predicts unusual cycle length when recent trend is lengthening`() {
        val baseTime = 1700000000000L
        val dayMs = 24L * 60 * 60 * 1000

        // Recent intervals: 36 days, 34 days, 31 days (systematic lengthening drift)
        val p1 = PeriodLog(id = 1, startDateMillis = baseTime)
        val p2 = PeriodLog(id = 2, startDateMillis = baseTime - (36L * dayMs))
        val p3 = PeriodLog(id = 3, startDateMillis = baseTime - (70L * dayMs)) // 34d
        val p4 = PeriodLog(id = 4, startDateMillis = baseTime - (101L * dayMs)) // 31d

        val analysis = PeriodAnomalyDetector.analyze(
            periods = listOf(p1, p2, p3, p4),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = baseTime + (5L * dayMs)
        )

        assertEquals(AnomalyType.UNUSUALLY_LONG, analysis.forecast.anomalyRiskType)
        assertTrue("Expected cycle length should be predicted longer than 33 days", analysis.forecast.expectedCycleLengthDays >= 34)
        assertTrue("Risk probability should be elevated", analysis.forecast.riskProbabilityPercent >= 50)
    }

    @Test
    fun `detects prolonged and unusually short bleeding flow duration anomalies`() {
        val baseTime = 1700000000000L
        val dayMs = 24L * 60 * 60 * 1000

        // Period with 9 days bleeding duration (prolonged / unusually long flow)
        val p1 = PeriodLog(
            id = 1,
            startDateMillis = baseTime,
            endDateMillis = baseTime + (8L * dayMs),
            durationDays = 9
        )
        // Period with 2 days bleeding duration (unusually short flow)
        val p2 = PeriodLog(
            id = 2,
            startDateMillis = baseTime - (28L * dayMs),
            endDateMillis = baseTime - (27L * dayMs),
            durationDays = 2
        )
        val p3 = PeriodLog(
            id = 3,
            startDateMillis = baseTime - (56L * dayMs),
            endDateMillis = baseTime - (52L * dayMs),
            durationDays = 5
        )

        val analysis = PeriodAnomalyDetector.analyze(
            periods = listOf(p1, p2, p3),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = baseTime + (10L * dayMs)
        )

        val longBleed = analysis.detectedAnomalies.find { it.periodId == 1L }
        assertNotNull("Should detect 9-day bleeding duration as unusually long", longBleed)
        assertEquals(AnomalyType.UNUSUALLY_LONG, longBleed?.anomalyType)

        val shortBleed = analysis.detectedAnomalies.find { it.periodId == 2L }
        assertNotNull("Should detect 2-day bleeding duration as unusually short", shortBleed)
        assertEquals(AnomalyType.UNUSUALLY_SHORT, shortBleed?.anomalyType)
    }

    @Test
    fun `logging in period start and logging out period end updates active status and duration`() {
        val today = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 10, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 24L * 60 * 60 * 1000

        // 1. User logs in period start on Day 1 (no end date yet)
        val ongoingPeriod = PeriodLog(
            id = 10,
            startDateMillis = today - (2L * dayMs), // started 2 days ago
            notes = "Flow started"
        )

        val activeStatus = CycleCalculator.compute(
            periods = listOf(ongoingPeriod),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = today
        )

        assertTrue("Period should be marked active when ongoing without end date", activeStatus.isPeriodCurrentlyActive)
        assertEquals(10L, activeStatus.activePeriodId)
        assertEquals(3, activeStatus.activePeriodDays) // Day 3 of flow

        // 2. User logs out period indicating period ending on Day 5
        val endedPeriod = ongoingPeriod.copy(
            endDateMillis = today + (2L * dayMs),
            durationDays = 5
        )

        val endedStatus = CycleCalculator.compute(
            periods = listOf(endedPeriod),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            referenceTimeMillis = today + (3L * dayMs) // day after period ended
        )

        assertFalse("Period should no longer be active after logout", endedStatus.isPeriodCurrentlyActive)
        assertEquals(5, endedStatus.periodLength)

        // 3. buildHistoryItems properly marks isOngoing
        val historyActive = CycleCalculator.buildHistoryItems(listOf(ongoingPeriod), 28, 5, referenceTimeMillis = today)
        assertTrue("Recent period without end date should be marked isOngoing", historyActive.first().isOngoing)

        val historyEnded = CycleCalculator.buildHistoryItems(listOf(endedPeriod), 28, 5, referenceTimeMillis = today + (3L * dayMs))
        assertFalse("Ended period should not be marked isOngoing", historyEnded.first().isOngoing)
        assertEquals(5, historyEnded.first().durationDays)
    }

    @Test
    fun `edge cases of short cycle and long bleeding do not crash calculations`() {
        val now = System.currentTimeMillis()
        val periods = listOf(
            PeriodLog(id = 1, startDateMillis = now, durationDays = 14)
        )
        // Extreme configuration: 18-day cycle with 14-day period
        val status = CycleCalculator.compute(
            periods = periods,
            defaultCycleLength = 18,
            defaultPeriodLength = 14,
            referenceTimeMillis = now
        )
        assertNotNull(status)
        assertTrue(status.totalCycleDays >= 18)

        // computePhaseForDate does not throw
        val (phase, day) = CycleCalculator.computePhaseForDate(
            targetMillis = now + (10L * 24 * 60 * 60 * 1000),
            periods = periods,
            defaultCycleLength = 18,
            defaultPeriodLength = 14
        )
        assertNotNull(phase)
        assertTrue(day >= 1)

        // generateMonthCalendarDays does not throw
        val monthDays = CycleCalculator.generateMonthCalendarDays(
            year = 2026,
            month = 9,
            periods = periods,
            defaultCycleLength = 18,
            defaultPeriodLength = 14,
            todayMillis = now
        )
        assertEquals(42, monthDays.size)
    }

    @Test
    fun `notification greetings are time-dependent based on current hour`() {
        val morningCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 9) }
        val afternoonCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 14) }
        val eveningCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 19) }
        val nightCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 23) }
        val earlyHoursCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 2) }

        assertEquals("Good morning", com.example.notifications.NotificationHelper.getTimeGreeting(morningCal))
        assertEquals("Good afternoon", com.example.notifications.NotificationHelper.getTimeGreeting(afternoonCal))
        assertEquals("Good evening", com.example.notifications.NotificationHelper.getTimeGreeting(eveningCal))
        assertEquals("Good night", com.example.notifications.NotificationHelper.getTimeGreeting(nightCal))
        assertEquals("Good night", com.example.notifications.NotificationHelper.getTimeGreeting(earlyHoursCal))

        assertEquals("Good morning, Krishi", com.example.notifications.NotificationHelper.getTimeGreeting(morningCal, "Krishi"))
        assertEquals("Good afternoon, Krishi", com.example.notifications.NotificationHelper.getTimeGreeting(afternoonCal, "Krishi"))
        assertEquals("Good evening, Krishi", com.example.notifications.NotificationHelper.getTimeGreeting(eveningCal, "Krishi"))
        assertEquals("Good night, Krishi", com.example.notifications.NotificationHelper.getTimeGreeting(nightCal, "Krishi"))
    }

    @Test
    fun `formatTimeDependentGreeting adapts hardcoded morning messages to afternoon and evening`() {
        val afternoonCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 15) }
        val eveningCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 20) }
        val morningCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 8) }
        val nightCal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 22) }

        val originalMessage = "Good morning queen! Time for your glow vitamins 🌸 Drink plenty of water and have a lovely day!"

        val afternoonResult = com.example.notifications.NotificationHelper.formatTimeDependentGreeting(originalMessage, afternoonCal)
        assertTrue("In the afternoon, 'Good morning' must become 'Good afternoon'", afternoonResult.startsWith("Good afternoon queen!"))

        val eveningResult = com.example.notifications.NotificationHelper.formatTimeDependentGreeting(originalMessage, eveningCal)
        assertTrue("In the evening, 'Good morning' must become 'Good evening'", eveningResult.startsWith("Good evening queen!"))

        val nightResult = com.example.notifications.NotificationHelper.formatTimeDependentGreeting(originalMessage, nightCal)
        assertTrue("At night, 'Good morning' must become 'Good night'", nightResult.startsWith("Good night queen!"))

        val morningResult = com.example.notifications.NotificationHelper.formatTimeDependentGreeting(originalMessage, morningCal)
        assertTrue("In the morning, 'Good morning' is retained", morningResult.startsWith("Good morning queen!"))
    }

    @Test
    fun `computeUpcomingPredictionWindows projects upcoming cycles with dynamic variance`() {
        val baseTime = 1700000000000L
        val dayMs = 24L * 60 * 60 * 1000

        // 3 logged historical cycles: 28 days, 30 days
        val p1 = PeriodLog(id = 1, startDateMillis = baseTime, durationDays = 5)
        val p2 = PeriodLog(id = 2, startDateMillis = baseTime - (28L * dayMs), durationDays = 5)
        val p3 = PeriodLog(id = 3, startDateMillis = baseTime - (58L * dayMs), durationDays = 5)

        val windows = CycleCalculator.computeUpcomingPredictionWindows(
            periods = listOf(p1, p2, p3),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            horizonCycles = 3,
            referenceTimeMillis = baseTime
        )

        assertTrue("Should compute at least 1 upcoming prediction window", windows.isNotEmpty())
        val firstWindow = windows.first()
        assertTrue("Earliest start must be before or equal to expected start", firstWindow.earliestDateMillis <= firstWindow.expectedDateMillis)
        assertTrue("Latest start must be after or equal to expected start", firstWindow.latestDateMillis >= firstWindow.expectedDateMillis)
        assertTrue("Window end must cover bleeding duration", firstWindow.windowEndDateMillis >= firstWindow.latestDateMillis)
        assertEquals(5, firstWindow.durationDays)
    }

    @Test
    fun `calendar days in predicted window are marked with prediction flags`() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMillis = cal.timeInMillis
        val p1 = PeriodLog(id = 1, startDateMillis = startMillis, durationDays = 5)

        val monthDays = CycleCalculator.generateMonthCalendarDays(
            year = 2026,
            month = Calendar.OCTOBER,
            periods = listOf(p1),
            defaultCycleLength = 28,
            defaultPeriodLength = 5,
            todayMillis = startMillis
        )

        // The upcoming cycle is around startMillis + 28 days (October 29)
        val predictedDays = monthDays.filter { it.isInPredictedWindow }
        assertTrue("Calendar month grid must identify days in predicted period window", predictedDays.isNotEmpty())
        val predictedStartDay = monthDays.find { it.isPredictedPeriodStart }
        assertNotNull("Expected predicted period start day must be marked", predictedStartDay)
    }
}

