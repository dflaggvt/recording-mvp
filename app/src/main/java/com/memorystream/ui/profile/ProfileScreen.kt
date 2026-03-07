package com.memorystream.ui.profile

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.service.RecordingService
import com.memorystream.ui.components.GlassCard
import com.memorystream.ui.navigation.isContinuousMemoryEnabled
import com.memorystream.ui.navigation.setContinuousMemoryEnabled
import com.memorystream.ui.theme.CalmColors

@Composable
fun ProfileScreen(
    onBack: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onNavigateToSpeakers: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var memoryEnabled by remember { mutableStateOf(isContinuousMemoryEnabled(context)) }
    val isRecording by RecordingService.isRecording.collectAsState()
    val geofenceEnabled by viewModel.featureEnabled.collectAsState()
    val zones by viewModel.zones.collectAsState()
    var showAddZoneDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Profile & Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White.copy(alpha = 0.90f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // User avatar and name
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CalmColors.SoftTeal),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "MemoryStream User",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.90f)
                )
                Text(
                    text = "Personal memory assistant",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.45f)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Automatic Privacy Zones
        Text(
            text = "Automatic Privacy Zones",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.85f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Geo-fence toggle
        GlassCard(cornerRadius = 16.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Geo-fence Enabled",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Switch(
                    checked = geofenceEnabled,
                    onCheckedChange = { viewModel.setFeatureEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = CalmColors.SoftTeal,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Exclusion zone list
        if (zones.isNotEmpty()) {
            GlassCard(cornerRadius = 16.dp) {
                Column {
                    zones.forEach { zone ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = zone.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = "${zone.radiusMeters.toInt()}m radius",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.45f)
                                )
                            }
                            IconButton(onClick = { viewModel.removeZone(zone.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove zone",
                                    tint = Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No privacy zones configured",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Add zone button
        GlassCard(
            modifier = Modifier.clickable { showAddZoneDialog = true },
            cornerRadius = 16.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = CalmColors.SoftTeal,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Add Privacy Zone",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = CalmColors.SoftTeal
                )
            }
        }

        if (showAddZoneDialog) {
            AddZoneDialog(
                viewModel = viewModel,
                onDismiss = { showAddZoneDialog = false }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Menu items
        ProfileMenuItem(
            icon = Icons.Default.Person,
            title = "Manage Voice Biometrics",
            onClick = onNavigateToSpeakers
        )

        Spacer(modifier = Modifier.height(8.dp))

        ProfileMenuItem(
            icon = Icons.Default.Storage,
            title = "Storage",
            subtitle = "On-Device, 12GB used",
            onClick = {}
        )

        Spacer(modifier = Modifier.height(8.dp))

        ProfileMenuItem(
            icon = Icons.Default.Gavel,
            title = "Legal & Consent",
            onClick = {}
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Continuous Memory toggle
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
                            checkedTrackColor = CalmColors.SoftTeal,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Developer Tools
        GlassCard(
            modifier = Modifier.clickable { onNavigateToDebug() },
            cornerRadius = 20.dp,
            fillAlpha = 0.04f,
            borderAlpha = 0.06f
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.30f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
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
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AddZoneDialog(
    viewModel: ProfileViewModel,
    onDismiss: () -> Unit
) {
    var zoneName by remember { mutableStateOf("") }
    var latText by remember { mutableStateOf("") }
    var lngText by remember { mutableStateOf("") }
    var radius by remember { mutableFloatStateOf(200f) }
    val currentLatLng by viewModel.currentLatLng.collectAsState()

    LaunchedEffect(currentLatLng) {
        currentLatLng?.let { (lat, lng) ->
            latText = "%.6f".format(lat)
            lngText = "%.6f".format(lng)
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White.copy(alpha = 0.7f),
        focusedBorderColor = CalmColors.SoftTeal,
        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
        focusedLabelColor = CalmColors.SoftTeal,
        unfocusedLabelColor = Color.White.copy(alpha = 0.5f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F2027),
        title = {
            Text("Add Privacy Zone", color = Color.White)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = zoneName,
                    onValueChange = { zoneName = it },
                    label = { Text("Zone name") },
                    placeholder = { Text("e.g. Courthouse", color = Color.White.copy(alpha = 0.3f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = textFieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it },
                        label = { Text("Latitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it },
                        label = { Text("Longitude") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = textFieldColors
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { viewModel.resolveCurrentLocation() }) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = CalmColors.SoftTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Use Current Location", color = CalmColors.SoftTeal)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Radius: ${radius.toInt()}m",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Slider(
                    value = radius,
                    onValueChange = { radius = it },
                    valueRange = 100f..500f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = CalmColors.SoftTeal,
                        activeTrackColor = CalmColors.SoftTeal
                    )
                )
            }
        },
        confirmButton = {
            val lat = latText.toDoubleOrNull()
            val lng = lngText.toDoubleOrNull()
            TextButton(
                onClick = {
                    if (zoneName.isNotBlank() && lat != null && lng != null) {
                        viewModel.addZone(zoneName.trim(), lat, lng, radius.toDouble())
                        onDismiss()
                    }
                },
                enabled = zoneName.isNotBlank() && lat != null && lng != null
            ) {
                Text("Save", color = CalmColors.SoftTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.5f))
            }
        }
    )
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.clickable { onClick() },
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.85f)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.45f)
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
