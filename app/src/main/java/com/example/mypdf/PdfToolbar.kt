package com.example.mypdf

import android.util.Log
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

// Barra de herramientas lateral extraída de PdfViewerScreen

@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StyledLeftToolBar(
    selectedTool: String,
    paletteColors: List<Color>,
    selectedPaletteIndex: Int,
    strokeWidth: Float,
    smoothingEnabled: Boolean,
    onSelectTool: (String) -> Unit,
    onPaletteSlotClicked: (Int) -> Unit,
    onStrokeChange: (Float) -> Unit,
    onToggleSmoothing: () -> Unit,
    darkMode: Boolean,
    language: Language,
    onToolboxPositioned: (Rect) -> Unit = {},
    hasUndo: Boolean = true,
    onUndo: () -> Unit = {},
    onMarkerLongPress: () -> Unit = {},
    onHighlighterLongPress: () -> Unit = {},
    onMarkerButtonPositioned: (Rect) -> Unit = {},
    onHighlighterButtonPositioned: (Rect) -> Unit = {},
    showSlider: Boolean = true,
    modifier: Modifier = Modifier,
    // Stylus parameters - nuevo sistema basado en detección por uso
    stylusDetected: Boolean = false,
    stylusButtonTool: StylusTool = StylusTool.MARKER,
    onStylusButtonClick: () -> Unit = {},
    onStylusButtonToolChange: (StylusTool) -> Unit = {},
    // Nuevos parámetros para el popup de tamaño
    markerStrokeWidth: Float = 0.006f,
    highlighterStrokeWidth: Float = 0.02f,
    eraserRadiusNorm: Float = 0.03f,
    onMarkerStrokeChange: (Float) -> Unit = {},
    onHighlighterStrokeChange: (Float) -> Unit = {},
    onEraserRadiusChange: (Float) -> Unit = {},
    // Parámetro para presión capacitiva del stylus
    enableStylusPressure: Boolean = true,
    onEnableStylusPressureChange: (Boolean) -> Unit = {},
    onShowSettings: () -> Unit = {}
) {
    val s = strings()
    val config = LocalConfiguration.current
    val isTablet = config.screenWidthDp > 600
    val density = LocalDensity.current

    // Log para depuración
    Log.d("PdfToolbar", "StyledLeftToolBar recompuesto con selectedTool=$selectedTool")

    // Estado para el diálogo de selección de herramienta del stylus
    var showStylusToolDialog by remember { mutableStateOf(false) }

    // Estados para los popups de ajuste de tamaño
    var showMarkerPopup by remember { mutableStateOf(false) }
    var showHighlighterPopup by remember { mutableStateOf(false) }
    var showEraserPopup by remember { mutableStateOf(false) }

    // Posiciones de los botones para ubicar los popups
    var markerButtonRect by remember { mutableStateOf(Rect.Zero) }
    var highlighterButtonRect by remember { mutableStateOf(Rect.Zero) }
    var eraserButtonRect by remember { mutableStateOf(Rect.Zero) }

    // Toolbar un pelín más estrecha
    val toolbarWidth = if (isTablet) 88.dp else 72.dp
    val buttonSize = if (isTablet) 52.dp else 44.dp
    val iconSize = if (isTablet) 34.dp else 28.dp
    val colorSize = if (isTablet) 44.dp else 36.dp

    Box {
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

            // Marker con long press para popup
            StyledToolButtonWithLongPress(
                icon = Icons.Default.Create,
                label = "Marker",
                selected = selectedTool == "marker",
                onClick = { onSelectTool("marker") },
                onLongClick = {
                    onSelectTool("marker")
                    showMarkerPopup = true
                },
                onPositioned = { rect ->
                    markerButtonRect = rect
                    onMarkerButtonPositioned(rect)
                },
                size = buttonSize,
                iconSize = iconSize
            )

            // Highlighter con long press para popup
            StyledToolButtonWithLongPress(
                icon = Icons.Default.BorderColor,
                label = "Highlighter",
                selected = selectedTool == "highlighter",
                onClick = { onSelectTool("highlighter") },
                onLongClick = {
                    onSelectTool("highlighter")
                    showHighlighterPopup = true
                },
                onPositioned = { rect ->
                    highlighterButtonRect = rect
                    onHighlighterButtonPositioned(rect)
                },
                size = buttonSize,
                iconSize = iconSize
            )

            // Eraser con long press para popup
            StyledToolButtonWithLongPress(
                icon = Icons.Default.Delete,
                label = s.toolErase,
                selected = selectedTool == "eraser",
                onClick = { onSelectTool("eraser") },
                onLongClick = {
                    onSelectTool("eraser")
                    showEraserPopup = true
                },
                onPositioned = { rect -> eraserButtonRect = rect },
                size = buttonSize,
                iconSize = iconSize
            )

            // Botón de Revertir/Deshacer ubicado justo debajo del borrador
            // Botón de Revertir/Deshacer siempre visible, deshabilitado si no hay undo
            StyledToolButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = s.toolUndo,
                selected = false,
                onClick = onUndo,
                size = buttonSize,
                iconSize = iconSize,
                enabled = hasUndo
            )

            // Botón del Stylus - solo visible si se ha detectado un stylus
            if (stylusDetected) {
                StyledToolButtonWithLongPress(
                    icon = Icons.Default.Gesture,
                    label = "Stylus Button",
                    selected = false,
                    onClick = {
                        Log.d("PdfToolbar", "Botón Stylus UI CLICKEADO - llamando onStylusButtonClick")
                        onStylusButtonClick()
                    },
                    onLongClick = { showStylusToolDialog = true }, // Long press: abre menú de selección
                    size = buttonSize,
                    iconSize = iconSize
                )
            }

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
                    // Color Circle with Border
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) colorSize + 8.dp else colorSize)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f), // Subtle border for unselected
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
                    val sliderLength = this.maxHeight - 16.dp
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

            // --- Settings Button (Bottom) ---
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                 modifier = Modifier.width(40.dp),
                 color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(8.dp))
            
            StyledToolButton(
                icon = Icons.Default.Settings,
                label = s.editorSettings,
                selected = false,
                onClick = onShowSettings,
                size = buttonSize,
                iconSize = iconSize,
                enabled = true
            )
        }
    }

    // Diálogo de selección de herramienta para el botón del stylus
    if (showStylusToolDialog) {
        AlertDialog(
            onDismissRequest = { showStylusToolDialog = false },
            title = { Text(s.stylusButtonTool) },
            text = {
                Column {
                    // Mostramos todas las herramientas incluyendo UNDO
                    listOf(StylusTool.MARKER, StylusTool.HIGHLIGHTER, StylusTool.ERASER, StylusTool.UNDO).forEach { tool ->
                        val toolLabel = when (tool) {
                            StylusTool.MARKER -> "Marker"
                            StylusTool.HIGHLIGHTER -> "Highlighter"
                            StylusTool.ERASER -> s.toolErase
                            StylusTool.UNDO -> "Undo"
                            else -> ""
                        }
                        val isSelected = stylusButtonTool == tool
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onStylusButtonToolChange(tool)
                                    showStylusToolDialog = false
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onStylusButtonToolChange(tool)
                                        showStylusToolDialog = false
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(toolLabel)
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // Opción para habilitar/deshabilitar presión capacitiva
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEnableStylusPressureChange(!enableStylusPressure) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableStylusPressure,
                            onCheckedChange = { onEnableStylusPressureChange(it) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Use pressure sensitivity")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStylusToolDialog = false }) {
                    Text(s.cancel)
                }
            }
        )
    }

        // Popup para ajustar tamaño del Marker
        if (showMarkerPopup) {
            ToolSizePopup(
                toolName = "Marker",
                currentSize = markerStrokeWidth,
                minSize = 0.003f,
                maxSize = 0.02f,
                onSizeChange = onMarkerStrokeChange,
                onDismiss = { showMarkerPopup = false },
                buttonRect = markerButtonRect,
                previewColor = paletteColors.getOrNull(selectedPaletteIndex) ?: Color.Black,
                isHighlighter = false
            )
        }

        // Popup para ajustar tamaño del Highlighter
        if (showHighlighterPopup) {
            ToolSizePopup(
                toolName = "Highlighter",
                currentSize = highlighterStrokeWidth,
                minSize = 0.01f,
                maxSize = 0.08f,
                onSizeChange = onHighlighterStrokeChange,
                onDismiss = { showHighlighterPopup = false },
                buttonRect = highlighterButtonRect,
                previewColor = paletteColors.getOrNull(selectedPaletteIndex) ?: Color.Yellow,
                isHighlighter = true
            )
        }

        // Popup para ajustar tamaño del Eraser
        if (showEraserPopup) {
            ToolSizePopup(
                toolName = s.toolErase,
                currentSize = eraserRadiusNorm,
                minSize = 0.015f,
                maxSize = 0.1f,
                onSizeChange = onEraserRadiusChange,
                onDismiss = { showEraserPopup = false },
                buttonRect = eraserButtonRect,
                previewColor = MaterialTheme.colorScheme.error,
                isHighlighter = false,
                isEraser = true
            )
        }
    }
}

