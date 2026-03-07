package com.memorystream.ui.debug

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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.api.CloudApi
import com.memorystream.ui.components.GlassCard
import com.memorystream.ui.components.GradientBackground
import com.memorystream.ui.theme.CalmColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DebugScreen(
    onBack: () -> Unit = {},
    viewModel: DebugViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val dateTimeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (uiState.selectedChunk != null) viewModel.clearSelection()
                    else onBack()
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White.copy(alpha = 0.60f)
                    )
                }
                Text(
                    text = if (uiState.selectedChunk != null) "Chunk Detail" else "Developer Tools",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.90f)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (uiState.selectedChunk == null) {
                    IconButton(onClick = { viewModel.refreshAll() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White.copy(alpha = 0.50f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.selectedChunk != null) {
                val selectedChunk = uiState.selectedChunk!!
                ChunkDetailView(
                    info = selectedChunk,
                    dateTimeFormat = dateTimeFormat
                )
            } else {
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf("Pipeline", "System", "Costs")

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White.copy(alpha = 0.85f),
                    indicator = { tabPositions ->
                        SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = CalmColors.Periwinkle
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    color = if (selectedTab == index) Color.White.copy(alpha = 0.85f)
                                            else Color.White.copy(alpha = 0.40f)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> ChunkListView(
                        uiState = uiState,
                        timeFormat = timeFormat,
                        onChunkTap = { viewModel.selectChunk(it) }
                    )
                    1 -> SystemView(uiState.systemInfo)
                    2 -> CostAnalysisView(uiState.costEstimate)
                }
            }
        }
    }
}

// -- System Tab --

@Composable
private fun SystemView(info: SystemInfo) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(cornerRadius = 16.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recording", style = MaterialTheme.typography.labelMedium, color = CalmColors.Periwinkle)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (info.isRecording) CalmColors.SageGreen else CalmColors.MutedCoral)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (info.isRecording) "Active" else "Stopped",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (info.isRecording) CalmColors.SageGreen else CalmColors.MutedCoral
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    MetaRow("Sample Rate", "${info.sampleRate} Hz")
                    MetaRow("Channels", info.channels)
                    MetaRow("Encoding", info.encoding)
                    MetaRow("Bitrate", info.bitrate)
                }
            }
        }

        item {
            GlassCard(cornerRadius = 16.dp) {
                Column {
                    Text("Local Storage", style = MaterialTheme.typography.labelMedium, color = CalmColors.Periwinkle)
                    Spacer(modifier = Modifier.height(10.dp))
                    MetaRow("Pending Upload", "${info.pendingUploadFiles} files",
                        valueColor = if (info.pendingUploadFiles > 0) CalmColors.SoftAmber else null)
                    MetaRow("Audio on Device", "%.1f MB".format(info.totalAudioFilesMB))
                }
            }
        }

        item {
            val cloudUrl = com.memorystream.api.ApiConfig.cloudRunUrl
            val isConfigured = com.memorystream.api.ApiConfig.isCloudConfigured()
            GlassCard(cornerRadius = 16.dp) {
                Column {
                    Text("Cloud API", style = MaterialTheme.typography.labelMedium, color = CalmColors.Periwinkle)
                    Spacer(modifier = Modifier.height(10.dp))
                    MetaRow("URL", cloudUrl.ifBlank { "(not set)" },
                        valueColor = if (!isConfigured) CalmColors.MutedCoral else null)
                    MetaRow("Status", if (isConfigured) "Configured" else "Not configured",
                        valueColor = if (!isConfigured) CalmColors.MutedCoral else CalmColors.SageGreen)
                }
            }
        }
    }
}

// -- Pipeline Tab --

@Composable
private fun ChunkListView(
    uiState: DebugUiState,
    timeFormat: SimpleDateFormat,
    onChunkTap: (String) -> Unit
) {
    val lastRefreshFormat = remember { SimpleDateFormat("h:mm:ss a", Locale.getDefault()) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            StatsCard(uiState)
        }

        if (uiState.isLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = CalmColors.Periwinkle,
                    trackColor = CalmColors.Periwinkle.copy(alpha = 0.10f)
                )
            }
        }

        if (uiState.lastError != null) {
            item {
                Text(
                    text = "Error: ${uiState.lastError}",
                    style = MaterialTheme.typography.bodySmall,
                    color = CalmColors.MutedCoral
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Recent Chunks",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.40f)
                )
                if (uiState.lastRefreshed > 0) {
                    Text(
                        text = "Updated ${lastRefreshFormat.format(Date(uiState.lastRefreshed))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }

        if (uiState.chunks.isEmpty() && !uiState.isLoading) {
            item {
                Text(
                    text = "No chunks from cloud. Check that the API URL is set and chunks have been uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }

        items(uiState.chunks, key = { it.chunk.id }) { info ->
            ChunkRow(info, timeFormat, onTap = { onChunkTap(info.chunk.id) })
        }
    }
}

@Composable
private fun StatsCard(uiState: DebugUiState) {
    GlassCard(cornerRadius = 16.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem("Chunks", uiState.totalChunks.toString())
            StatItem("Insights", uiState.totalInsights.toString())
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = CalmColors.Periwinkle
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.40f)
        )
    }
}

@Composable
private fun ChunkRow(
    info: ChunkDebugInfo,
    timeFormat: SimpleDateFormat,
    onTap: () -> Unit
) {
    val chunk = info.chunk
    val time = timeFormat.format(Date(chunk.start_timestamp))
    val preview = chunk.transcript?.take(80) ?: "(no transcript)"
    val shape = RoundedCornerShape(16.dp)
    val speakerCount = info.utterances.mapNotNull { it.diarization_label }.distinct().size

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), shape)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape)
            .clickable { onTap() }
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$time${chunk.place_name?.let { " \u00B7 $it" } ?: ""}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.80f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (speakerCount > 0) {
                        Badge("$speakerCount spk", CalmColors.SoftTeal)
                    }
                    if (info.utterances.isNotEmpty()) {
                        Badge("${info.utterances.size} utt", CalmColors.Lavender)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.40f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = color
        )
    }
}

