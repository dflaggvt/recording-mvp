package com.memorystream.ui.commitments

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.api.CloudApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommitmentsScreen(viewModel: CommitmentsViewModel = hiltViewModel()) {
    val commitments by viewModel.commitments.collectAsState()
    val completingIds by viewModel.completingIds.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Commitments",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (commitments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Nothing to follow up on.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You're on track.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                    )
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(commitments, key = { it.id }) { commitment ->
                    val isCompleting = commitment.id in completingIds
                    CommitmentItem(
                        commitment = commitment,
                        isCompleting = isCompleting,
                        onComplete = { viewModel.complete(commitment.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommitmentItem(
    commitment: CloudApi.InsightResult,
    isCompleting: Boolean,
    onComplete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val daysAgo = ((now - commitment.source_timestamp) / (24 * 60 * 60 * 1000L)).toInt()
    val timeText = when {
        daysAgo == 0 -> "Today"
        daysAgo == 1 -> "Yesterday"
        daysAgo < 7 -> "$daysAgo days ago"
        else -> dateFormat.format(Date(commitment.source_timestamp))
    }

    val isOverdue = daysAgo >= 3
    val isSeriouslyOverdue = daysAgo >= 7
    val accentColor = when {
        isSeriouslyOverdue -> Color(0xFFD4A050)
        isOverdue -> Color(0xFFD4A050).copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val iconColor by animateColorAsState(
        targetValue = if (isCompleting)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        animationSpec = tween(200),
        label = "checkColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (accentColor != Color.Transparent) {
                    Modifier.drawBehind {
                        drawLine(
                            color = accentColor,
                            start = Offset(0f, size.height * 0.15f),
                            end = Offset(0f, size.height * 0.85f),
                            strokeWidth = 8f
                        )
                    }
                } else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            IconButton(
                onClick = onComplete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (isCompleting) Icons.Default.CheckCircle
                    else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Mark complete",
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = commitment.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = commitment.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSeriouslyOverdue)
                            Color(0xFFD4A050)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    if (commitment.place_hint != null) {
                        Text(
                            text = " \u00B7 ${commitment.place_hint}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}
