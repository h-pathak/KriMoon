package com.example.model

import com.example.data.PeriodLog
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class AnomalyType(val label: String, val emoji: String) {
    NORMAL("Typical Length", "✨"),
    UNUSUALLY_LONG("Unusually Long", "⚠️"),
    UNUSUALLY_SHORT("Unusually Short", "⚡")
}

enum class AnomalySeverity(val label: String) {
    NONE("Normal"),
    MILD("Mild Deviation"),
    MODERATE("Moderate Deviation"),
    SIGNIFICANT("Significant Deviation")
}

enum class VariabilityLevel(val title: String, val description: String) {
    HIGHLY_REGULAR("Highly Regular", "Cycles follow a consistent, predictable rhythm (variance < 2.5 days)."),
    MODERATE_VARIABILITY("Moderate Variability", "Normal physiological fluctuations of 3 to 5 days between cycles."),
    HIGH_IRREGULARITY("High Irregularity", "Higher variation observed (> 6 days); atypical lengths are more frequent.")
}

data class PeriodAnomalyRecord(
    val periodId: Long,
    val startDateMillis: Long,
    val cycleLengthDays: Int?,
    val durationDays: Int?,
    val anomalyType: AnomalyType,
    val severity: AnomalySeverity,
    val deviationDays: Int,
    val message: String
)

data class PredictedCycleForecast(
    val expectedCycleLengthDays: Int,
    val confidenceLowerDays: Int,
    val confidenceUpperDays: Int,
    val predictedNextPeriodMillis: Long,
    val anomalyRiskType: AnomalyType,
    val riskSeverity: AnomalySeverity,
    val riskProbabilityPercent: Int,
    val trendSlopeDaysPerCycle: Double,
    val forecastSummary: String,
    val physiologicalCauses: List<String>,
    val wellnessTips: List<String>
)

data class PeriodAnomalyAnalysis(
    val baselineCycleLength: Double,
    val baselinePeriodLength: Double,
    val standardDeviationDays: Double,
    val variabilityLevel: VariabilityLevel,
    val stabilityScorePercent: Int, // 0 to 100
    val totalCyclesAnalyzed: Int,
    val detectedAnomalies: List<PeriodAnomalyRecord>,
    val currentCycleAnomaly: PeriodAnomalyRecord?,
    val forecast: PredictedCycleForecast
)

object PeriodAnomalyDetector {

    private const val MS_PER_DAY = 24L * 60 * 60 * 1000

