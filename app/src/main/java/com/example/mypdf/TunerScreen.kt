@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.mypdf

import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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
                            onClick = { /* Switch maneja el cambio */ }
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
                    Text("Error: ${result?.errorMessage}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { tuner.startTuning(scope) }) { Text("Reintentar afinador") }
                }
                else -> {
                    Text("Nota: ${result!!.targetNote}")
                    Text(
                        "Frecuencia: ${"%.2f".format(result!!.detectedFrequency)} Hz"
                    )
                    Text(
                        "Desviación: ${"%.1f".format(result!!.centsOff)} cents"
                    )
                    Text(if (result!!.isInTune) "✅ Afinado" else "🟡 Desafinado")
                }
            }
        }
    }
}