// -- Chunk Detail --

private val speakerColors = listOf(
    Color(0xFF5B9BD5), Color(0xFFD4726A), Color(0xFF4ECDC4),
    Color(0xFFD4A574), Color(0xFF8B9DC3), Color(0xFF8FA67A),
    Color(0xFFE091C0), Color(0xFFA0D995)
)

@Composable
private fun ChunkDetailView(
    info: ChunkDebugInfo,
    dateTimeFormat: SimpleDateFormat
) {
    val chunk = info.chunk

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(cornerRadius = 16.dp) {
                Column {
                    Text("Metadata", style = MaterialTheme.typography.labelMedium, color = CalmColors.Periwinkle)
                    Spacer(modifier = Modifier.height(8.dp))
                    MetaRow("Time", dateTimeFormat.format(Date(chunk.start_timestamp)))
                    MetaRow("Duration", "${(chunk.end_timestamp - chunk.start_timestamp) / 1000}s")
                    MetaRow("Place", chunk.place_name ?: "Unknown")
                    MetaRow("Status", chunk.status)
                    MetaRow("Transcript", "${chunk.transcript?.length ?: 0} chars")
                    MetaRow("Utterances", "${info.utterances.size}")
                    MetaRow("Speakers", "${info.utterances.mapNotNull { it.diarization_label }.distinct().size}")
                    MetaRow("Chunk ID", chunk.id.take(8) + "...")
                }
            }
        }

        if (info.utterances.isNotEmpty()) {
            item {
                GlassCard(cornerRadius = 16.dp) {
                    Column {
                        val distinctSpeakers = info.utterances.mapNotNull { it.diarization_label }.distinct().sorted()
                        Text(
                            "Diarization (${info.utterances.size} utterances, ${distinctSpeakers.size} speakers)",
                            style = MaterialTheme.typography.labelMedium,
                            color = CalmColors.Periwinkle
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        info.utterances.forEach { utt ->
                            UtteranceRow(utt, chunk.start_timestamp)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        item {
            GlassCard(cornerRadius = 16.dp) {
                Column {
                    Text("Transcript", style = MaterialTheme.typography.labelMedium, color = CalmColors.Periwinkle)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = chunk.transcript ?: "(empty)",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = Color.White.copy(alpha = 0.60f),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun UtteranceRow(
    utt: CloudApi.UtteranceResult,
    chunkStartTimestamp: Long
) {
    val offsetSec = (utt.timestamp - chunkStartTimestamp) / 1000f
    val endOffsetSec = utt.end_timestamp?.let { (it - chunkStartTimestamp) / 1000f }
    val label = utt.diarization_label
    val colorIndex = if (label != null) ((label % speakerColors.size) + speakerColors.size) % speakerColors.size else -1
    val color = if (colorIndex >= 0) speakerColors[colorIndex] else Color.White.copy(alpha = 0.30f)
    val speakerName = if (label != null) "Speaker $label" else "Unknown"

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = speakerName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (endOffsetSec != null)
                    "%.1fs-%.1fs".format(offsetSec, endOffsetSec)
                else
                    "%.1fs".format(offsetSec),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.30f)
            )
        }
        Text(
            text = utt.text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 14.dp, top = 2.dp)
        )
    }
}

// -- Shared Components --

@Composable
private fun MetaRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.40f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: Color.White.copy(alpha = 0.75f)
        )
    }
}

// -- Costs Tab --

@Composable
private fun CostAnalysisView(cost: CostEstimate) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GlassCard(cornerRadius = 16.dp, fillAlpha = 0.10f) {
                Column {
                    Text(
                        "Estimated Monthly Cost",
                        style = MaterialTheme.typography.labelMedium,
                        color = CalmColors.Periwinkle
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$%.2f/mo".format(cost.totalMonthlyCost),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.90f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Based on %.1f hrs/day recording, %.0f chunks/day".format(
                            cost.recordingHoursPerDay, cost.chunksPerDay
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.40f)
                    )
                }
            }
        }

        item {
            GlassCard(cornerRadius = 16.dp) {
                Column {
                    Text(
                        "Usage Stats",
                        style = MaterialTheme.typography.labelMedium,
                        color = CalmColors.Periwinkle
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MetaRow("Total chunks processed", "${cost.totalChunks}")
                    MetaRow("Total recording time", "%.0f min".format(cost.totalDurationMinutes))
                }
            }
        }
    }
}