    private fun startOfDayMillis(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Statistical algorithm to detect past and ongoing cycle length anomalies,
     * calculate stability and variance, and predict whether upcoming periods
     * will be unusually long or short.
     */
    fun analyze(
        periods: List<PeriodLog>,
        defaultCycleLength: Int = 28,
        defaultPeriodLength: Int = 5,
        referenceTimeMillis: Long = System.currentTimeMillis()
    ): PeriodAnomalyAnalysis {
        val today = startOfDayMillis(referenceTimeMillis)
        val sortedPeriods = periods.sortedByDescending { it.startDateMillis }

        // Extract historical intervals between consecutive period starts
        val intervals = mutableListOf<Int>()
        for (i in 0 until sortedPeriods.size - 1) {
            val start1 = startOfDayMillis(sortedPeriods[i].startDateMillis)
            val start2 = startOfDayMillis(sortedPeriods[i + 1].startDateMillis)
            val diff = kotlin.math.round((start1 - start2).toDouble() / MS_PER_DAY).toInt()
            if (diff in 14..75) {
                intervals.add(diff)
            }
        }

        // Robust baseline using median or defaultCycleLength to prevent masking by outliers
        val baseline: Double = if (intervals.isNotEmpty()) {
            val sorted = intervals.sorted()
            if (sorted.size % 2 == 1) {
                sorted[sorted.size / 2].toDouble()
            } else {
                (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
            }
        } else {
            defaultCycleLength.toDouble()
        }

        val avgCycleLength: Double = if (intervals.isNotEmpty()) {
            intervals.average()
        } else {
            defaultCycleLength.toDouble()
        }

        // Standard Deviation σ
        val sigma: Double = if (intervals.size >= 2) {
            val variance = intervals.map { (it - baseline) * (it - baseline) }.average()
            sqrt(variance).coerceIn(1.2, 8.0)
        } else {
            1.8
        }

        // Stability Score: 100% when σ <= 1.5d, tapering to 40% when σ >= 6.5d
        val stabilityScore = ((1.0 - ((sigma - 1.2) / 6.0)).coerceIn(0.2, 1.0) * 100).roundToInt()

        val variabilityLevel = when {
            sigma <= 2.2 -> VariabilityLevel.HIGHLY_REGULAR
            sigma <= 4.8 -> VariabilityLevel.MODERATE_VARIABILITY
            else -> VariabilityLevel.HIGH_IRREGULARITY
        }

        // Clinical (FIGO) & statistical anomaly criteria
        fun isUnusuallyLong(cycleDays: Int): Boolean {
            val diff = cycleDays - baseline
            return (cycleDays >= 35 && diff >= 4.0) || diff >= max(1.6 * sigma, 5.0)
        }

        fun isUnusuallyShort(cycleDays: Int): Boolean {
            val diff = baseline - cycleDays
            return (cycleDays <= 21 && diff >= 4.0) || diff >= max(1.6 * sigma, 5.0)
        }

        // Detect anomalies in logged historical cycles
        val detectedAnomalies = mutableListOf<PeriodAnomalyRecord>()
        for (i in 0 until sortedPeriods.size - 1) {
            val curPeriod = sortedPeriods[i]
            val prevPeriod = sortedPeriods[i + 1]
            val intervalDays = kotlin.math.round(
                (startOfDayMillis(curPeriod.startDateMillis) - startOfDayMillis(prevPeriod.startDateMillis)).toDouble() / MS_PER_DAY
            ).toInt()

            val deviation = (intervalDays - baseline).roundToInt()
            val absDev = abs(deviation)

            val anomalyType: AnomalyType
            val severity: AnomalySeverity
            val message: String

            when {
                isUnusuallyLong(intervalDays) -> {
                    anomalyType = AnomalyType.UNUSUALLY_LONG
                    severity = when {
                        absDev >= 8 || intervalDays >= 40 -> AnomalySeverity.SIGNIFICANT
                        absDev >= 5 -> AnomalySeverity.MODERATE
                        else -> AnomalySeverity.MILD
                    }
                    message = "Cycle was $intervalDays days (+${absDev}d longer than your ~${baseline.roundToInt()}d baseline)."
                    detectedAnomalies.add(
                        PeriodAnomalyRecord(
                            periodId = curPeriod.id,
                            startDateMillis = curPeriod.startDateMillis,
                            cycleLengthDays = intervalDays,
                            durationDays = curPeriod.durationDays ?: defaultPeriodLength,
                            anomalyType = anomalyType,
                            severity = severity,
                            deviationDays = deviation,
                            message = message
                        )
                    )
                }
                isUnusuallyShort(intervalDays) -> {
                    anomalyType = AnomalyType.UNUSUALLY_SHORT
                    severity = when {
                        absDev >= 7 || intervalDays <= 19 -> AnomalySeverity.SIGNIFICANT
                        absDev >= 4 -> AnomalySeverity.MODERATE
                        else -> AnomalySeverity.MILD
                    }
                    message = "Cycle was $intervalDays days (-${absDev}d shorter than your ~${baseline.roundToInt()}d baseline)."
                    detectedAnomalies.add(
                        PeriodAnomalyRecord(
                            periodId = curPeriod.id,
                            startDateMillis = curPeriod.startDateMillis,
                            cycleLengthDays = intervalDays,
                            durationDays = curPeriod.durationDays ?: defaultPeriodLength,
                            anomalyType = anomalyType,
                            severity = severity,
                            deviationDays = deviation,
                            message = message
                        )
                    )
                }
            }

            // Also detect unusual bleeding flow duration (from logged in start to logged out end)
            val bleedDays = curPeriod.durationDays ?: if (curPeriod.endDateMillis != null && curPeriod.endDateMillis >= curPeriod.startDateMillis) {
                kotlin.math.round((startOfDayMillis(curPeriod.endDateMillis) - startOfDayMillis(curPeriod.startDateMillis)).toDouble() / MS_PER_DAY).toInt() + 1
            } else null

            if (bleedDays != null && detectedAnomalies.none { it.periodId == curPeriod.id }) {
                if (bleedDays >= 8) {
                    detectedAnomalies.add(
                        PeriodAnomalyRecord(
                            periodId = curPeriod.id,
                            startDateMillis = curPeriod.startDateMillis,
                            cycleLengthDays = intervalDays,
                            durationDays = bleedDays,
                            anomalyType = AnomalyType.UNUSUALLY_LONG,
                            severity = if (bleedDays >= 10) AnomalySeverity.SIGNIFICANT else AnomalySeverity.MODERATE,
                            deviationDays = bleedDays - defaultPeriodLength,
                            message = "Bleeding duration was $bleedDays days (+${bleedDays - defaultPeriodLength}d longer flow than your ~${defaultPeriodLength}d average)."
                        )
                    )
                } else if (bleedDays <= 2) {
                    detectedAnomalies.add(
                        PeriodAnomalyRecord(
                            periodId = curPeriod.id,
                            startDateMillis = curPeriod.startDateMillis,
                            cycleLengthDays = intervalDays,
                            durationDays = bleedDays,
                            anomalyType = AnomalyType.UNUSUALLY_SHORT,
                            severity = AnomalySeverity.MILD,
                            deviationDays = defaultPeriodLength - bleedDays,
                            message = "Bleeding duration was $bleedDays days (shorter flow than your ~${defaultPeriodLength}d average)."
                        )
                    )
                }
            }
        }

        // Detect Ongoing Current Cycle Anomaly (if current dayInCycle has exceeded usual length)
        var currentCycleAnomaly: PeriodAnomalyRecord? = null
        if (sortedPeriods.isNotEmpty()) {
            val mostRecent = sortedPeriods.first()
            val daysSinceStart = kotlin.math.round((today - startOfDayMillis(mostRecent.startDateMillis)).toDouble() / MS_PER_DAY).toInt() + 1
            if (isUnusuallyLong(daysSinceStart)) {
                val dev = (daysSinceStart - baseline).roundToInt()
                currentCycleAnomaly = PeriodAnomalyRecord(
                    periodId = mostRecent.id,
                    startDateMillis = mostRecent.startDateMillis,
                    cycleLengthDays = daysSinceStart,
                    durationDays = mostRecent.durationDays ?: defaultPeriodLength,
                    anomalyType = AnomalyType.UNUSUALLY_LONG,
                    severity = if (dev >= 7) AnomalySeverity.MODERATE else AnomalySeverity.MILD,
                    deviationDays = dev,
                    message = "Current cycle is on Day $daysSinceStart (+${dev}d longer than typical). Period is delayed."
                )
            }
        }

        // Predictive Algorithm: Trend & Weighted Moving Average for upcoming period
        val (predictedLength, trendSlope) = computePredictedNextCycleLength(intervals, baseline)

        val confidenceMargin = (1.645 * sigma).roundToInt().coerceAtLeast(2)
        val confLower = (predictedLength - confidenceMargin).coerceAtLeast(18)
        val confUpper = (predictedLength + confidenceMargin).coerceAtMost(60)

        val lastStartMillis = if (sortedPeriods.isNotEmpty()) {
            startOfDayMillis(sortedPeriods.first().startDateMillis)
        } else {
            today - (defaultPeriodLength.toLong() * MS_PER_DAY)
        }
        val predictedNextPeriodMillis = lastStartMillis + (predictedLength.toLong() * MS_PER_DAY)

        val predictedDev = (predictedLength - baseline).roundToInt()
        val isForecastLong = predictedLength >= 35 || isUnusuallyLong(predictedLength) || (trendSlope > 1.2 && predictedDev >= 4)
        val isForecastShort = predictedLength <= 21 || isUnusuallyShort(predictedLength) || (trendSlope < -1.2 && predictedDev <= -4)

        val predictedAnomalyType: AnomalyType
        val predictedSeverity: AnomalySeverity
        val riskProbability: Int
        val forecastSummary: String

        when {
            isForecastLong -> {
                predictedAnomalyType = AnomalyType.UNUSUALLY_LONG
                predictedSeverity = if (abs(predictedDev) >= 6) AnomalySeverity.MODERATE else AnomalySeverity.MILD
                riskProbability = min(88, 50 + (abs(predictedDev) * 7))
                forecastSummary = "Predicted Unusually Long: Trend indicates an upcoming ~${predictedLength}-day cycle (+${abs(predictedDev)}d longer than baseline)."
            }
            isForecastShort -> {
                predictedAnomalyType = AnomalyType.UNUSUALLY_SHORT
                predictedSeverity = if (abs(predictedDev) >= 5) AnomalySeverity.MODERATE else AnomalySeverity.MILD
                riskProbability = min(85, 48 + (abs(predictedDev) * 8))
                forecastSummary = "Predicted Unusually Short: Trend indicates an upcoming ~${predictedLength}-day cycle (-${abs(predictedDev)}d earlier than baseline)."
            }
            else -> {
                predictedAnomalyType = AnomalyType.NORMAL
                predictedSeverity = AnomalySeverity.NONE
                riskProbability = max(10, (sigma * 5).roundToInt())
                forecastSummary = "Predicted Normal: Upcoming cycle expected at ~$predictedLength days ($confLower–$confUpper days window)."
            }
        }

        val physiologicalCauses = when (predictedAnomalyType) {
            AnomalyType.UNUSUALLY_LONG -> listOf(
                "Elevated stress or high cortisol delaying ovulation",
                "Travel across time zones or disrupted circadian sleep rhythm",
                "Sudden calorie restriction or intense athletic training",
                "Illness, recovery, or hormonal adjustments"
            )
            AnomalyType.UNUSUALLY_SHORT -> listOf(
                "Anovulatory cycle or early luteal phase transition",
                "Acute lifestyle or environmental stressor",
                "Perimenopausal or post-pill hormonal recalibration",
                "Subtle shifts in thyroid or progesterone levels"
            )
            AnomalyType.NORMAL -> listOf(
                "Balanced hypothalamic-pituitary-ovarian axis signaling",
                "Stable nutrition, sleep, and restorative recovery"
            )
        }

        val wellnessTips = when (predictedAnomalyType) {
            AnomalyType.UNUSUALLY_LONG -> listOf(
                "Focus on gentle movement (walking, yoga) rather than high-intensity sessions.",
                "Ensure sufficient healthy fats and warm, nutrient-rich meals.",
                "Prioritize 8+ hours of deep, dark sleep to support luteinizing hormone signaling."
            )
            AnomalyType.UNUSUALLY_SHORT -> listOf(
                "Keep extra period essentials in your bag this week in case it arrives early.",
                "Incorporate magnesium and iron-rich foods (leafy greens, lentils, dark chocolate).",
                "Track any spotting or mood shifts in your private Notes tab."
            )
            AnomalyType.NORMAL -> listOf(
                "Continue maintaining your consistent hydration and sleep routines.",
                "Log any subtle pre-menstrual symptoms as your window approaches."
            )
        }

        val forecast = PredictedCycleForecast(
            expectedCycleLengthDays = predictedLength,
            confidenceLowerDays = confLower,
            confidenceUpperDays = confUpper,
            predictedNextPeriodMillis = predictedNextPeriodMillis,
            anomalyRiskType = predictedAnomalyType,
            riskSeverity = predictedSeverity,
            riskProbabilityPercent = riskProbability,
            trendSlopeDaysPerCycle = trendSlope,
            forecastSummary = forecastSummary,
            physiologicalCauses = physiologicalCauses,
            wellnessTips = wellnessTips
        )

        return PeriodAnomalyAnalysis(
            baselineCycleLength = avgCycleLength,
            baselinePeriodLength = defaultPeriodLength.toDouble(),
            standardDeviationDays = sigma,
            variabilityLevel = variabilityLevel,
            stabilityScorePercent = stabilityScore,
            totalCyclesAnalyzed = intervals.size,
            detectedAnomalies = detectedAnomalies,
            currentCycleAnomaly = currentCycleAnomaly,
            forecast = forecast
        )
    }

    /**
     * Weighted moving average + linear trend slope across recent cycles.
     */
    private fun computePredictedNextCycleLength(
        intervals: List<Int>,
        baseline: Double
    ): Pair<Int, Double> {
        if (intervals.isEmpty()) {
            return Pair(baseline.roundToInt(), 0.0)
        }
        if (intervals.size == 1) {
            return Pair(intervals.first(), 0.0)
        }

        // Weighted moving average of up to last 3 cycles (most recent weighted heaviest)
        val recent = intervals.take(3)
        val weights = when (recent.size) {
            2 -> listOf(0.65, 0.35)
            else -> listOf(0.50, 0.30, 0.20)
        }

        var weightedSum = 0.0
        for (i in recent.indices) {
            weightedSum += recent[i] * weights[i]
        }

        // Linear slope: intervals are chronologically newest at index 0
        // trend = (interval[0] - interval[last]) / count
        val trendSlope = if (recent.size >= 2) {
            (recent.first() - recent.last()).toDouble() / (recent.size - 1)
        } else {
            0.0
        }

        // Blend weighted average with trend drift, constrained to realistic biological limits
        val projected = (weightedSum + (trendSlope * 0.5)).coerceIn(19.0, 55.0)
        return Pair(projected.roundToInt(), trendSlope)
    }
}
