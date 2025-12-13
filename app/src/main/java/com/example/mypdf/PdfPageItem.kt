package com.example.mypdf

import android.graphics.Bitmap
import android.util.Log
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Suppress("UNUSED_PARAMETER") // el parámetro index puede no usarse en composición; suprimimos la advertencia
@Composable
fun PdfPageItem(
    index: Int,
    bitmap: Bitmap?,
    showLoadingLabel: Boolean,
    editMode: Boolean,
    annotations: PageAnnotations,
    selectedTool: String,
    currentColor: Color,
    currentStrokeWidth: Float,
    eraserRadiusNorm: Float,
    smoothingEnabled: Boolean,
    onPathAdded: (DrawingPath) -> Unit,
    onErase: (List<Offset>) -> Unit,
    // Añadimos callbacks de inicio/fin de gesto con valores por defecto para compatibilidad
    onEraseStart: () -> Unit = {},
    onEraseEnd: () -> Unit = {},
    darkMode: Boolean,
    // Stylus parameters
    onStylusDetected: () -> Unit = {},
    onStylusButtonPressed: () -> Unit = {},
    enableStylusPressure: Boolean = true,
    onPressureUpdate: (Float) -> Unit = {} // Callback para presión en tiempo real
) {
    val currentPath = remember { mutableStateListOf<Offset>() }
    val currentPressures = remember { mutableStateListOf<Float>() } // Nueva lista para presiones
    var canvasW by remember { mutableFloatStateOf(0f) }
    var canvasH by remember { mutableFloatStateOf(0f) }

    var eraserCenter by remember { mutableStateOf<Offset?>(null) }

    fun toNorm(o: Offset): Offset =
        if (canvasW > 0f && canvasH > 0f) Offset(o.x / canvasW, o.y / canvasH) else Offset.Zero

    fun toPx(o: Offset): Offset = Offset(o.x * canvasW, o.y * canvasH)

    fun smoothPath(points: List<Offset>, iterations: Int = 1): List<Offset> {
        if (points.size < 3) return points
        var smoothed = points
        repeat(iterations) {
            val result = mutableListOf<Offset>()
            result.add(smoothed.first())
            for (i in 0 until smoothed.size - 1) {
                val p0 = smoothed[i]
                val p1 = smoothed[i + 1]
                val q = Offset(
                    0.75f * p0.x + 0.25f * p1.x,
                    0.75f * p0.y + 0.25f * p1.y
                )
                val r = Offset(
                    0.25f * p0.x + 0.75f * p1.x,
                    0.25f * p0.y + 0.75f * p1.y
                )
                result.add(q); result.add(r)
            }
            result.add(smoothed.last())
            smoothed = result
        }
        return smoothed
    }

    fun smoothPressures(pressures: List<Float>, iterations: Int = 1): List<Float> {
        if (pressures.size < 3) return pressures
        var smoothed = pressures
        repeat(iterations) {
            val result = mutableListOf<Float>()
            result.add(smoothed.first())
            for (i in 0 until smoothed.size - 1) {
                val p0 = smoothed[i]
                val p1 = smoothed[i + 1]
                val q = 0.75f * p0 + 0.25f * p1
                val r = 0.25f * p0 + 0.75f * p1
                result.add(q); result.add(r)
            }
            result.add(smoothed.last())
            smoothed = result
        }
        return smoothed
    }

    fun isTinyStroke(points: List<Offset>): Boolean {
        if (points.isEmpty()) return true
        var minX = points[0].x
        var maxX = points[0].x
        var minY = points[0].y
        var maxY = points[0].y
        for (i in points.indices) {
            val p = points[i]
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        return (maxX - minX) < 0.03f && (maxY - minY) < 0.03f
    }

    if (bitmap != null) {
        val pageBg = if (darkMode) Color(0xFF303030) else MaterialTheme.colorScheme.surface
        val canvasBg = if (darkMode) Color(0xFFFAFAFA) else MaterialTheme.colorScheme.surface

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = MaterialTheme.shapes.small,
            shadowElevation = 4.dp,
            color = pageBg
        ) {
            // Estado para rastrear si el stylus fue detectado y si el botón estaba presionado
            var stylusWasDetected by remember { mutableStateOf(false) }
            var stylusButtonWasPressed by remember { mutableStateOf(false) }

            // Construimos el modifier en dos pasos para mantener el código legible
            val canvasBaseModifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                .background(canvasBg)
                .onSizeChanged { newSize ->
                    canvasW = newSize.width.toFloat()
                    canvasH = newSize.height.toFloat()
                }
                // Interceptar eventos para detectar el stylus y su botón
                .pointerInteropFilter { event ->
                    // Detectar si el evento viene de un stylus
                    if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
                        // Primera vez que se detecta un stylus
                        if (!stylusWasDetected) {
                            Log.d("PdfPageItem", "Stylus detectado por primera vez")
                            onStylusDetected()
                            @Suppress("UNUSED_VARIABLE")
                            stylusWasDetected = true
                        }

                        // Reportar presión en tiempo real
                        if (enableStylusPressure) {
                            val pressure = event.pressure.coerceIn(0f, 1f)
                            onPressureUpdate(pressure)
                        }

                        // Detectar botón del stylus (solo en ACTION_DOWN o ACTION_MOVE)
                        val buttonPressed = (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                                          (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0

                        // Detectar transición de no-presionado a presionado
                        if (buttonPressed && !stylusButtonWasPressed) {
                            Log.d("PdfPageItem", "Botón del stylus PRESIONADO - llamando onStylusButtonPressed")
                            onStylusButtonPressed()
                        }
                        @Suppress("UNUSED_VARIABLE")
                        stylusButtonWasPressed = buttonPressed
                    }
                    // Retornar false para no consumir el evento y permitir que continúe el procesamiento normal
                    false
                }

            val canvasModifier = if (editMode && (selectedTool == "marker" || selectedTool == "eraser" || selectedTool == "highlighter")) {
                canvasBaseModifier.pointerInput(
                    selectedTool,
                    smoothingEnabled,
                    currentStrokeWidth,
                    currentColor,
                    canvasW,
                    canvasH
                ) {
                    awaitEachGesture {
                        if (canvasW <= 0f || canvasH <= 0f) return@awaitEachGesture
                        currentPath.clear()

                        val down = awaitFirstDown()
                        var lastNorm = toNorm(down.position)

                        currentPath.add(lastNorm)
                        // Capturar presión inicial (normalizada entre 0 y 1)
                        val initialPressure = if (enableStylusPressure) down.pressure.coerceIn(0f, 1f) else 1f
                        currentPressures.add(initialPressure)
                        if (enableStylusPressure) onPressureUpdate(initialPressure)

                        if (selectedTool == "eraser") {
                            eraserCenter = lastNorm
                            onEraseStart()
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.position != change.previousPosition) {
                                val newNorm = toNorm(change.position)
                                currentPath.add(newNorm)
                                // Capturar presión en cada movimiento
                                val pressure = if (enableStylusPressure) change.pressure.coerceIn(0f, 1f) else 1f
                                currentPressures.add(pressure)
                                // Reportar presión en tiempo real
                                if (enableStylusPressure) onPressureUpdate(pressure)
                                if (selectedTool == "eraser") {
                                    eraserCenter = newNorm
                                    onErase(listOf(lastNorm, newNorm))
                                }
                                lastNorm = newNorm
                                change.consume()
                            }
                        }

                        if (currentPath.isNotEmpty()) {
                            val basePoints = currentPath.toList()
                            val basePressures = currentPressures.toList()
                            val tiny = isTinyStroke(basePoints)
                            val final = if (
                                smoothingEnabled &&
                                (selectedTool == "marker" || selectedTool == "highlighter") &&
                                !tiny &&
                                basePoints.size > 1
                            ) {
                                smoothPath(basePoints, 1)
                            } else basePoints

                            // Aplicar suavizado a las presiones también
                            val finalPressures = if (
                                smoothingEnabled &&
                                (selectedTool == "marker" || selectedTool == "highlighter") &&
                                !tiny &&
                                basePoints.size > 1
                            ) {
                                smoothPressures(basePressures, 1)
                            } else basePressures

                            if (selectedTool == "eraser") {
                                eraserCenter = final.lastOrNull()
                                onErase(final)
                                onEraseEnd()
                            } else {
                                onPathAdded(DrawingPath(final, currentColor, currentStrokeWidth, isEraser = false, pressures = finalPressures))
                            }
                        }

                        currentPath.clear()
                        currentPressures.clear()
                        eraserCenter = null
                    }
                }
            } else canvasBaseModifier

            Canvas(modifier = canvasModifier) {
                // Dibujo en el Canvas
                drawImage(bitmap.asImageBitmap(), dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))

                // Dibujar anotaciones previas
                annotations.paths.forEach { p ->
                    if (p.points.size > 1) {
                        // Si hay información de presión, dibujar con variación de ancho
                        if (enableStylusPressure && p.pressures.size == p.points.size) {
                            // Dibujar segmento por segmento con ancho variable según presión
                            // A 60% presión se usa el tamaño actual
                            // Rango: 0.3x (presión 0%) a 1.7x (presión 100%)
                            for (i in 0 until p.points.size - 1) {
                                val pt1 = toPx(p.points[i])
                                val pt2 = toPx(p.points[i + 1])
                                val pressure = p.pressures[i + 1].coerceIn(0f, 1f)
                                val pressureFactor = 0.3f + (pressure * 1.4f) // 0.3 a 1.7
                                val width = (p.strokeWidth * size.width) * pressureFactor
                                drawPath(
                                    path = Path().apply { moveTo(pt1.x, pt1.y); lineTo(pt2.x, pt2.y) },
                                    color = p.color,
                                    style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        } else {
                            // Dibujar normal sin variación de presión
                            val path = Path().apply {
                                val first = toPx(p.points.first())
                                moveTo(first.x, first.y)
                                p.points.drop(1).forEach { pt ->
                                    val pp = toPx(pt)
                                    lineTo(pp.x, pp.y)
                                }
                            }
                            drawPath(path = path, color = p.color, style = Stroke(width = p.strokeWidth * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    } else if (p.points.size == 1) {
                        val pp = toPx(p.points.first())
                        val pressure = if (enableStylusPressure && p.pressures.isNotEmpty()) p.pressures[0].coerceIn(0f, 1f) else 1f
                        val pressureFactor = 0.3f + (pressure * 1.4f)
                        val radius = (p.strokeWidth * size.width / 2f) * pressureFactor
                        drawCircle(color = p.color, radius = radius, center = pp)
                    }
                }

                // Dibujar trazo actual
                if ((selectedTool == "marker" || selectedTool == "highlighter") && currentPath.isNotEmpty()) {
                    val drawColor = if (selectedTool == "highlighter") currentColor.copy(alpha = 0.5f) else currentColor

                    if (currentPath.size > 1) {
                        // Si hay presión disponible y está habilitada, dibujar con variación
                        if (enableStylusPressure && currentPressures.size == currentPath.size) {
                            // Dibujar segmento por segmento con presión variable
                            // A 60% presión se usa el tamaño actual
                            // Rango: 0.3x (presión 0%) a 1.7x (presión 100%)
                            for (i in 0 until currentPath.size - 1) {
                                val pt1 = toPx(currentPath[i])
                                val pt2 = toPx(currentPath[i + 1])
                                val pressure = currentPressures[i + 1].coerceIn(0f, 1f)
                                val pressureFactor = 0.3f + (pressure * 1.4f) // 0.3 a 1.7
                                val width = (currentStrokeWidth * size.width) * pressureFactor
                                drawPath(
                                    path = Path().apply { moveTo(pt1.x, pt1.y); lineTo(pt2.x, pt2.y) },
                                    color = drawColor,
                                    style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        } else {
                            // Dibujar normal sin presión
                            val path = Path().apply {
                                val first = toPx(currentPath.first())
                                moveTo(first.x, first.y)
                                currentPath.drop(1).forEach { pt ->
                                    val pp = toPx(pt)
                                    lineTo(pp.x, pp.y)
                                }
                            }
                            drawPath(path = path, color = drawColor, style = Stroke(width = currentStrokeWidth * size.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    } else {
                        val pp = toPx(currentPath.first())
                        drawCircle(color = drawColor, radius = (currentStrokeWidth * size.width) / 2f, center = pp)
                    }
                }

                // Dibujar borrador con soporte de presión
                if (selectedTool == "eraser" && currentPath.isNotEmpty()) {
                    if (currentPath.size > 1) {
                        // Borrador normal sin presión
                        for (i in 0 until currentPath.size - 1) {
                            val pt = toPx(currentPath[i + 1])
                            val radius = eraserRadiusNorm * size.width / 2f
                            drawCircle(color = Color.Red.copy(alpha = 0.3f), radius = radius, center = pt)
                        }
                    }
                }

                // Dibujar círculo del borrador
                if (selectedTool == "eraser") {
                    eraserCenter?.let { centerNorm ->
                        val centerPx = toPx(centerNorm)
                        val radiusPx = eraserRadiusNorm * size.width
                        drawCircle(color = Color.Red.copy(alpha = 0.5f), radius = radiusPx, center = centerPx, style = Stroke(width = 4f))
                    }
                }
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().height(200.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (showLoadingLabel) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            }
        }
    }
}
