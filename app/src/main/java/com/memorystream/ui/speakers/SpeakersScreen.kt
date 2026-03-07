package com.memorystream.ui.speakers

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.memorystream.api.CloudApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SpeakersScreen(onBack: () -> Unit = {}, viewModel: SpeakersViewModel = hiltViewModel()) {
    val speakers by viewModel.speakers.collectAsState()
    val enrollmentState by viewModel.enrollmentState.collectAsState()
    var showEnrollDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                    text = "People",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(onClick = { showEnrollDialog = true }) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (speakers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No people added",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add yourself first, then add\nfamily and friends",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = {
                        viewModel.resetEnrollment()
                        viewModel.updateName("")
                        showEnrollDialog = true
                    }) {
                        Text("Add yourself")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(speakers, key = { it.id }) { speaker ->
                    SpeakerCard(
                        speaker = speaker,
                        onDelete = { viewModel.deleteSpeaker(speaker) }
                    )
                }
            }
        }
    }

    if (showEnrollDialog) {
        EnrollmentDialog(
            state = enrollmentState,
            onNameChange = viewModel::updateName,
            onTogglePrimary = viewModel::togglePrimary,
            onSave = viewModel::enrollSpeaker,
            onDismiss = {
                showEnrollDialog = false
                viewModel.resetEnrollment()
            }
        )
    }
}

@Composable
private fun SpeakerCard(speaker: CloudApi.SpeakerResult, onDelete: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(speaker.color),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = speaker.name.first().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = speaker.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (speaker.is_primary) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Primary",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Text(
                    text = "Added ${dateFormat.format(Date(speaker.enrolled_at))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove ${speaker.name}?") },
            text = { Text("They won't be identified in future recordings.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EnrollmentDialog(
    state: EnrollmentState,
    onNameChange: (String) -> Unit,
    onTogglePrimary: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (state.success) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = { Text("Added!", textAlign = TextAlign.Center) },
            text = {
                Text(
                    "This person will be identified in future recordings.",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(onClick = onDismiss) { Text("Done") }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = {
            if (!state.isProcessing) onDismiss()
        },
        title = { Text("Add person") },
        text = {
            Column {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isProcessing
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = state.isPrimary,
                        onCheckedChange = { onTogglePrimary() },
                        enabled = !state.isProcessing
                    )
                    Text(
                        text = "This is me (primary user)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (state.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !state.isProcessing && state.name.isNotBlank()
            ) {
                if (state.isProcessing) {
                    Text("Saving...")
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            if (!state.isProcessing) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
