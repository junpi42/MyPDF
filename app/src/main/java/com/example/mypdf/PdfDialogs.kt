package com.example.mypdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

@Composable
fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val s = strings()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Select Color", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val colors = listOf(
                    Color.Red, Color.Blue, Color.Green, Color.Yellow,
                    Color.Black, Color.Magenta, Color.Cyan, Color(0xFFFF6B6B),
                    Color(0xFF4ECDC4), Color(0xFF95E1D3),
                    Color(0xFFF38181), Color(0xFFAA96DA)
                )
                
                // Grid layout using rows
                colors.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { color ->
                            val isSelected = color == currentColor
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        onColorSelected(color)
                                        onDismiss()
                                    }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.close) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun TunerSettingsDialog(
    tuner: AudioTuner,
    isDaltonic: Boolean,
    onToggleDaltonic: () -> Unit,
    extendedMode: Boolean,
    onToggleExtendedMode: () -> Unit,
    onDismiss: () -> Unit
) {
    val a4 by tuner.baseFrequency.collectAsState(initial = 442.0)
    val noisy by tuner.noisyEnvironment.collectAsState(initial = false)
    val s = strings()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.tunerSettingsTitle, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // Frequency Control
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Base Frequency (A4)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilledIconButton(
                            onClick = { tuner.decrementBaseFrequency(1.0) },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-")
                        }
                        
                        Text(
                            text = "${a4.toInt()} Hz",
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        FilledIconButton(
                            onClick = { tuner.incrementBaseFrequency(1.0) },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.tunerNoisyEnv, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = noisy, onCheckedChange = { tuner.setNoisyEnvironment(it) })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.tunerDaltonismSoon, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isDaltonic, onCheckedChange = { onToggleDaltonic() })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(s.tunerExtendedMode, style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = extendedMode, onCheckedChange = { onToggleExtendedMode() })
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(s.close) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
fun SettingsDialog(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    isDaltonic: Boolean,
    onToggleDaltonic: () -> Unit,
    language: Language,
    onLanguageChange: (Language) -> Unit,
    // Ahora controlamos columnas en vez de un "gridScale" directo.
    gridColumns: Int,
    onColumnsChange: (Int) -> Unit,
    onResetTutorial: () -> Unit,
    googleAccount: com.google.android.gms.auth.api.signin.GoogleSignInAccount? = null,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val s = strings()
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            "Unknown"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(s.settingsTitle, style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // Google Account
                Column {
                    Text("Google Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    if (googleAccount != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // User Avatar (placeholder for now if no coil)
                             Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = googleAccount.givenName?.take(1) ?: "U",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(googleAccount.displayName ?: "User", style = MaterialTheme.typography.bodyLarge)
                                Text(googleAccount.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Sign Out")
                        }
                    } else {
                        Text("Sign in to sync your settings and files across devices.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onSignIn) {
                            Text("Sign In with Google")
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Theme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(s.themes, style = MaterialTheme.typography.titleMedium)
                    }
                    Switch(checked = isDarkMode, onCheckedChange = { onToggleDarkMode() })
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Language
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(s.languageLabel, style = MaterialTheme.typography.titleMedium)
                    }
                    
                    Box {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedButton(onClick = { expanded = true }) {
                            Text(when(language) {
                                Language.EN -> "English"
                                Language.ES -> "Español"
                                Language.FR -> "Français"
                                Language.IT -> "Italiano"
                            })
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            Language.values().forEach { lang ->
                                DropdownMenuItem(
                                    text = { 
                                        Text(when(lang) {
                                            Language.EN -> "English"
                                            Language.ES -> "Español"
                                            Language.FR -> "Français"
                                            Language.IT -> "Italiano"
                                        }) 
                                    },
                                    onClick = {
                                        onLanguageChange(lang)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Daltonic
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

                Spacer(Modifier.height(8.dp))

                MetronomeColorPreview(isDaltonic = isDaltonic)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Grid Size (ahora controla número de columnas).
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(s.gridSize, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))

                    // Leemos los límites desde recursos para poder personalizar por dispositivos (res/values, res/values-sw600dp)
                    val ctx = LocalContext.current
                    val minCols = try { ctx.resources.getInteger(R.integer.grid_min_columns) } catch (e: Exception) { 1 }
                    val maxCols = try { ctx.resources.getInteger(R.integer.grid_max_columns) } catch (e: Exception) { 4 }
                    // Detect tablet and double the minimum when on tablet as requested
                    val configuration = ctx.resources.configuration
                    val screenWidthDp = configuration.screenWidthDp
                    val isTabletLocal = screenWidthDp >= 600
                    val effectiveMin = if (isTabletLocal) {
                        // Ensure we don't exceed maxCols-1
                        (minCols * 2).coerceAtMost(maxCols - 1)
                    } else minCols

                    val range = (maxCols - effectiveMin).coerceAtLeast(1)

                    // Slider: 0 -> maxCols, range -> effectiveMin
                    val sliderValue = (maxCols - gridColumns).toFloat().coerceIn(0f, range.toFloat())
                    Slider(
                        value = sliderValue,
                        onValueChange = { v ->
                            val cols = (maxCols - v.toInt()).coerceIn(effectiveMin, maxCols)
                            onColumnsChange(cols)
                        },
                        valueRange = 0f..range.toFloat(),
                        steps = (range - 1).coerceAtLeast(0)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(text = "${gridColumns} columnas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Reset Tutorial
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text("Reset Tutorial", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = onResetTutorial) {
                        Text("Reset")
                    }
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // About
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("PentagramApp", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Text("v$versionName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(s.close) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun MetronomeColorPreview(isDaltonic: Boolean) {
    val s = strings()
    val label = if (isDaltonic) s.tunerDaltonismSoon else s.tunerNoisyEnv // reuse or create specific strings

    val leftColor: Color
    val centerColor: Color
    val rightColor: Color

    if (isDaltonic) {
        leftColor = Color(0xFFAA6C39)   // equivalente al azul pero más distinguible
        centerColor = Color(0xFFB39DDB) // franja central alternativa
        rightColor = Color(0xFF80CBC4)  // equivalente al rojo en paleta accesible
    } else {
        leftColor = Color(0xFF1565C0)   // azul (desafinada por abajo)
        centerColor = Color(0xFF2E7D32) // verde (afinada)
        rightColor = Color(0xFFD32F2F)  // rojo (desafinada por arriba)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isDaltonic) "Daltonic metronome preview" else "Standard metronome preview",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(CircleShape),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(leftColor)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(centerColor)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(rightColor)
            )
        }
    }
}
