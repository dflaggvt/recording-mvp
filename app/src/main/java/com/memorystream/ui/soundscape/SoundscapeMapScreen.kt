package com.memorystream.ui.soundscape

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.memorystream.api.CloudApi
import com.memorystream.ui.theme.CalmColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class TimeOfDay(val label: String, val color: Color, val hue: Float) {
    Morning("Morning", Color(0xFFE8A838), BitmapDescriptorFactory.HUE_ORANGE),
    Afternoon("Afternoon", Color(0xFFE06050), BitmapDescriptorFactory.HUE_RED),
    Evening("Evening", Color(0xFFA060C0), BitmapDescriptorFactory.HUE_VIOLET),
    Night("Night", Color(0xFF5888CC), BitmapDescriptorFactory.HUE_AZURE);

    companion object {
        fun from(timestamp: Long): TimeOfDay {
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            return when (cal.get(Calendar.HOUR_OF_DAY)) {
                in 5..11 -> Morning
                in 12..16 -> Afternoon
                in 17..20 -> Evening
                else -> Night
            }
        }
    }
}

@Composable
fun SoundscapeMapScreen(
    onBack: () -> Unit = {},
    dayTimestamp: Long = System.currentTimeMillis(),
    viewModel: SoundscapeMapViewModel = hiltViewModel()
) {
    LaunchedEffect(dayTimestamp) {
        viewModel.loadDay(dayTimestamp)
    }

    val uiState by viewModel.uiState.collectAsState()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(40.0, -74.0), 12f)
    }

    // Fit camera to pins when chunks load
    LaunchedEffect(uiState.chunks) {
        val located = uiState.chunks.filter { it.latitude != null && it.longitude != null }
        if (located.isNotEmpty()) {
            val bounds = LatLngBounds.builder().apply {
                located.forEach { include(LatLng(it.latitude!!, it.longitude!!)) }
            }.build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        }
    }

    val dayLabel = remember(uiState.dayTimestamp) {
        if (uiState.dayTimestamp == 0L) return@remember "Today"
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L
        when {
            uiState.dayTimestamp >= todayStart -> "Today"
            uiState.dayTimestamp >= yesterdayStart -> "Yesterday"
            else -> SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
                .format(Date(uiState.dayTimestamp))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White.copy(alpha = 0.60f)
                )
            }
            Text(
                text = "Soundscape",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White.copy(alpha = 0.90f)
            )
        }

        // Day navigator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateDay(-1) }) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = "Previous day",
                    tint = Color.White.copy(alpha = 0.50f)
                )
            }
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.80f)
            )
        }

        // Map
        Box(modifier = Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    mapToolbarEnabled = false
                )
            ) {
                uiState.chunks.forEach { chunk ->
                    if (chunk.latitude != null && chunk.longitude != null) {
                        val timeOfDay = TimeOfDay.from(chunk.start_timestamp)
                        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
                        val startTime = timeFormat.format(Date(chunk.start_timestamp))
                        val title = chunk.place_name ?: "Recording"

                        Marker(
                            state = MarkerState(
                                position = LatLng(chunk.latitude, chunk.longitude)
                            ),
                            title = title,
                            snippet = "$startTime · ${timeOfDay.label}",
                            icon = BitmapDescriptorFactory.defaultMarker(timeOfDay.hue)
                        )
                    }
                }
            }

            // Time-of-day legend overlay
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.55f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeOfDay.entries.forEach { tod ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tod.color)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tod.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.70f)
                        )
                    }
                }
            }
        }

        // Bottom bar with recording count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.60f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val totalChunks = uiState.chunks.size
            Text(
                text = "$totalChunks recording${if (totalChunks != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.60f)
            )
        }
    }
}
