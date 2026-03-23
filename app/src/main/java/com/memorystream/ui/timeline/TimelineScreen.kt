package com.memorystream.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.api.CloudApi
import com.memorystream.ui.components.GlassCard
import com.memorystream.ui.components.WaveformVisualization
import com.memorystream.ui.theme.CalmColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val placeColors = mapOf(
    "Home" to Color(0xFF6BC4B8),
    "Work" to Color(0xFF7B8FD4)
)
private val defaultPlaceColor = Color(0xFFD4A574)
private val unknownPlaceColor = Color(0xFF6A6480)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = hiltViewModel(),
    onNavigateToDayReview: (Long) -> Unit = {},
    onNavigateToSoundscape: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= uiState.days.size - 3 && uiState.hasMore) {
                    viewModel.loadMore()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Timeline",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White.copy(alpha = 0.90f)
            )
            IconButton(
                onClick = { onNavigateToSoundscape(System.currentTimeMillis()) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = "Soundscape map",
                    tint = CalmColors.Periwinkle.copy(alpha = 0.70f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (uiState.days.isEmpty() && !uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WaveformVisualization(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(40.dp),
                        barCount = 25,
                        barColor = CalmColors.Lavender.copy(alpha = 0.10f),
                        barHighlightColor = CalmColors.Lavender.copy(alpha = 0.18f),
                        animate = false,
                        seed = 99L
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No recordings yet.",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.30f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your audio memories will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.35f)
                    )
                }
            }
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uiState.discovery != null) {
                    item(key = "discovery") {
                        DiscoveryCard(
                            discovery = uiState.discovery!!,
                            timeFormat = timeFormat,
                            onPlay = { viewModel.playChunk(uiState.discovery!!.chunk) },
                            modifier = Modifier.animateItemPlacement(tween(300))
                        )
                    }
                }

                items(uiState.days, key = { it.dayTimestamp }) { day ->
                    DayCardView(
                        day = day,
                        timeFormat = timeFormat,
                        onTap = { onNavigateToDayReview(day.dayTimestamp) },
                        modifier = Modifier.animateItemPlacement(tween(300))
                    )
                }

                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = CalmColors.Periwinkle.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryCard(
    discovery: DiscoveryMemory,
    timeFormat: SimpleDateFormat,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chunk = discovery.chunk
    val place = chunk.place_name ?: "Recording"
    val time = timeFormat.format(Date(chunk.start_timestamp))
    val preview = chunk.transcript?.take(120) ?: ""

    GlassCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        fillAlpha = 0.10f,
        borderAlpha = 0.15f,
        shimmer = true
    ) {
        Column {
            WaveformVisualization(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                barCount = 50,
                barColor = CalmColors.Periwinkle.copy(alpha = 0.20f),
                barHighlightColor = CalmColors.Periwinkle.copy(alpha = 0.40f),
                animate = false,
                seed = chunk.start_timestamp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = discovery.label,
                style = MaterialTheme.typography.labelMedium,
                color = CalmColors.Lavender.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "$place \u2014 $time",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\u201C$preview...\u201D",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.50f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CalmColors.Periwinkle.copy(alpha = 0.15f))
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = CalmColors.Periwinkle,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCardView(
    day: DayCard,
    timeFormat: SimpleDateFormat,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hours = day.totalDurationMs / 3_600_000
    val minutes = (day.totalDurationMs % 3_600_000) / 60_000
    val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L

    val dayLabel = when {
        day.dayTimestamp >= todayStart -> "Today"
        day.dayTimestamp >= yesterdayStart -> "Yesterday"
        else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(day.dayTimestamp))
    }

    val placeCount = day.places.size
    val summaryText = buildString {
        append("${day.chunkCount} conversation${if (day.chunkCount != 1) "s" else ""}")
        if (placeCount > 0) append(", $placeCount place${if (placeCount != 1) "s" else ""}")
    }

    GlassCard(
        modifier = modifier.clickable { onTap() },
        cornerRadius = 20.dp,
        fillAlpha = 0.07f,
        borderAlpha = 0.10f
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.90f)
                )
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.40f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (day.chunks.isNotEmpty()) {
                PlaceStrip(chunks = day.chunks, timeFormat = timeFormat)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
                text = if (day.chunkCount == 0) "No recordings" else summaryText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (day.chunkCount == 0) 0.25f else 0.40f)
            )
        }
    }
}

@Composable
private fun PlaceStrip(chunks: List<CloudApi.ChunkSummary>, timeFormat: SimpleDateFormat) {
    if (chunks.isEmpty()) return

    val firstTime = chunks.first().start_timestamp
    val lastTime = chunks.last().end_timestamp
    val totalSpan = maxOf(1L, lastTime - firstTime)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.04f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            chunks.forEachIndexed { index, chunk ->
                val chunkDuration = maxOf(1L, chunk.end_timestamp - chunk.start_timestamp)
                val weight = chunkDuration.toFloat() / totalSpan

                if (index > 0) {
                    val gap = chunk.start_timestamp - chunks[index - 1].end_timestamp
                    if (gap > 0) {
                        Spacer(modifier = Modifier.weight(gap.toFloat() / totalSpan))
                    }
                }

                val color = when {
                    chunk.place_name == "Home" -> placeColors["Home"]!!
                    chunk.place_name == "Work" -> placeColors["Work"]!!
                    chunk.place_name != null -> defaultPlaceColor
                    else -> unknownPlaceColor
                }

                Box(
                    modifier = Modifier
                        .weight(weight)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.50f))
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = timeFormat.format(Date(firstTime)),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.30f)
            )
            Text(
                text = timeFormat.format(Date(lastTime)),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.30f)
            )
        }
    }
}
