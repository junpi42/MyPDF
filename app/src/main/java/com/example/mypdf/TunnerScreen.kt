@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mypdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// Archivo de prototipo: actualmente no se usa en el flujo principal.
// Se mantiene solo el widget Tunner por si lo quieres reutilizar manualmente.

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tunner(
    tunner: AudioTuner,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    val a4 by tunner.baseFrequency.collectAsState(initial = 442.0)
    val noisy by tunner.noisyEnvironment.collectAsState(initial = false)
    val result by tunner.tuningState.collectAsState(initial = null)
    var menuOpen by remember { mutableStateOf(false) }

    val cents = result?.centsOff ?: 0.0
    val inTune = result?.isInTune == true
    val bg = when {
        result == null -> Color(0xFF444444)
        inTune -> Color(0xFF2ECC71)      // verde
        cents > 10.0 -> Color(0xFFE74C3C) // rojo agudo
        cents < -10.0 -> Color(0xFF3498DB) // azul grave
        else -> Color(0xFFFFC107)        // cerca, ámbar
    }

    Box(
        modifier = modifier
            .combinedClickable(
                onClick = { /* tap normal: solo ver nota */ },
                onLongClick = { menuOpen = true } // menú de opciones
            )
            .background(bg, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.wrapContentWidth()
        ) {
            Text(
                text = result?.targetNote ?: "--",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            if (result != null) {
                Text(
                    text = "${"%+.1f".format(cents)} cents",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Black
                )
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Ambiente ruidoso")
                        Switch(
                            checked = noisy,
                            onCheckedChange = {
                                tunner.setNoisyEnvironment(it)
                            }
                        )
                    }
                },
                onClick = { /* el Switch hace el trabajo */ }
            )

            DropdownMenuItem(
                text = { Text("A4: ${a4.toInt()} Hz  (−1 Hz)") },
                onClick = { tunner.decrementBaseFrequency(1.0) }
            )
            DropdownMenuItem(
                text = { Text("A4: ${a4.toInt()} Hz  (+1 Hz)") },
                onClick = { tunner.incrementBaseFrequency(1.0) }
            )

            DropdownMenuItem(
                text = { Text("Iniciar afinador") },
                onClick = {
                    tunner.startTuning(scope)
                    menuOpen = false
                }
            )

            DropdownMenuItem(
                text = { Text("Parar afinador") },
                onClick = {
                    tunner.stopTuning(clearState = false)
                    menuOpen = false
                }
            )
        }
    }
}
