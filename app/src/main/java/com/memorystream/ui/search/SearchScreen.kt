package com.memorystream.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.api.CloudApi
import com.memorystream.service.RecordingService
import com.memorystream.ui.components.GlassCard
import com.memorystream.ui.components.GlassSearchBar
import com.memorystream.ui.components.WaveformVisualization
import com.memorystream.ui.theme.CalmColors
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isListening by RecordingService.isRecording.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // "Ask My Life..." search bar
            GlassSearchBar(
                value = uiState.query,
                onValueChange = { viewModel.updateQuery(it) },
                onSearch = { viewModel.search() },
                onClear = { viewModel.clearSearch() },
                placeholder = "Ask My Life..."
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Chapter timeline
            ChapterTimeline()

            Spacer(modifier = Modifier.height(16.dp))

            // Main content with animated transitions
            val contentState = when {
                uiState.isSearching -> "searching"
                uiState.results.isEmpty() && uiState.hasSearched -> "empty"
                uiState.results.isNotEmpty() -> "results"
                else -> "idle"
            }

            AnimatedContent(
                targetState = contentState,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 })
                        .togetherWith(fadeOut(tween(200)))
                },
                label = "search_content",
                modifier = Modifier.weight(1f)
            ) { state ->
                when (state) {
                    "searching" -> ShimmerPlaceholder()
                    "empty" -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "Nothing found in your recordings.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.30f),
                            modifier = Modifier.padding(top = 32.dp)
                        )
                    }
                    "results" -> SearchResultsView(uiState)
                    else -> IdleView(viewModel)
                }
            }
        }

        // Live waveform at bottom
        WaveformVisualization(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            barCount = 50,
            barColor = CalmColors.Periwinkle.copy(alpha = 0.15f),
            barHighlightColor = CalmColors.SoftTeal.copy(alpha = 0.35f),
            animate = isListening
        )

        // Bookmark FAB
        FloatingActionButton(
            onClick = { /* bookmark last 30 seconds */ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 56.dp),
            containerColor = CalmColors.SoftTeal.copy(alpha = 0.85f),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = "Bookmark",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ChapterTimeline() {
    val chapters = remember {
        listOf(
            ChapterItem("Daily Sync", "9:00 AM", "\u2615"),
            ChapterItem("Lunch", "12:15 PM", "\uD83C\uDFE2"),
            ChapterItem("Client Call", "2:30 PM", "\uD83D\uDCDE")
        )
    }

    Column {
        Text(
            text = "CHAPTER",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.40f),
            letterSpacing = 1.5.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(chapters) { chapter ->
                ChapterChip(chapter)
            }
        }
    }
}

private data class ChapterItem(
    val name: String,
    val time: String,
    val emoji: String
)

@Composable
private fun ChapterChip(chapter: ChapterItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = chapter.emoji,
                fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = chapter.time,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.50f)
        )
        Text(
            text = chapter.name,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.75f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp)
        )
    }
}

@Composable
private fun IdleView(viewModel: SearchViewModel) {
    val recentInsights by viewModel.recentInsights.collectAsState()

    LazyColumn {
        if (recentInsights.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WaveformVisualization(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(40.dp),
                            barCount = 30,
                            barColor = CalmColors.Lavender.copy(alpha = 0.10f),
                            barHighlightColor = CalmColors.Lavender.copy(alpha = 0.18f),
                            animate = false,
                            seed = 42L
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "I'm listening.\nAsk me anything when you need to remember.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.35f),
                            lineHeight = 28.sp
                        )
                    }
                }
            }
        } else {
            items(recentInsights, key = { it.id }) { insight ->
                InsightCard(
                    insight = insight,
                    onDismiss = { viewModel.dismissInsight(insight.id) }
                )
            }
        }
    }
}

