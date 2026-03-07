package com.memorystream.ui.player

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.api.CloudApi
import com.memorystream.ui.components.WaveformVisualization
import com.memorystream.ui.theme.CalmColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FullPlayerScreen(
    onBack: () -> Unit = {},
    viewModel: FullPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()
    val dateFormat = SimpleDateFormat("EEEE, MMM d 'at' h:mm a", Locale.getDefault())
    val transcriptListState = rememberLazyListState()

    LaunchedEffect(uiState.currentUtteranceIndex) {
        if (uiState.currentUtteranceIndex >= 0) {
            transcriptListState.animateScrollToItem(uiState.currentUtteranceIndex)
        }
    }

    val chunk = uiState.chunk
    val place = chunk?.place_name ?: playbackState.placeName ?: "Recording"
    val timestamp = chunk?.start_timestamp ?: playbackState.chunkStartTimestamp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White.copy(alpha = 0.50f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        WaveformVisualization(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            barCount = 70,
            barColor = CalmColors.Periwinkle.copy(alpha = 0.20f),
            barHighlightColor = CalmColors.Lavender.copy(alpha = 0.50f),
            animate = playbackState.isPlaying,
            seed = timestamp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = place,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.90f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateFormat.format(Date(timestamp)),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.35f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val posSeconds = playbackState.currentPositionMs / 1000
        val durSeconds = playbackState.durationMs / 1000

        if (playbackState.durationMs > 0) {
            Slider(
                value = playbackState.currentPositionMs.toFloat(),
                onValueChange = { viewModel.playbackManager.seekTo(it.toInt()) },
                valueRange = 0f..playbackState.durationMs.toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = CalmColors.Periwinkle,
                    activeTrackColor = CalmColors.Periwinkle,
                    inactiveTrackColor = CalmColors.Periwinkle.copy(alpha = 0.15f)
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(posSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.35f)
            )
            Text(
                text = formatTime(durSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.35f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.playPrevious() },
                enabled = uiState.hasPrevious,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = if (uiState.hasPrevious) 0.15f else 0.05f)),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(28.dp),
                    tint = if (uiState.hasPrevious) Color.White.copy(alpha = 0.70f)
                           else Color.White.copy(alpha = 0.15f)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            IconButton(
                onClick = { viewModel.playbackManager.togglePlayPause() },
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            ) {
                Icon(
                    if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = CalmColors.GradientBottom,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            IconButton(
                onClick = { viewModel.playNext() },
                enabled = uiState.hasNext,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = if (uiState.hasNext) 0.15f else 0.05f)),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(28.dp),
                    tint = if (uiState.hasNext) Color.White.copy(alpha = 0.70f)
                           else Color.White.copy(alpha = 0.15f)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        SpeedSelector(
            currentSpeed = playbackState.speed,
            onSpeedChange = { viewModel.setSpeed(it) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Transcript",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.35f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.utterances.isEmpty() && chunk?.transcript != null) {
            Text(
                text = chunk.transcript,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.60f),
                lineHeight = 24.sp
            )
        } else {
            LazyColumn(
                state = transcriptListState,
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(uiState.utterances) { index, utterance ->
                    TranscriptRow(
                        utterance = utterance,
                        speaker = utterance.speaker_id?.let { uiState.speakers[it] },
                        isActive = index == uiState.currentUtteranceIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedSelector(currentSpeed: Float, onSpeedChange: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        speeds.forEach { speed ->
            val label = if (speed == 1.0f) "1x" else "${speed}x"
            val selected = kotlin.math.abs(currentSpeed - speed) < 0.01f
            val shape = RoundedCornerShape(16.dp)
            val bgAlpha = if (selected) 0.18f else 0.06f
            val borderAlpha = if (selected) 0.25f else 0.10f

            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = bgAlpha))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = borderAlpha)), shape)
                    .clickable { onSpeedChange(speed) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = if (selected) Color.White.copy(alpha = 0.90f)
                            else Color.White.copy(alpha = 0.45f)
                )
            }
        }
    }
}

@Composable
private fun TranscriptRow(
    utterance: CloudApi.UtteranceResult,
    speaker: CloudApi.SpeakerResult?,
    isActive: Boolean
) {
    val bgAlpha = if (isActive) 0.10f else 0f
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CalmColors.Periwinkle.copy(alpha = bgAlpha), shape)
            .padding(vertical = 6.dp, horizontal = 10.dp)
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
                    color = Color.White.copy(alpha = if (isActive) 0.85f else 0.50f),
                    lineHeight = 20.sp
                )
            }
        } else {
            Text(
                text = utterance.text,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = if (isActive) 0.85f else 0.50f),
                modifier = Modifier.weight(1f),
                lineHeight = 20.sp
            )
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%d:%02d".format(min, sec)
}
