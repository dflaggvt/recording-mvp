package com.memorystream.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.api.CloudApi
import com.memorystream.ui.components.GlassCard
import com.memorystream.ui.components.WaveformVisualization
import com.memorystream.ui.theme.CalmColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val placeColors = mapOf(
    "Home" to Color(0xFF6BC4B8),
    "Work" to Color(0xFF7B8FD4)
)
private val defaultPlaceColor = Color(0xFFD4A574)
private val unknownPlaceColor = Color(0xFF6A6480)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayReviewScreen(
    onBack: () -> Unit = {},
    onNavigateToSoundscape: (Long) -> Unit = {},
    dayTimestamp: Long = System.currentTimeMillis(),
    viewModel: DayReviewViewModel = hiltViewModel()
) {
    LaunchedEffect(dayTimestamp) {
        viewModel.loadDay(dayTimestamp)
    }
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.selectedChunk) {
        showSheet = uiState.selectedChunk != null
    }

    if (showSheet) {
        val chunk = uiState.selectedChunk
        if (chunk != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showSheet = false
                    viewModel.clearSelection()
                },
                sheetState = sheetState,
                containerColor = CalmColors.GradientMid
            ) {
                TranscriptSheet(
                    chunk = chunk,
                    utterances = uiState.selectedChunkUtterances,
                    speakers = uiState.speakers,
                    isPlaying = playbackState.isPlaying && playbackState.chunkId == chunk.id,
                    positionMs = playbackState.currentPositionMs,
                    durationMs = playbackState.durationMs,
                    onPlayPause = {
                        if (playbackState.isPlaying && playbackState.chunkId == chunk.id) {
                            viewModel.playbackManager.pause()
                        } else {
                            viewModel.playChunkAudio(chunk)
                        }
                    },
                    onSeek = { viewModel.playbackManager.seekTo(it) },
                    timeFormat = timeFormat
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White.copy(alpha = 0.60f)
                )
            }
            Text(
                text = "Your day, remembered.",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onNavigateToSoundscape(dayTimestamp) },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = "Soundscape map",
                    tint = CalmColors.Periwinkle.copy(alpha = 0.60f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!uiState.hasData) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    WaveformVisualization(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(32.dp),
                        barCount = 20,
                        barColor = CalmColors.Lavender.copy(alpha = 0.08f),
                        barHighlightColor = CalmColors.Lavender.copy(alpha = 0.14f),
                        animate = false,
                        seed = 77L
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No recordings yet today.\nI'm listening.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.35f),
                        lineHeight = 28.sp
                    )
                }
            }
        } else {
            LazyColumn {
                item {
                    StatsRow(uiState)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    NarrativeCard(
                        narrative = uiState.narrative,
                        isGenerating = uiState.isGeneratingNarrative
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                item {
                    Text(
                        text = "Timeline",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TimelineStrip(
                        chunks = uiState.chunks,
                        onChunkTapped = { viewModel.selectChunk(it) },
                        timeFormat = timeFormat
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (uiState.commitments.isNotEmpty()) {
                    item {
                        Text(
                            text = "Follow through",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(uiState.commitments) { commitment ->
                        CommitmentCard(
                            commitment = commitment,
                            onComplete = { viewModel.completeCommitment(commitment.id) }
                        )
                    }
                }

                if (uiState.inconsistencies.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Inconsistencies",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(uiState.inconsistencies) { inconsistency ->
                        InconsistencyCard(insight = inconsistency)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(uiState: DayReviewUiState) {
    val hours = uiState.totalDurationMs / 3_600_000
    val minutes = (uiState.totalDurationMs % 3_600_000) / 60_000
    val durationText = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlassStatChip("$durationText recorded")
        if (uiState.placeCount > 0) GlassStatChip("${uiState.placeCount} place${if (uiState.placeCount != 1) "s" else ""}")
        GlassStatChip("${uiState.chunks.size} conversation${if (uiState.chunks.size != 1) "s" else ""}")
    }
}

@Composable
private fun GlassStatChip(text: String) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.06f), shape)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.50f)
        )
    }
}

@Composable
private fun NarrativeCard(narrative: String?, isGenerating: Boolean) {
    GlassCard(
        cornerRadius = 24.dp,
        fillAlpha = 0.10f,
        borderAlpha = 0.15f
    ) {
        Column {
            if (isGenerating) {
                NarrativeShimmer()
            } else {
                AnimatedVisibility(visible = narrative != null, enter = fadeIn(tween(500))) {
                    Text(
                        text = narrative ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.80f),
                        lineHeight = 26.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun NarrativeShimmer() {
    val transition = rememberInfiniteTransition(label = "narrativeShimmer")
    val alpha by transition.animateFloat(
        0.04f, 0.12f,
        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        "shimAlpha"
    )
    Column {
        repeat(4) { i ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (i == 3) 0.5f else 1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CalmColors.Periwinkle.copy(alpha = alpha))
            )
            if (i < 3) Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TimelineStrip(
    chunks: List<CloudApi.ChunkSummary>,
    onChunkTapped: (CloudApi.ChunkSummary) -> Unit,
    timeFormat: SimpleDateFormat
) {
    if (chunks.isEmpty()) return

    val firstTime = chunks.first().start_timestamp
    val lastTime = chunks.last().end_timestamp
    val totalSpan = maxOf(1L, lastTime - firstTime)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.04f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            chunks.forEachIndexed { index, chunk ->
                val chunkDuration = maxOf(1L, chunk.end_timestamp - chunk.start_timestamp)
                val weight = chunkDuration.toFloat() / totalSpan

                if (index > 0) {
                    val gap = chunk.start_timestamp - chunks[index - 1].end_timestamp
                    if (gap > 0) {
                        val gapWeight = gap.toFloat() / totalSpan
                        Spacer(modifier = Modifier.weight(gapWeight))
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
                        .height(36.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(color.copy(alpha = 0.55f))
                        .clickable { onChunkTapped(chunk) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = timeFormat.format(Date(firstTime)),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.30f)
            )
            Text(
                text = timeFormat.format(Date(lastTime)),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.30f)
            )
        }
    }
}

@Composable
private fun TranscriptSheet(
    chunk: CloudApi.ChunkSummary,
    utterances: List<CloudApi.UtteranceResult>,
    speakers: Map<String, CloudApi.SpeakerResult>,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    timeFormat: SimpleDateFormat
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        val place = chunk.place_name ?: "Recording"
        val startTime = timeFormat.format(Date(chunk.start_timestamp))
        val endTime = timeFormat.format(Date(chunk.end_timestamp))

        Text(
            text = place,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.90f)
        )
        Text(
            text = "$startTime - $endTime",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.40f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = CalmColors.Periwinkle,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (durationMs > 0) {
                Slider(
                    value = positionMs.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..durationMs.toFloat(),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = CalmColors.Periwinkle,
                        activeTrackColor = CalmColors.Periwinkle,
                        inactiveTrackColor = CalmColors.Periwinkle.copy(alpha = 0.15f)
                    )
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.weight(1f),
                    color = CalmColors.Periwinkle.copy(alpha = 0.3f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (utterances.isEmpty()) {
            Text(
                text = chunk.transcript ?: "No transcript available.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.padding(bottom = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(300.dp)
            ) {
                items(utterances) { utterance ->
                    val speaker = utterance.speaker_id?.let { speakers[it] }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        if (speaker != null) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(speaker.color))
                                    .align(Alignment.Top)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = speaker.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color(speaker.color)
                                )
                                Text(
                                    text = utterance.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.70f)
                                )
                            }
                        } else {
                            Text(
                                text = utterance.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.60f),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CommitmentCard(
    commitment: CloudApi.InsightResult,
    onComplete: () -> Unit
) {
    GlassCard(
        modifier = Modifier.padding(vertical = 4.dp),
        cornerRadius = 16.dp,
        fillAlpha = 0.06f,
        borderAlpha = 0.08f
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            IconButton(onClick = onComplete, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Complete",
                    tint = CalmColors.Periwinkle.copy(alpha = 0.50f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commitment.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = commitment.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.50f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun InconsistencyCard(insight: CloudApi.InsightResult) {
    GlassCard(
        modifier = Modifier.padding(vertical = 4.dp),
        cornerRadius = 16.dp,
        fillAlpha = 0.06f,
        borderAlpha = 0.08f
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CalmColors.MutedCoral.copy(alpha = 0.7f))
                    .align(Alignment.Top)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = CalmColors.MutedCoral.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = insight.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }
    }
}
