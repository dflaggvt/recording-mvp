package com.memorystream.ui.settings

import android.content.Context
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memorystream.service.RecordingService
import com.memorystream.ui.components.GlassCard
import com.memorystream.ui.components.GradientBackground
import com.memorystream.ui.navigation.isContinuousMemoryEnabled
import com.memorystream.ui.navigation.setContinuousMemoryEnabled
import com.memorystream.ui.theme.CalmColors

@Composable
fun SettingsScreen(onBack: () -> Unit = {}, onNavigateToDebug: () -> Unit = {}) {
    val context = LocalContext.current
    var memoryEnabled by remember { mutableStateOf(isContinuousMemoryEnabled(context)) }
    val isRecording by RecordingService.isRecording.collectAsState()

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White.copy(alpha = 0.60f)
                    )
                }
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.90f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GlassCard(cornerRadius = 20.dp) {
                Column {
                    Text(
                        text = "Continuous Memory",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CalmColors.Periwinkle
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRecording) "Listening" else "Off",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "Always-on recording, transcription, and indexing",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        }
                        Switch(
                            checked = memoryEnabled,
                            onCheckedChange = { enabled ->
                                memoryEnabled = enabled
                                setContinuousMemoryEnabled(context, enabled)
                                if (enabled) {
                                    RecordingService.startRecording(context)
                                } else {
                                    RecordingService.stopRecording(context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = CalmColors.Periwinkle,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            GlassCard(cornerRadius = 20.dp) {
                Column {
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CalmColors.Periwinkle
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "MemoryStream v0.2.0",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.80f)
                    )
                    Text(
                        text = "A private memory prosthetic",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.40f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val shape = RoundedCornerShape(20.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f), shape)
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)), shape)
                    .clickable { onNavigateToDebug() }
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.30f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Developer Tools",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        Text(
                            text = "Pipeline inspector, claim viewer, replay tools",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.30f)
                        )
                    }
                }
            }
        }
    }
}
