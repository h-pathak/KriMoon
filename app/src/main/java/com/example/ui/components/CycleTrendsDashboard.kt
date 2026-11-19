package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DailyCycleLog
import com.example.data.PeriodLog
import com.example.data.UserProfile
import com.example.model.AlgorithmicTrendInsight
import com.example.model.CycleStatus
import com.example.model.CycleTrendPoint
import com.example.model.CycleTrendsAnalytics
import com.example.model.CycleTrendsDashboardData
import com.example.model.PhaseDistributionData
import com.example.model.SymptomTrendItem
import com.example.model.TrendTimeRange
import com.example.ui.theme.PhasesGold
import com.example.ui.theme.PhasesLavender
import com.example.ui.theme.PhasesPeach
import com.example.ui.theme.PhasesPlum
import com.example.ui.theme.PhasesRose
import com.example.ui.theme.PhasesSage
import com.example.ui.theme.PhasesSky
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CycleTrendsDashboard(
    periodLogs: List<PeriodLog>,
    dailyCycleLogs: List<DailyCycleLog>,
    userProfile: UserProfile?,
    cycleStatus: CycleStatus,
    onLogPastPeriod: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedTimeRange by remember { mutableStateOf(TrendTimeRange.LAST_6_MONTHS) }
    var selectedCycleIndex by remember { mutableStateOf<Int?>(null) }

    val defaultCycle = userProfile?.cycleLength ?: 28
    val defaultPeriod = userProfile?.periodLength ?: 5

    val dashboardData = remember(periodLogs, dailyCycleLogs, defaultCycle, defaultPeriod, selectedTimeRange) {
        CycleTrendsAnalytics.computeTrends(
            periodLogs = periodLogs,
            dailyCycleLogs = dailyCycleLogs,
            defaultCycleLength = defaultCycle,
            defaultPeriodLength = defaultPeriod,
            timeRange = selectedTimeRange
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cycle_trends_dashboard"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Dashboard Header & Time Range Filter
        DashboardHeader(
            selectedTimeRange = selectedTimeRange,
            onSelectTimeRange = { range ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedTimeRange = range
                selectedCycleIndex = null
            }
        )

        // 2. Data Confidence / Sparse Data Note (if applicable)
        if (!dashboardData.hasEnoughData) {
            SparseDataHelperCard(
                periodsCount = dashboardData.totalPeriodsLogged,
                onLogPastPeriod = onLogPastPeriod
            )
        }

        // 3. Quick High-Level Metrics Summary Row
        MetricHighlightsGrid(data = dashboardData)

        // 4. Interactive Cycle Length Trends Canvas Chart
        CycleLengthTrendChartCard(
            data = dashboardData,
            selectedCycleIndex = selectedCycleIndex,
            onSelectCycle = { idx ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                selectedCycleIndex = if (selectedCycleIndex == idx) null else idx
            }
        )

        // 5. Monthly Rhythm Trends (Cycle length across the months)
        MonthlyRhythmCard(buckets = dashboardData.monthlyBuckets)

        // 6. Algorithmic Pattern Insights
        AlgorithmicInsightsCard(insights = dashboardData.insights)

        // 7. Action Callout Footer
        DashboardActionFooter(
            onLogPastPeriod = onLogPastPeriod
        )
    }
}

@Composable
private fun DashboardHeader(
    selectedTimeRange: TrendTimeRange,
    onSelectTimeRange: (TrendTimeRange) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(PhasesRose.copy(alpha = 0.2f), PhasesPlum.copy(alpha = 0.2f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "📊", fontSize = 22.sp)
                    }

                    Column {
                        Text(
                            text = "6-Month Health Trends",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Cycle intervals & symptom patterns",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Range Selector Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp)
                ) {
                    TrendTimeRange.values().forEach { range ->
                        val isSelected = selectedTimeRange == range
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                            shadowElevation = if (isSelected) 1.dp else 0.dp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(100.dp))
                                .clickable { onSelectTimeRange(range) }
                                .testTag("time_range_${range.name.lowercase()}")
                        ) {
                            Text(
                                text = range.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SparseDataHelperCard(
    periodsCount: Int,
    onLogPastPeriod: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = PhasesRose.copy(alpha = 0.08f)
        ),
        border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "💡", fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (periodsCount < 2) "Unlock Deeper Predictive Insights" else "Fine-Tune Trends",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Logging past periods helps our statistical algorithm learn your unique variance and forecast upcoming cycles accurately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onLogPastPeriod,
                shape = RoundedCornerShape(100.dp),
                border = BorderStroke(1.dp, PhasesRose),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PhasesRose),
                modifier = Modifier.testTag("trends_log_past_period_button")
            ) {
                Text(text = "+ Log Past", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricHighlightsGrid(data: CycleTrendsDashboardData) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 2
    ) {
        // Metric 1: Average Cycle Length
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Avg Cycle Length",
            value = "${String.format(Locale.US, "%.1f", data.averageCycleLength)} days",
            subtitle = data.regularityDescription,
            badge = "Min ${data.minCycleLength}d · Max ${data.maxCycleLength}d",
            badgeColor = PhasesPlum,
            icon = "⏱️"
        )

        // Metric 2: Average Period Duration
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Avg Bleeding Flow",
            value = "${String.format(Locale.US, "%.1f", data.averagePeriodDuration)} days",
            subtitle = "Typical healthy range (3-7d)",
            badge = "Normal Duration",
            badgeColor = PhasesRose,
            icon = "🩸"
        )

        // Metric 3: Cycle Predictability Variance
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Variance (σ)",
            value = "±${String.format(Locale.US, "%.1f", data.standardDeviationDays)} days",
            subtitle = if (data.standardDeviationDays <= 2.5) "Predictable cycle" else "Normal variation",
            badge = "Prediction Model",
            badgeColor = PhasesPeach,
            icon = "🔮"
        )

        // Metric 4: Cycle Regularity Score
        MetricCard(
            modifier = Modifier.weight(1f),
            title = "Regularity Score",
            value = "${data.regularityScorePercent}%",
            subtitle = if (data.regularityScorePercent >= 80) "Rhythm is stable" else "Moderate variability",
            badge = if (data.regularityScorePercent >= 80) "Optimal Rhythm" else "Variable",
            badgeColor = PhasesSage,
            icon = "✨"
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    badge: String,
    badgeColor: Color,
    icon: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = icon, fontSize = 16.sp)
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = badgeColor.copy(alpha = 0.12f),
                border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.3f)),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CycleLengthTrendChartCard(
    data: CycleTrendsDashboardData,
    selectedCycleIndex: Int?,
    onSelectCycle: (Int) -> Unit
) {
    val density = LocalDensity.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary

    val selectedPoint = data.cyclePoints.find { it.cycleIndex == selectedCycleIndex }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cycle_length_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Cycle Length History & Target Band",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = onSurfaceColor
                    )
                    Text(
                        text = "Tap a bar to inspect specific cycle interval details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegendDot(color = PhasesRose, label = "Cycle")
                    LegendDot(color = PhasesPlum, label = "Bleeding")
                }
            }

            // Canvas Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                val cyclePoints = data.cyclePoints
                val avgLength = data.averageCycleLength.toFloat()

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(cyclePoints) {
                            detectTapGestures { tapOffset ->
                                val count = cyclePoints.size
                                if (count > 0) {
                                    val leftPadding = 36f
                                    val rightPadding = 16f
                                    val chartWidth = size.width - leftPadding - rightPadding
                                    val slotWidth = chartWidth / count
                                    val relativeX = tapOffset.x - leftPadding
                                    if (relativeX >= 0) {
                                        val tappedIndex = (relativeX / slotWidth).toInt().coerceIn(0, count - 1)
                                        onSelectCycle(cyclePoints[tappedIndex].cycleIndex)
                                    }
                                }
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height

                    val leftPadding = 40.dp.toPx()
                    val rightPadding = 16.dp.toPx()
                    val topPadding = 24.dp.toPx()
                    val bottomPadding = 36.dp.toPx()

                    val chartWidth = width - leftPadding - rightPadding
                    val chartHeight = height - topPadding - bottomPadding

                    val minY = 18f
                    val maxY = 42f

                    fun yForDays(days: Float): Float {
                        val fraction = (days - minY) / (maxY - minY)
                        return (topPadding + chartHeight * (1f - fraction)).coerceIn(topPadding, topPadding + chartHeight)
                    }

                    // 1. Draw Normal Reference Band (24d to 35d)
                    val y24 = yForDays(24f)
                    val y35 = yForDays(35f)
                    drawRect(
                        color = PhasesPlum.copy(alpha = 0.05f),
                        topLeft = Offset(leftPadding, y35),
                        size = Size(chartWidth, y24 - y35)
                    )

                    // 2. Grid Lines (20d, 25d, 30d, 35d, 40d)
                    val gridValues = listOf(20f, 25f, 30f, 35f, 40f)
                    val gridPaint = android.graphics.Paint().apply {
                        color = outlineVariant.toArgb()
                        textSize = 10.sp.toPx()
                        isAntiAlias = true
                    }

                    gridValues.forEach { gVal ->
                        val y = yForDays(gVal)
                        drawLine(
                            color = outlineVariant.copy(alpha = 0.6f),
                            start = Offset(leftPadding, y),
                            end = Offset(width - rightPadding, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            "${gVal.toInt()}d",
                            8.dp.toPx(),
                            y + 4.dp.toPx(),
                            gridPaint
                        )
                    }

                    // 3. Average Cycle Reference Line
                    val avgY = yForDays(avgLength)
                    drawLine(
                        color = primaryColor.copy(alpha = 0.85f),
                        start = Offset(leftPadding, avgY),
                        end = Offset(width - rightPadding, avgY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )

                    // 4. Draw Bars for each cycle
                    val count = cyclePoints.size
                    val slotWidth = chartWidth / count.coerceAtLeast(1)
                    val barWidth = (slotWidth * 0.48f).coerceIn(16.dp.toPx(), 44.dp.toPx())

                    val labelPaint = android.graphics.Paint().apply {
                        color = onSurfaceColor.toArgb()
                        textSize = 11.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }

                    val datePaint = android.graphics.Paint().apply {
                        color = outlineVariant.toArgb()
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }

                    cyclePoints.forEachIndexed { index, point ->
                        val centerX = leftPadding + slotWidth * index + slotWidth / 2f
                        val isSelected = point.cycleIndex == selectedCycleIndex

                        val barHeightDays = point.cycleLengthDays.toFloat()
                        val topY = yForDays(barHeightDays)
                        val bottomY = topPadding + chartHeight

                        val barBrush = if (isSelected) {
                            Brush.verticalGradient(
                                listOf(PhasesRose, PhasesPlum)
                            )
                        } else {
                            Brush.verticalGradient(
                                listOf(
                                    PhasesRose.copy(alpha = 0.85f),
                                    PhasesPlum.copy(alpha = 0.6f)
                                )
                            )
                        }

                        // Cycle Bar
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(centerX - barWidth / 2f, topY),
                            size = Size(barWidth, bottomY - topY),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )

                        // Highlight border if selected
                        if (isSelected) {
                            drawRoundRect(
                                color = onSurfaceColor,
                                topLeft = Offset(centerX - barWidth / 2f - 2.dp.toPx(), topY - 2.dp.toPx()),
                                size = Size(barWidth + 4.dp.toPx(), bottomY - topY + 4.dp.toPx()),
                                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Bleeding duration inner indicator
                        val periodDurationDays = point.periodDurationDays.toFloat()
                        val periodIndicatorHeight = (chartHeight * (periodDurationDays / (maxY - minY))).coerceAtLeast(8.dp.toPx())
                        val periodTopY = bottomY - periodIndicatorHeight

                        drawRoundRect(
                            color = PhasesRose.copy(alpha = 0.95f),
                            topLeft = Offset(centerX - (barWidth * 0.7f) / 2f, periodTopY),
                            size = Size(barWidth * 0.7f, periodIndicatorHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Top value text (e.g. "28d")
                        drawContext.canvas.nativeCanvas.drawText(
                            "${point.cycleLengthDays}d",
                            centerX,
                            topY - 6.dp.toPx(),
                            labelPaint
                        )

                        // Bottom date label (e.g. "Aug 06")
                        drawContext.canvas.nativeCanvas.drawText(
                            point.startDateFormatted,
                            centerX,
                            bottomY + 18.dp.toPx(),
                            datePaint
                        )
                    }
                }
            }

            // Selected Cycle Floating Inspector Card
            AnimatedVisibility(
                visible = selectedPoint != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                if (selectedPoint != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PhasesRose.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PhasesRose.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Cycle #${selectedPoint.cycleIndex} (${selectedPoint.startDateFormatted} – ${selectedPoint.endDateFormatted})",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Cycle Length: ${selectedPoint.cycleLengthDays} days · Bleeding: ${selectedPoint.periodDurationDays} days",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = if (selectedPoint.isVariationNormal) PhasesSage.copy(alpha = 0.2f) else PhasesPeach.copy(alpha = 0.2f),
                                border = BorderStroke(
                                    0.5.dp,
                                    if (selectedPoint.isVariationNormal) PhasesSage else PhasesPeach
                                )
                            ) {
                                Text(
                                    text = selectedPoint.statusText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (selectedPoint.isVariationNormal) PhasesSage else PhasesPeach,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SymptomPatternsCard(
    symptoms: List<SymptomTrendItem>,
    onLogSymptomToday: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("symptom_patterns_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Symptom Patterns & Cycle Recurrence",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Frequency and peak hormonal phases over the last 6 months",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onLogSymptomToday,
                    modifier = Modifier.testTag("add_symptom_shortcut_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Log Symptom",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (symptoms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No symptoms logged yet. Tap '+' to begin tracking!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    symptoms.forEach { item ->
                        SymptomPatternRow(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun SymptomPatternRow(item: SymptomTrendItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = item.emoji, fontSize = 20.sp)
                Column {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.totalOccurrences} days · Present in ${item.cyclesPresentCount} of ${item.totalCyclesCount} cycles (${item.cyclePrevalencePercent}%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Peak Phase Pill
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = PhasesPlum.copy(alpha = 0.12f),
                border = BorderStroke(0.5.dp, PhasesPlum.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = item.peakPhaseEmoji, fontSize = 12.sp)
                    Text(
                        text = "Peaks in ${item.peakPhase}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = PhasesPlum
                    )
                }
            }
        }

        // Animated Proportional Bar
        val animatedProgress by animateFloatAsState(
            targetValue = item.frequencyScore,
            animationSpec = tween(durationMillis = 600),
            label = "symptom_progress"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(8.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(PhasesRose, PhasesPlum)
                        )
                    )
            )
        }
    }
}

@Composable
private fun PhaseSymptomDistributionCard(distribution: PhaseDistributionData) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Symptom Clustering by Cycle Phase",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Percentage breakdown of all recorded symptoms across hormonal phases",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Multi-segment Phase Distribution Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(100.dp))
            ) {
                if (distribution.menstrualPercent > 0) {
                    Box(
                        modifier = Modifier
                            .weight(distribution.menstrualPercent.toFloat())
                            .height(14.dp)
                            .background(PhasesRose)
                    )
                }
                if (distribution.follicularPercent > 0) {
                    Box(
                        modifier = Modifier
                            .weight(distribution.follicularPercent.toFloat())
                            .height(14.dp)
                            .background(PhasesSage)
                    )
                }
                if (distribution.ovulationPercent > 0) {
                    Box(
                        modifier = Modifier
                            .weight(distribution.ovulationPercent.toFloat())
                            .height(14.dp)
                            .background(PhasesLavender)
                    )
                }
                if (distribution.lutealPercent > 0) {
                    Box(
                        modifier = Modifier
                            .weight(distribution.lutealPercent.toFloat())
                            .height(14.dp)
                            .background(PhasesPlum)
                    )
                }
            }

            // Phase Legend Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhaseLegendCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🌸",
                    name = "Menstrual",
                    percent = distribution.menstrualPercent,
                    color = PhasesRose
                )
                PhaseLegendCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🌱",
                    name = "Follicular",
                    percent = distribution.follicularPercent,
                    color = PhasesSage
                )
                PhaseLegendCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🌿",
                    name = "Ovulation",
                    percent = distribution.ovulationPercent,
                    color = PhasesLavender
                )
                PhaseLegendCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🌙",
                    name = "Luteal/PMS",
                    percent = distribution.lutealPercent,
                    color = PhasesPlum
                )
            }
        }
    }
}

@Composable
private fun PhaseLegendCard(
    emoji: String,
    name: String,
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = emoji, fontSize = 16.sp)
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MonthlyRhythmCard(buckets: List<com.example.model.MonthTrendBucket>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    text = "Month-by-Month Rhythm",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Cycle length history across the last 6 months",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                buckets.forEach { b ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = b.monthYearLabel,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Cycle duration tag
                            Text(
                                text = if (b.cycleLengthDays != null) "${b.cycleLengthDays}d" else "—",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = PhasesRose
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlgorithmicInsightsCard(insights: List<AlgorithmicTrendInsight>) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🧠", fontSize = 20.sp)
                Column {
                    Text(
                        text = "Algorithmic Insights & Observations",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Data-driven clinical patterns from your logged history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                insights.forEach { ins ->
                    InsightItemRow(insight = ins)
                }
            }
        }
    }
}

@Composable
private fun InsightItemRow(insight: AlgorithmicTrendInsight) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = insight.emoji, fontSize = 22.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = insight.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardActionFooter(
    onLogPastPeriod: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onLogPastPeriod,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PhasesRose),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("dashboard_log_past_period_button")
        ) {
            Text(
                text = "📋 Log Past Period Dates for Prediction",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}
