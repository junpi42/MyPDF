package com.example.mypdf

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    penColor: Color,
    strokeWidth: Float,
    smoothingEnabled: Boolean,
    onPathAdded: (DrawingPath) -> Unit,
    onErase: (List<Offset>) -> Unit,
    darkMode: Boolean
) {
    val currentPath = remember { mutableStateListOf<Offset>() }
    var canvasW by remember { mutableStateOf(0f) }
    var canvasH by remember { mutableStateOf(0f) }
    var redrawTrigger by remember { mutableStateOf(0) }

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
                val q = Offset(0.75f * p0.x + 0.25f * p1.x, 0.75f * p0.y + 0.25f * p1.y)
                val r = Offset(0.25f * p0.x + 0.75f * p1.x, 0.25f * p0.y + 0.75f * p1.y)
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
        for (i in 1 until points.size) {
            val p = points[i]
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val width = maxX - minX
        val height = maxY - minY
        return width < 0.03f && height < 0.03f
    }

    if (bitmap != null) {
        val pageBg = if (darkMode) Color(0xFF303030) else MaterialTheme.colorScheme.surface
        val canvasBg = if (darkMode) Color(0xFFFAFAFA) else MaterialTheme.colorScheme.surface

        Box(
            Modifier
                .fillMaxWidth()
                .background(pageBg)
                .padding(8.dp)
        ) {
            key(redrawTrigger) {
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
                            if (editMode && (selectedTool == "pen" || selectedTool == "eraser")) {
                                Modifier.pointerInput(
                                    selectedTool, smoothingEnabled, strokeWidth, canvasW, canvasH
                                ) {
                                    awaitEachGesture {
                                        if (canvasW <= 0f || canvasH <= 0f) return@awaitEachGesture
                                        currentPath.clear()

                                        val down = awaitFirstDown()
                                        currentPath.add(toNorm(down.position))

                                        drag(down.id) { change ->
                                            change.consume()

                                            currentPath.add(toNorm(change.position))

                                            if (selectedTool == "eraser") {
                                                onErase(currentPath.toList())
                                                redrawTrigger++
                                            }
                                        }

                                        if (currentPath.isNotEmpty()) {
                                            val basePoints = currentPath.toList()
                                            val tiny = isTinyStroke(basePoints)
                                            val final =
                                                if (smoothingEnabled && selectedTool == "pen" && !tiny && basePoints.size > 1) {
                                                    smoothPath(basePoints, iterations = 1)
                                                } else basePoints

                                            if (selectedTool == "eraser") {
                                                onErase(final)
                                            } else {
                                                onPathAdded(
                                                    DrawingPath(
                                                        final, penColor, strokeWidth, isEraser = false
                                                    )
                                                )
                                            }
                                        }
                                        currentPath.clear()
                                    }
                                }
                            } else Modifier
                        )
                ) {
                    drawImage(
                        bitmap.asImageBitmap(),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )

                    annotations.paths.forEach { p ->
                        when {
                            p.points.size > 1 -> {
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
                            }
                            p.points.size == 1 -> {
                                val pp = toPx(p.points.first())
                                drawCircle(
                                    color = p.color,
                                    radius = (p.strokeWidth * size.width) / 2f,
                                    center = pp
                                )
                            }
                        }
                    }

                    if (selectedTool == "pen" && currentPath.isNotEmpty()) {
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
                                color = penColor,
                                style = Stroke(width = strokeWidth * size.width)
                            )
                        } else {
                            val pp = toPx(currentPath.first())
                            drawCircle(
                                color = penColor,
                                radius = (strokeWidth * size.width) / 2f,
                                center = pp
                            )
                        }
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
