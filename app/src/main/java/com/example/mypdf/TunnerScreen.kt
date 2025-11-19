@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.mypdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// ================ PANTALLA COMPLETA (PROTOTIPO) ==================

@Composable
fun TunerScreen(tuner: AudioTuner) {
    val scope = rememberCoroutineScope()
    val a4 by tuner.baseFrequency.collectAsState(initial = 442.0)
    val noisy by tuner.noisyEnvironment.collectAsState(initial = false)
    val result by tuner.tuningState.collectAsState(initial = null)

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Afinador") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menú")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Ambiente ruidoso")
                                    Switch(
                                        checked = noisy,
                                        onCheckedChange = { tuner.setNoisyEnvironment(it) }
                                    )
                                }
                            },
                            onClick = { /* El Switch ya hace el trabajo */ }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A4: ${a4.toInt()} Hz", modifier = Modifier.weight(1f))
                Button(onClick = { tuner.decrementBaseFrequency(1.0) }) { Text("−1 Hz") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { tuner.incrementBaseFrequency(1.0) }) { Text("+1 Hz") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { tuner.startTuning(scope) }) { Text("Iniciar") }
                OutlinedButton(onClick = { tuner.stopTuning() }) { Text("Parar") }
            }

            when {
                result == null -> Text("Sin dato aún…")
                result?.errorMessage != null -> {
                    Text(
                        "Error: ${result?.errorMessage}",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { tuner.startTuning(scope) }) {
                        Text("Reintentar afinador")
                    }
                }
                else -> {
                    Text("Nota: ${result!!.targetNote}")
                    Text("Frecuencia: ${"%.2f".format(result!!.detectedFrequency)} Hz")
                    Text("Desviación: ${"%.1f".format(result!!.centsOff)} cents")
                    Text(if (result!!.isInTune) "✅ Afinado" else "🟡 Desafinado")
                }
            }
        }
    }
}

// ================ WRAPPER PARA USAR EL NUEVO WIDGET ==================

@Composable
fun TunnerScreen(tunner: AudioTuner) {
    TunerScreen(tunner)
}

// ================ WIDGET PEQUEÑO (USADO EN EL PDF VIEWER) ==================

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
                onLongClick = { menuOpen = true } // ⇐ aquí aparece el menú de opciones
            )
            .background(bg, shape = MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Contenido compacto: nota + cents
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

        // Menú de pulsación larga con las opciones del prototipo
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            // Ambiente ruidoso (igual que en el prototipo)
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

            // Ajuste fino de A4 (-1 / +1 Hz), como en el prototipo
            DropdownMenuItem(
                text = { Text("A4: ${a4.toInt()} Hz  (−1 Hz)") },
                onClick = {
                    tunner.decrementBaseFrequency(1.0)
                }
            )
            DropdownMenuItem(
                text = { Text("A4: ${a4.toInt()} Hz  (+1 Hz)") },
                onClick = {
                    tunner.incrementBaseFrequency(1.0)
                }
            )

            // Iniciar afinador (equivalente al botón "Iniciar" del prototipo)
            DropdownMenuItem(
                text = { Text("Iniciar afinador") },
                onClick = {
                    tunner.startTuning(scope)
                    menuOpen = false
                }
            )

            // Parar afinador (equivalente al botón "Parar")
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
