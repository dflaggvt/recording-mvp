package com.memorystream.ui.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.ui.components.GlassCard
import com.memorystream.ui.components.WaveformVisualization
import com.memorystream.ui.theme.CalmColors

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var showSummary by remember { mutableStateOf(false) }
    var showInsights by remember { mutableStateOf(false) }
    var showMood by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100); showSummary = true
        delay(150); showInsights = true
        delay(150); showMood = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White.copy(alpha = 0.90f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = showSummary,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
        ) {
            DailySummaryCard(summary = uiState.dailySummary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = showInsights,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
        ) {
            Column {
                Text(
                    text = "Personal AI Insights",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.90f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InsightMiniCard(
                        title = "Most Mentioned Books",
                        items = uiState.topBooks,
                        placeholder = "Klara and the Sun",
                        accentColor = CalmColors.Lavender,
                        modifier = Modifier.weight(1f)
                    )
                    InsightMiniCard(
                        title = "People Interacted With",
                        items = uiState.topPeople,
                        placeholder = "Sarah, John D.",
                        accentColor = CalmColors.SoftTeal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedVisibility(
            visible = showMood,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 4 }
        ) {
            MoodTrackerCard(moodData = uiState.moodData)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun DailySummaryCard(summary: String?) {
    GlassCard(shimmer = true) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daily Summary",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.90f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View details",
                    tint = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            WaveformVisualization(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                barCount = 60,
                barColor = CalmColors.Periwinkle.copy(alpha = 0.4f),
                barHighlightColor = CalmColors.Periwinkle.copy(alpha = 0.7f),
                animate = true,
                seed = 42L
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = summary ?: "Key phrases detected throughout the day",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.40f)
            )
        }
    }
}

@Composable
private fun InsightMiniCard(
    title: String,
    items: List<String>,
    placeholder: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        fillAlpha = 0.06f,
        borderAlpha = 0.10f
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.90f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            val displayText = if (items.isNotEmpty()) {
                items.joinToString(", ")
            } else {
                placeholder
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = accentColor.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MoodTrackerCard(moodData: List<Float>) {
    GlassCard {
        Column {
            Text(
                text = "Mood Tracker",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.90f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tonal analysis of conversations",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.40f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            MoodChart(
                moodData = moodData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        }
    }
}

@Composable
private fun MoodChart(
    moodData: List<Float>,
    modifier: Modifier = Modifier
) {
    val emojiLabels = listOf("\uD83D\uDE22", "\uD83D\uDE10", "\uD83D\uDE42", "\uD83D\uDE04")
    val timeLabels = listOf("9am", "12pm", "3pm", "6pm", "9pm")
    val lineColor = CalmColors.Periwinkle
    val gridColor = Color.White.copy(alpha = 0.08f)
    val textColor = Color.White.copy(alpha = 0.40f)

    val chartData = moodData.ifEmpty {
        listOf(0.3f, 0.5f, 0.7f, 0.6f, 0.8f, 0.65f, 0.4f, 0.55f)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .height(140.dp)
                .width(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            emojiLabels.reversed().forEach { emoji ->
                Text(text = emoji, fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height
                val padding = 4.dp.toPx()

                for (i in 0..3) {
                    val y = chartHeight - (chartHeight * i / 3f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                if (chartData.size >= 2) {
                    val path = Path()
                    val stepX = (chartWidth - padding * 2) / (chartData.size - 1)

                    chartData.forEachIndexed { index, value ->
                        val x = padding + stepX * index
                        val y = chartHeight - (value * (chartHeight - padding * 2)) - padding

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            val prevX = padding + stepX * (index - 1)
                            val prevY = chartHeight - (chartData[index - 1] * (chartHeight - padding * 2)) - padding
                            val controlX = (prevX + x) / 2f
                            path.cubicTo(controlX, prevY, controlX, y, x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = lineColor.copy(alpha = 0.8f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    chartData.forEachIndexed { index, value ->
                        val x = padding + stepX * index
                        val y = chartHeight - (value * (chartHeight - padding * 2)) - padding
                        drawCircle(
                            color = lineColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                timeLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor
                    )
                }
            }
        }
    }
}
