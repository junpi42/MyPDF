package com.example.mypdf

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MetronomeSettingsPanel(
    visible: Boolean,
    bpm: Int,
    timeSignature: Pair<Int, Int>,
    onBpmChange: (Int) -> Unit,
    onTimeSignatureChange: (Pair<Int, Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut()
    ) {
        Surface(
            modifier = Modifier
                .width(300.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 8.dp,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = s.metronomeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onDismiss) {
                        Text(s.close)
                    }
                }

                HorizontalDivider()

                // BPM Control
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.metronomeTempo, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "$bpm BPM",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledIconButton(
                            onClick = { onBpmChange((bpm - 1).coerceAtLeast(40)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Remove, "Decrease BPM", modifier = Modifier.size(16.dp))
                        }
                        
                        Slider(
                            value = bpm.toFloat(),
                            onValueChange = { onBpmChange(it.toInt()) },
                            valueRange = 40f..240f,
                            modifier = Modifier.weight(1f)
                        )
                        
                        FilledIconButton(
                            onClick = { onBpmChange((bpm + 1).coerceAtMost(240)) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, "Increase BPM", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Time Signature Control
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.metronomeTimeSignature, style = MaterialTheme.typography.labelLarge)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val signatures = listOf(1 to 1, 2 to 4, 3 to 4, 4 to 4)
                        signatures.forEach { sig ->
                            val selected = sig == timeSignature
                            FilterChip(
                                selected = selected,
                                onClick = { onTimeSignatureChange(sig) },
                                label = { Text("${sig.first}/${sig.second}") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                // Visual Pattern Preview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(timeSignature.first) { index ->
                        val isStrong = index == 0
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isStrong) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isStrong) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                        )
                    }
                }
            }
        }
    }
}