/**
 * Popup para ajustar el tamaño de una herramienta
 */
@Composable
fun ToolSizePopup(
    toolName: String,
    currentSize: Float,
    minSize: Float,
    maxSize: Float,
    onSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    buttonRect: Rect,
    previewColor: Color,
    isHighlighter: Boolean = false,
    isEraser: Boolean = false
) {
    val density = LocalDensity.current

    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        offset = with(density) {
            IntOffset(
                x = buttonRect.right.toInt() + 16.dp.toPx().toInt(),
                y = buttonRect.top.toInt() - 40.dp.toPx().toInt()
            )
        }
    ) {
        Surface(
            modifier = Modifier
                .width(200.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = toolName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(12.dp))

                // Preview del trazo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isEraser) {
                        // Preview del borrador como círculo
                        Canvas(modifier = Modifier.size(50.dp)) {
                            drawCircle(
                                color = previewColor.copy(alpha = 0.5f),
                                radius = currentSize * 500f
                            )
                        }
                    } else {
                        // Preview del trazo como línea curva
                        val displayColor = if (isHighlighter) previewColor.copy(alpha = 0.5f) else previewColor
                        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            val path = Path().apply {
                                moveTo(0f, size.height * 0.5f)
                                quadraticBezierTo(
                                    size.width * 0.25f, size.height * 0.2f,
                                    size.width * 0.5f, size.height * 0.5f
                                )
                                quadraticBezierTo(
                                    size.width * 0.75f, size.height * 0.8f,
                                    size.width, size.height * 0.5f
                                )
                            }
                            drawPath(
                                path = path,
                                color = displayColor,
                                style = Stroke(
                                    width = currentSize * 1000f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Slider para el tamaño
                Slider(
                    value = currentSize,
                    onValueChange = onSizeChange,
                    valueRange = minSize..maxSize,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // Botón para cerrar
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("OK")
                }
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
    onPositioned: (Rect) -> Unit = {},
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
            )
            .onGloballyPositioned { coordinates ->
                onPositioned(coordinates.boundsInRoot())
            },
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
