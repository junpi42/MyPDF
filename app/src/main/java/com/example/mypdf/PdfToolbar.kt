package com.example.mypdf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Barra de herramientas lateral extraída de PdfViewerScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StyledLeftToolBar(
    selectedTool: String,
    penColor: Color,
    strokeWidth: Float,
    smoothingEnabled: Boolean,
    onSelectTool: (String) -> Unit,
    onColorClick: () -> Unit,
    onColorChanged: (Color) -> Unit, // New callback
    onStrokeChange: (Float) -> Unit,
    onToggleSmoothing: () -> Unit,
    onUndo: () -> Unit,
    darkMode: Boolean,
    language: Language,
    modifier: Modifier = Modifier
) {
    val s = strings()
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = config.screenWidthDp > 600
    
    val toolbarWidth = if (isTablet) 100.dp else 80.dp
    val buttonSize = if (isTablet) 64.dp else 56.dp
    val iconSize = if (isTablet) 32.dp else 24.dp
    val colorSize = if (isTablet) 56.dp else 48.dp

    Surface(
        modifier = modifier.width(toolbarWidth).padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- Tools Group ---
            StyledToolButton(
                icon = Icons.Default.TouchApp,
                label = s.toolMove,
                selected = selectedTool == "none",
                onClick = { onSelectTool("none") },
                size = buttonSize,
                iconSize = iconSize
            )

            StyledToolButton(
                icon = Icons.Default.Edit,
                label = s.toolPen,
                selected = selectedTool == "pen",
                onClick = { onSelectTool("pen") },
                size = buttonSize,
                iconSize = iconSize
            )

            StyledToolButton(
                icon = Icons.Default.Delete,
                label = s.toolErase,
                selected = selectedTool == "eraser",
                onClick = { onSelectTool("eraser") },
                size = buttonSize,
                iconSize = iconSize
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.width(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(8.dp))

            // --- Properties Group ---
            // 3-Color Palette
            val colors = listOf(Color.Red, Color.Blue, Color.Black)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                colors.forEach { color ->
                    val isSelected = penColor == color
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) colorSize + 4.dp else colorSize)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .combinedClickable(
                                onClick = { onColorChanged(color) },
                                onLongClick = { onColorClick() }
                            )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stroke Width Slider
            if (selectedTool == "pen" || selectedTool == "eraser") {
                val (minVal, maxVal) = if (selectedTool == "pen") {
                    0.003f to 0.02f
                } else {
                    0.015f to 0.1f
                }

                Box(
                    modifier = Modifier
                        .height(120.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Slider(
                        value = strokeWidth,
                        onValueChange = onStrokeChange,
                        valueRange = minVal..maxVal,
                        modifier = Modifier
                            .graphicsLayer { rotationZ = 270f }
                            .width(120.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.width(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(8.dp))

            // --- Actions Group ---
            StyledToolButton(
                icon = Icons.Default.Tune,
                label = s.toolSmooth,
                selected = smoothingEnabled,
                onClick = onToggleSmoothing,
                size = buttonSize,
                iconSize = iconSize
            )

            Spacer(modifier = Modifier.weight(1f))

            FilledIconButton(
                onClick = onUndo,
                modifier = Modifier.size(buttonSize),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = s.toolUndo, modifier = Modifier.size(iconSize))
            }
        }
    }
}

@Composable
fun StyledToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    if (compact) {
        IconToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(size),
            colors = IconButtonDefaults.iconToggleButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                checkedContainerColor = containerColor,
                checkedContentColor = contentColor
            )
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize))
        }
    } else {
        FilledIconToggleButton(
            checked = selected,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(size),
            shape = MaterialTheme.shapes.medium,
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize))
        }
    }
}
