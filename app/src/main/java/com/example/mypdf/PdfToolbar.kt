package com.example.mypdf

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

// Barra de herramientas lateral extraída de PdfViewerScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StyledLeftToolBar(
    selectedTool: String,
    paletteColors: List<Color>,
    selectedPaletteIndex: Int,
    strokeWidth: Float,
    smoothingEnabled: Boolean, // mantenido para compatibilidad aunque no se use aún visualmente
    onSelectTool: (String) -> Unit,
    onPaletteSlotClicked: (Int) -> Unit,
    onStrokeChange: (Float) -> Unit,
    onToggleSmoothing: () -> Unit, // idem
    darkMode: Boolean, // idem
    language: Language, // idem
    onToolboxPositioned: (Rect) -> Unit = {},
    hasUndo: Boolean = true,
    onUndo: () -> Unit = {},
    onMarkerLongPress: () -> Unit = {},
    onHighlighterLongPress: () -> Unit = {},
    showSlider: Boolean = true, // Si es false, no se muestra el slider (útil para landscape)
    modifier: Modifier = Modifier
) {
    val s = strings()
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp > 600
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Toolbar un pelín más estrecha
    val toolbarWidth = if (isTablet) 88.dp else 72.dp
    val buttonSize = if (isTablet) 52.dp else 44.dp
    val iconSize = if (isTablet) 34.dp else 28.dp
    val colorSize = if (isTablet) 44.dp else 36.dp

    Surface(
        modifier = modifier
            .width(toolbarWidth)
            .onGloballyPositioned { coordinates ->
                onToolboxPositioned(coordinates.boundsInRoot())
            },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
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

            StyledToolButtonWithLongPress(
                icon = Icons.Default.Create,
                label = "Marker",
                selected = selectedTool == "marker",
                onClick = { onSelectTool("marker") },
                onLongClick = {
                    onSelectTool("marker")
                    onMarkerLongPress()
                },
                size = buttonSize,
                iconSize = iconSize
            )
            
            StyledToolButtonWithLongPress(
                icon = Icons.Default.BorderColor,
                label = "Highlighter",
                selected = selectedTool == "highlighter",
                onClick = { onSelectTool("highlighter") },
                onLongClick = {
                    onSelectTool("highlighter")
                    onHighlighterLongPress()
                },
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

            // Botón de Revertir/Deshacer ubicado justo debajo del borrador
            // Botón de Revertir/Deshacer siempre visible, deshabilitado si no hay undo
            StyledToolButton(
                icon = Icons.Default.Undo,
                label = s.toolUndo,
                selected = false,
                onClick = onUndo,
                size = buttonSize,
                iconSize = iconSize,
                enabled = hasUndo
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.width(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(8.dp))

            // --- Colors Group (3 Slots) ---
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                paletteColors.forEachIndexed { index, color ->
                    val isSelected = selectedPaletteIndex == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) colorSize + 8.dp else colorSize)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { onPaletteSlotClicked(index) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.width(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(8.dp))

            // --- Preview ---
            if (selectedTool == "marker" || selectedTool == "highlighter") {
                Box(
                    modifier = Modifier
                        .size(height = if (showSlider) 120.dp else 80.dp, width = 48.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val currentColor = paletteColors.getOrNull(selectedPaletteIndex) ?: Color.Black
                    val previewColor = if (selectedTool == "highlighter") currentColor.copy(alpha = 0.5f) else currentColor

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            // Start from top center
                            moveTo(size.width * 0.5f, 0f)
                            // Curve 1
                            quadraticBezierTo(
                                size.width, size.height * 0.25f,
                                size.width * 0.5f, size.height * 0.5f
                            )
                            // Curve 2
                            quadraticBezierTo(
                                0f, size.height * 0.75f,
                                size.width * 0.5f, size.height
                            )
                        }
                        drawPath(
                            path = path,
                            color = previewColor,
                            style = Stroke(
                                width = strokeWidth * 1000f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            // --- Slider Group (solo si showSlider es true) ---
            if (showSlider && (selectedTool == "marker" || selectedTool == "eraser" || selectedTool == "highlighter")) {
                val (minVal, maxVal) = when (selectedTool) {
                    "marker" -> 0.003f to 0.02f
                    "highlighter" -> 0.01f to 0.08f
                    else -> 0.015f to 0.1f
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val sliderLength = maxHeight - 16.dp
                    val safeLength = if (sliderLength > 50.dp) sliderLength else 50.dp

                    Slider(
                        value = strokeWidth,
                        onValueChange = onStrokeChange,
                        valueRange = minVal..maxVal,
                        modifier = Modifier
                            .graphicsLayer { rotationZ = 270f }
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = constraints.minHeight,
                                        maxWidth = constraints.maxHeight,
                                        minHeight = constraints.minWidth,
                                        maxHeight = constraints.maxWidth
                                    )
                                )
                                layout(placeable.height, placeable.width) {
                                    placeable.place(
                                        -(placeable.width - placeable.height) / 2,
                                        -(placeable.height - placeable.width) / 2
                                    )
                                }
                            }
                            .width(safeLength)
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
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
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    enabled: Boolean = true
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
            ),
            enabled = enabled
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
            ),
            enabled = enabled
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * Botón de herramienta con soporte para pulsación larga.
 * Usado para marker y highlighter donde el long press abre el panel de ajustes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StyledToolButtonWithLongPress(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 56.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    enabled: Boolean = true
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = containerColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(iconSize),
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
            )
        }
    }
}
