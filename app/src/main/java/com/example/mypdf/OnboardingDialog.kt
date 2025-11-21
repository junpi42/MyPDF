package com.example.mypdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun OnboardingDialog(
    initialLanguage: Language,
    onFinish: (Language, Boolean, Boolean) -> Unit
) {
    // State for the onboarding flow
    var currentLanguage by remember { mutableStateOf(initialLanguage) }
    var isDarkMode by remember { mutableStateOf(false) }
    var isDaltonic by remember { mutableStateOf(false) }
    
    // We use a derived state for strings so they update immediately when language changes
    val s = stringsFor(currentLanguage)

    Dialog(
        onDismissRequest = {}, // Prevent dismissal without finishing
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 400.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Title
                    Text(
                        text = s.welcomeTitle,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Language Selection
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.chooseLanguage, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            FilterChip(
                                selected = currentLanguage == Language.EN,
                                onClick = { currentLanguage = Language.EN },
                                label = { Text("English") },
                                leadingIcon = if (currentLanguage == Language.EN) {
                                    { Icon(Icons.Default.Check, null) }
                                } else null
                            )
                            FilterChip(
                                selected = currentLanguage == Language.ES,
                                onClick = { currentLanguage = Language.ES },
                                label = { Text("Español") },
                                leadingIcon = if (currentLanguage == Language.ES) {
                                    { Icon(Icons.Default.Check, null) }
                                } else null
                            )
                        }
                    }

                    HorizontalDivider()

                    // Theme Selection
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(s.chooseTheme, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Light Preview
                            ThemePreviewOption(
                                dark = false,
                                selected = !isDarkMode,
                                onClick = { isDarkMode = false },
                                label = "Light" // Could be translated if needed, but icons/preview speak for themselves
                            )
                            // Dark Preview
                            ThemePreviewOption(
                                dark = true,
                                selected = isDarkMode,
                                onClick = { isDarkMode = true },
                                label = "Dark"
                            )
                        }
                    }

                    HorizontalDivider()

                    // Daltonism Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(s.daltonismOption, style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = isDaltonic,
                            onCheckedChange = { isDaltonic = it }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Finish Button
                    Button(
                        onClick = { onFinish(currentLanguage, isDarkMode, isDaltonic) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(s.finish, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePreviewOption(
    dark: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(80.dp, 120.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium
                )
                .background(if (dark) Color(0xFF1C1B1F) else Color(0xFFFFFBFE)) // Standard M3 backgrounds
                .clickable(onClick = onClick)
        ) {
            // Mini UI representation
            Column(Modifier.padding(8.dp)) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (dark) Color(0xFFD0BCFF) else Color(0xFF6750A4)) // Primary
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(if (dark) Color(0xFF49454F) else Color(0xFFE7E0EC)) // Surface Variant
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .width(40.dp)
                        .height(8.dp)
                        .background(if (dark) Color(0xFF49454F) else Color(0xFFE7E0EC))
                )
            }
            
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
