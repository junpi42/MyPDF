package com.example.mypdf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditorSettingsDialog(
    isDaltonic: Boolean,
    onToggleDaltonic: () -> Unit,
    isVerticalScroll: Boolean,
    onVerticalScrollChange: (Boolean) -> Unit,
    enableStylusPressure: Boolean,
    onEnableStylusPressureChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(s.editorSettings, style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Daltonism
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(s.daltonismOption, style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(checked = isDaltonic, onCheckedChange = { onToggleDaltonic() })
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Scroll Direction
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(if (isVerticalScroll) s.verticalScroll else s.horizontalScroll, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    // Segmented Button or simple switch? User didn't specify.
                    // Let's use two Radio Buttons or specialized Switches for clarity.
                    // Or a specialized Toggle Row.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        FilterChip(
                            selected = isVerticalScroll,
                            onClick = { onVerticalScrollChange(true) },
                            label = { Text(s.verticalScroll) },
                            leadingIcon = { if (isVerticalScroll) Icon(Icons.Default.SwapVert, null) }
                        )
                        FilterChip(
                            selected = !isVerticalScroll,
                            onClick = { onVerticalScrollChange(false) },
                            label = { Text(s.horizontalScroll) },
                            leadingIcon = { if (!isVerticalScroll) Icon(Icons.Default.SwapVert, null) } // Could use SwapHoriz if available, or rotate
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Stylus Pressure
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Brush, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(s.stylusPressure, style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(checked = enableStylusPressure, onCheckedChange = { onEnableStylusPressureChange(it) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.close) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}
