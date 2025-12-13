package com.example.mypdf

import android.graphics.Bitmap
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
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
    darkMode: Boolean
) {
    val currentPath = remember { mutableStateListOf<Offset>() }
    var canvasW by remember { mutableStateOf(0f) }
    var canvasH by remember { mutableStateOf(0f) }

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
            // Removed key(redrawTrigger) to prevent full Canvas recreation on every drag event
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .background(canvasBg)
                    .onSizeChanged { newSize ->
                        canvasW = newSize.width.toFloat()
                        canvasH = newSize.height.toFloat()
                    }
                    .then(
                        if (editMode && (selectedTool == "marker" || selectedTool == "eraser" || selectedTool == "highlighter")) {
                            Modifier.pointerInput(
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

                                    if (selectedTool == "eraser") {
                                        eraserCenter = lastNorm
                                    }

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: break

                                        if (!change.pressed) break

                                        if (change.position != change.previousPosition) {
                                            val newNorm = toNorm(change.position)
                                            currentPath.add(newNorm)

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
                                            val tiny = isTinyStroke(basePoints)

                                            val final = if (
                                                smoothingEnabled &&
                                                (selectedTool == "marker" || selectedTool == "highlighter") &&
                                                !tiny &&
                                                basePoints.size > 1
                                            ) {
                                                smoothPath(basePoints, 1)
                                            } else basePoints

                                            if (selectedTool == "eraser") {
                                                eraserCenter = final.lastOrNull()
                                                onErase(final)
                                            } else {
                                                onPathAdded(
                                                    DrawingPath(
                                                        final,
                                                        currentColor,
                                                        currentStrokeWidth,
                                                        isEraser = false
                                                    )
                                                )
                                            }
                                        }

                                        currentPath.clear()
                                        eraserCenter = null
                                    }
                                }
                            } else Modifier
                        )
                ) {
                    drawImage(
                        bitmap.asImageBitmap(),
                        dstSize = IntSize(
                            size.width.roundToInt(),
                            size.height.roundToInt()
                        )
                    )

                    // Dibujar anotaciones previas
                    annotations.paths.forEach { p ->
                        if (p.points.size > 1) {
                            val path = Path().apply {
                                val first = toPx(p.points.first())
                                moveTo(first.x, first.y)
                                p.points.drop(1).forEach { pt ->
                                    val pp = toPx(pt)
                                    lineTo(pp.x, pp.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = p.color,
                                style = Stroke(width = p.strokeWidth * size.width)
                            )
                        } else if (p.points.size == 1) {
                            val pp = toPx(p.points.first())
                            drawCircle(
                                color = p.color,
                                radius = (p.strokeWidth * size.width) / 2f,
                                center = pp
                            )
                        }
                    }

                    // Dibujar trazo actual
                    if ((selectedTool == "marker" || selectedTool == "highlighter") && currentPath.isNotEmpty()) {
                        val drawColor = if (selectedTool == "highlighter") currentColor.copy(alpha = 0.5f) else currentColor
                        
                        if (currentPath.size > 1) {
                            val path = Path().apply {
                                val first = toPx(currentPath.first())
                                moveTo(first.x, first.y)
                                currentPath.drop(1).forEach { pt ->
                                    val pp = toPx(pt)
                                    lineTo(pp.x, pp.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = drawColor,
                                style = Stroke(
                                    width = currentStrokeWidth * size.width,
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                        } else {
                            val pp = toPx(currentPath.first())
                            drawCircle(
                                color = drawColor,
                                radius = (currentStrokeWidth * size.width) / 2f,
                                center = pp
                            )
                        }
                    }

                    // Dibujar círculo del borrador
                    if (selectedTool == "eraser") {
                        eraserCenter?.let { centerNorm ->
                            val centerPx = toPx(centerNorm)
                            val radiusPx = eraserRadiusNorm * size.width

                            drawCircle(
                                color = Color.Red.copy(alpha = 0.5f),
                                radius = radiusPx,
                                center = centerPx,
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                }
            }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (showLoadingLabel) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
