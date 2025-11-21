package com.example.mypdf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column {
                val colors = listOf(
                    Color.Red, Color.Blue, Color.Green, Color.Yellow,
                    Color.Black, Color.Magenta, Color.Cyan, Color(0xFFFF6B6B),
                    Color(0xFF4ECDC4), Color(0xFF95E1D3),
                    Color(0xFFF38181), Color(0xFFAA96DA)
                )
                colors.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { color ->
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                color = color,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable {
                                        onColorSelected(color)
                                        onDismiss()
                                    },
                                shadowElevation = if (color == currentColor) 4.dp else 0.dp,
                                border = if (color == currentColor)
                                    androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
                                else null
                            ) {}
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.close) }
        }
    )
}

@Composable
fun TunerSettingsDialog(
    tuner: AudioTuner,
    onDismiss: () -> Unit
) {
    val a4 by tuner.baseFrequency.collectAsState(initial = 442.0)
    val noisy by tuner.noisyEnvironment.collectAsState(initial = false)
    var daltonic by remember { mutableStateOf(false) }
    val s = strings()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.tunerSettingsTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { tuner.decrementBaseFrequency(1.0) }) {
                        Icon(Icons.Default.Remove, contentDescription = "-")
                    }
                    Text(
                        text = "A4: ${a4.toInt()} Hz",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { tuner.incrementBaseFrequency(1.0) }) {
                        Icon(Icons.Default.Add, contentDescription = "+")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = noisy, onCheckedChange = { tuner.setNoisyEnvironment(it) })
                    Spacer(Modifier.width(4.dp))
                    Text(s.tunerNoisyEnv)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = daltonic, onCheckedChange = { daltonic = it })
                    Spacer(Modifier.width(4.dp))
                    Text(s.tunerDaltonismSoon)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(s.close) } }
    )
}
