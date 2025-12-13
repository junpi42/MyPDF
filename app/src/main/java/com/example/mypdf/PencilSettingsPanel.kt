package com.example.mypdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.BorderColor
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Panel flotante de ajustes de herramienta de dibujo (lápiz/marker/highlighter).
 * Se muestra al lado de la barra de herramientas cuando el usuario hace long press
 * sobre el botón del lápiz o highlighter.
 *
 * @param visible Si el panel está visible
 * @param toolType Tipo de herramienta: "marker" o "highlighter"
 * @param paletteColors Lista de colores disponibles en la paleta
 * @param selectedColorIndex Índice del color actualmente seleccionado
 * @param strokeWidth Grosor actual del trazo (normalizado)
 * @param minStrokeWidth Valor mínimo del slider de grosor
 * @param maxStrokeWidth Valor máximo del slider de grosor
 * @param onColorSelected Callback cuando se selecciona un color
 * @param onStrokeWidthChanged Callback cuando se cambia el grosor
 * @param onDismiss Callback para cerrar el panel
 * @param modifier Modifier opcional
 */
@Composable
fun PencilSettingsPanel(
    visible: Boolean,
    toolType: String,
    paletteColors: List<Color>,
    selectedColorIndex: Int,
    strokeWidth: Float,
    minStrokeWidth: Float,
    maxStrokeWidth: Float,
    onColorSelected: (Int) -> Unit,
    onStrokeWidthChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .width(100.dp)
                .wrapContentHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cabecera con icono
                Icon(
                    imageVector = if (toolType == "highlighter") Icons.Default.BorderColor else Icons.Default.Create,
                    contentDescription = if (toolType == "highlighter") "Highlighter" else "Marker",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.width(60.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Selector de colores compacto
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    paletteColors.forEachIndexed { index, color ->
                        val isSelected = selectedColorIndex == index
                        val colorSize = if (isSelected) 36.dp else 30.dp
                        
                        Box(
                            modifier = Modifier
                                .size(colorSize)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable { onColorSelected(index) }
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.width(60.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // Preview del trazo
                val currentColor = paletteColors.getOrNull(selectedColorIndex) ?: Color.Black
                val previewColor = if (toolType == "highlighter") currentColor.copy(alpha = 0.5f) else currentColor
                
                Box(
                    modifier = Modifier
                        .size(width = 70.dp, height = 50.dp)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            moveTo(0f, size.height * 0.5f)
                            quadraticBezierTo(
                                size.width * 0.3f, size.height * 0.2f,
                                size.width * 0.5f, size.height * 0.5f
                            )
                            quadraticBezierTo(
                                size.width * 0.7f, size.height * 0.8f,
                                size.width, size.height * 0.5f
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

                // Slider horizontal de grosor
                Text(
                    text = strings().strokeThicknessLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Slider(
                    value = strokeWidth,
                    onValueChange = onStrokeWidthChanged,
                    valueRange = minStrokeWidth..maxStrokeWidth,
                    modifier = Modifier
                        .width(80.dp)
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
