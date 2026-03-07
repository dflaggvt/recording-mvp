package com.memorystream.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memorystream.audio.PlaybackState
import com.memorystream.ui.theme.CalmColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (playbackState.currentFilePath == null) return

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val time = timeFormat.format(Date(playbackState.chunkStartTimestamp))
    val place = playbackState.placeName ?: "Recording"
    val progress = if (playbackState.durationMs > 0) {
        playbackState.currentPositionMs.toFloat() / playbackState.durationMs
    } else 0f

    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), shape)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape
            )
            .clickable { onTap() }
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = CalmColors.Periwinkle,
            trackColor = CalmColors.Periwinkle.copy(alpha = 0.08f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$place \u00B7 $time",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val posSeconds = playbackState.currentPositionMs / 1000
                val durSeconds = playbackState.durationMs / 1000
                Text(
                    text = "${formatTime(posSeconds)} / ${formatTime(durSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CalmColors.Periwinkle.copy(alpha = 0.15f))
            ) {
                Icon(
                    if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = CalmColors.Periwinkle,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onStop,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Stop",
                    tint = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun formatTime(totalSeconds: Int): String {
    val min = totalSeconds / 60
    val sec = totalSeconds % 60
    return "%d:%02d".format(min, sec)
}
