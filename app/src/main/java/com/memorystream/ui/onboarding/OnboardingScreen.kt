package com.memorystream.ui.onboarding

import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.memorystream.ui.components.GradientBackground
import com.memorystream.ui.components.WaveformVisualization
import com.memorystream.ui.theme.CalmColors
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(false) }
    var showDoneMessage by remember { mutableStateOf(false) }

    var showWaveform by remember { mutableStateOf(false) }
    var showTitle by remember { mutableStateOf(false) }
    var showSubtitle by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var showMicStatus by remember { mutableStateOf(false) }
    var showToggle by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val hasUsbMic = remember {
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).any {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
    }

    LaunchedEffect(Unit) {
        delay(200); showWaveform = true
        delay(400); showTitle = true
        delay(200); showSubtitle = true
        delay(300); showDescription = true
        delay(200); showMicStatus = true
        delay(200); showToggle = true
    }

    LaunchedEffect(enabled) {
        if (enabled) {
            showDoneMessage = true
            delay(1500)
            onComplete()
        }
    }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = showWaveform, enter = fadeIn(tween(800))) {
                WaveformVisualization(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(80.dp),
                    barCount = 40,
                    barColor = CalmColors.Periwinkle.copy(alpha = 0.15f),
                    barHighlightColor = CalmColors.Lavender.copy(alpha = 0.35f),
                    animate = true,
                    seed = 12345L
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(visible = showTitle, enter = fadeIn(tween(400))) {
                Text(
                    text = "MemoryStream",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White.copy(alpha = 0.90f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(visible = showSubtitle, enter = fadeIn(tween(400))) {
                Text(
                    text = "Listens so you don't have to.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.40f)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            AnimatedVisibility(visible = showDescription, enter = fadeIn(tween(400))) {
                Text(
                    text = "Conversations are privately transcribed and indexed on your device. Commitments are tracked automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            AnimatedVisibility(visible = showMicStatus && hasUsbMic, enter = fadeIn(tween(300))) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "USB microphone detected.",
                        style = MaterialTheme.typography.labelMedium,
                        color = CalmColors.SoftTeal.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            AnimatedVisibility(visible = showToggle, enter = fadeIn(tween(400))) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Enable Continuous Memory",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = CalmColors.Periwinkle,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.size(width = 60.dp, height = 32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            AnimatedVisibility(
                visible = showDoneMessage,
                enter = fadeIn(tween(300)) + scaleIn(
                    initialScale = 0.5f,
                    animationSpec = tween(400)
                )
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = CalmColors.SoftTeal.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You're all set.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CalmColors.Periwinkle.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
