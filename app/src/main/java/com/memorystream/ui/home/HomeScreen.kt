package com.memorystream.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.UsbOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.data.db.MemoryChunkEntity
import com.memorystream.data.model.ChunkStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val recentChunks by viewModel.recentChunks.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MemoryStream",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (uiState.hasUsbMic) Icons.Default.Usb else Icons.Default.UsbOff,
                contentDescription = "USB mic status",
                tint = if (uiState.hasUsbMic) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.hasUsbMic) "USB mic connected" else "No USB mic detected",
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.hasUsbMic) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        val buttonColor by animateColorAsState(
            targetValue = if (uiState.isRecording) MaterialTheme.colorScheme.error
                          else MaterialTheme.colorScheme.primary,
            label = "recordButtonColor"
        )

        Button(
            onClick = { viewModel.toggleRecording() },
            modifier = Modifier.size(100.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = if (uiState.isRecording) Icons.Default.Stop
                              else Icons.Default.Mic,
                contentDescription = if (uiState.isRecording) "Stop recording"
                                     else "Start recording",
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (uiState.isRecording) "Recording..." else "Tap to start",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        // Live transcript area
        if (uiState.isRecording && uiState.liveTranscript.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .height(100.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = uiState.liveTranscript,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatCard("Chunks", "${uiState.chunkCount}")
            StatCard("Total Time", formatDuration(uiState.totalDurationMs))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Recent Chunks",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(recentChunks) { chunk ->
                ChunkListItem(chunk)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ChunkListItem(chunk: MemoryChunkEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.FiberManualRecord,
                contentDescription = null,
                tint = statusColor(chunk.status),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatTimestamp(chunk.startTimestamp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (!chunk.summary.isNullOrBlank()) {
                    Text(
                        text = chunk.summary.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (!chunk.commitments.isNullOrBlank()) {
                    Text(
                        text = "Has commitments",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = chunk.status.name.lowercase().replace('_', ' '),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor(chunk.status)
            )
        }
    }
}

@Composable
private fun statusColor(status: ChunkStatus): Color = when (status) {
    ChunkStatus.RECORDING -> Color(0xFFE53935)
    ChunkStatus.PENDING_TRANSCRIPTION -> Color(0xFFFFA726)
    ChunkStatus.TRANSCRIBING -> Color(0xFF42A5F5)
    ChunkStatus.TRANSCRIBED -> Color(0xFF66BB6A)
    ChunkStatus.EMBEDDING -> Color(0xFF42A5F5)
    ChunkStatus.EMBEDDED -> Color(0xFF2E7D32)
    ChunkStatus.ERROR -> Color(0xFFD32F2F)
}

private fun formatDuration(ms: Long): String {
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    return "${hours}h ${minutes}m"
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return if (diff < 24 * 60 * 60 * 1000) {
        timeFormat.format(Date(timestamp))
    } else {
        dateFormat.format(Date(timestamp))
    }
}