@Composable
private fun InsightCard(
    insight: CloudApi.InsightResult,
    onDismiss: () -> Unit
) {
    val accentColor = insightAccentColor(insight.type)

    GlassCard(
        modifier = Modifier.padding(vertical = 6.dp),
        cornerRadius = 20.dp
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.7f))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = insight.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(14.dp),
                        tint = Color.White.copy(alpha = 0.40f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = insight.body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun SearchResultsView(uiState: SearchUiState) {
    var sourcesExpanded by remember { mutableStateOf(false) }
    val meaningfulResults = uiState.results.filter { !isNoiseResult(it.text) }
    val resultCount = meaningfulResults.size

    LazyColumn {
        item {
            ConversationCard(
                answer = uiState.answer,
                isSynthesizing = uiState.isSynthesizing,
                results = meaningfulResults.take(3)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (resultCount > 0) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { sourcesExpanded = !sourcesExpanded }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Based on $resultCount recording${if (resultCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.40f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        if (sourcesExpanded) Icons.Default.KeyboardArrowUp
                        else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.30f)
                    )
                }
            }

            if (sourcesExpanded) {
                items(meaningfulResults) { result ->
                    SourceItem(
                        result = result,
                        placeName = result.placeName
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(
    answer: String?,
    isSynthesizing: Boolean,
    results: List<SearchResultItem>
) {
    GlassCard(
        cornerRadius = 24.dp,
        fillAlpha = 0.10f,
        borderAlpha = 0.15f
    ) {
        Column {
            if (results.isNotEmpty()) {
                val firstResult = results.first()
                val timeText = formatRelativeTime(firstResult.timestamp)
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.90f)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isSynthesizing) {
                ThinkingDots()
            } else if (answer != null) {
                results.take(3).forEach { result ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            text = result.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (answer.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = answer,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerPlaceholder() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmerProgress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimSweep"
    )

    @Composable
    fun ShimmerBox(modifier: Modifier) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .drawBehind {
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            CalmColors.Periwinkle.copy(alpha = 0.08f),
                            CalmColors.Periwinkle.copy(alpha = 0.15f),
                            CalmColors.Periwinkle.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        start = Offset(size.width * shimmerProgress, 0f),
                        end = Offset(size.width * (shimmerProgress + 0.6f), size.height)
                    )
                    drawRect(brush)
                }
        )
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        repeat(3) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth(if (it == 2) 0.6f else 1f)
                    .height(16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val dot1 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(400), RepeatMode.Reverse), "d1")
    val dot2 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(400, delayMillis = 150), RepeatMode.Reverse), "d2")
    val dot3 by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(400, delayMillis = 300), RepeatMode.Reverse), "d3")

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Thinking",
            style = MaterialTheme.typography.bodyLarge,
            color = CalmColors.Periwinkle.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(2.dp))
        listOf(dot1, dot2, dot3).forEach { alpha ->
            Text(
                text = ".",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = CalmColors.Periwinkle.copy(alpha = alpha * 0.7f)
            )
        }
    }
}

@Composable
private fun SourceItem(
    result: SearchResultItem,
    placeName: String? = null
) {
    GlassCard(
        modifier = Modifier.padding(vertical = 3.dp),
        cornerRadius = 16.dp,
        fillAlpha = 0.06f,
        borderAlpha = 0.08f
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val timeText = formatRelativeTime(result.timestamp)
                Text(
                    text = if (placeName != null) "$timeText \u00B7 $placeName" else timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.40f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.text,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.60f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun insightAccentColor(type: String): Color = when (type) {
    "commitment" -> CalmColors.SoftAmber
    "followup", "timesensitive" -> CalmColors.Periwinkle
    "positive" -> CalmColors.SageGreen
    "preference" -> CalmColors.Lavender
    "friction", "inconsistency" -> CalmColors.MutedCoral
    else -> CalmColors.Lavender
}

private fun isNoiseResult(text: String): Boolean {
    val words = text.split("\\s+".toRegex()).filter { it.length > 1 }
    if (words.size < 2) return true
    val unique = words.map { it.lowercase().trimEnd('.', ',') }.toSet()
    if (unique.size == 1) return true
    if (words.size > 5 && unique.size.toFloat() / words.size < 0.15f) return true
    return false
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestamp }
    val timeFormat = java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
    return when {
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) ->
            "Today ${timeFormat.format(Date(timestamp))}"
        run {
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            yesterday.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
        } ->
            "Yesterday ${timeFormat.format(Date(timestamp))}"
        else -> {
            val dateFormat = java.text.SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

