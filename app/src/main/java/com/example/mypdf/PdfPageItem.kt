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
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
    darkMode: Boolean
) {
    val currentPath = remember { mutableStateListOf<Offset>() }
    var canvasW by remember { mutableStateOf(0f) }
    var canvasH by remember { mutableStateOf(0f) }

    // Cache para mejorar rendimiento
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val drawScope = remember { CanvasDrawScope() }
    var cachedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    // Trigger para forzar recomposición cuando actualizamos el bitmap manualmente
    var bitmapVersion by remember { mutableStateOf(0) }

    LaunchedEffect(canvasW, canvasH, annotations, redrawTrigger) {
        if (canvasW > 0f && canvasH > 0f) {
            val bitmap = ImageBitmap(canvasW.toInt(), canvasH.toInt(), ImageBitmapConfig.Argb8888)
            val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
            val size = androidx.compose.ui.geometry.Size(canvasW, canvasH)

            drawScope.draw(
                density = density,
                layoutDirection = layoutDirection,
                canvas = canvas,
                size = size
            ) {
                annotations.paths.forEach { p ->
                    val blendMode = if (p.isEraser) androidx.compose.ui.graphics.BlendMode.Clear else androidx.compose.ui.graphics.BlendMode.SrcOver
                    val color = if (p.isEraser) Color.Transparent else p.color
                    
                    fun toPx(o: Offset): Offset = Offset(o.x * size.width, o.y * size.height)

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
                                color = color,
                                style = Stroke(width = p.strokeWidth * size.width),
                                blendMode = blendMode
                            )
                        }
                        p.points.size == 1 -> {
                            val pp = toPx(p.points.first())
                            drawCircle(
                                color = color,
                                radius = (p.strokeWidth * size.width) / 2f,
                                center = pp,
                                blendMode = blendMode
                            )
                        }
                    }
                }
            }
            cachedBitmap = bitmap
            bitmapVersion++
        }
    }

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

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = MaterialTheme.shapes.small,
            shadowElevation = 4.dp,
            color = pageBg
        ) {
            // We use a Box to layer the bitmap and the drawing canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .background(canvasBg)
                    .onSizeChanged { newSize ->
                        canvasW = newSize.width.toFloat()
                        canvasH = newSize.height.toFloat()
                    }
                    // Input handling layer
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
                                    }

                                    if (currentPath.isNotEmpty()) {
                                        val basePoints = currentPath.toList()
                                        val tiny = isTinyStroke(basePoints)
                                        // Smooth only if it's a pen and not tiny
                                        val final =
                                            if (smoothingEnabled && selectedTool == "pen" && !tiny && basePoints.size > 1) {
                                                smoothPath(basePoints, iterations = 1)
                                            } else basePoints

                                        val isEraser = selectedTool == "eraser"
                                        val effectiveStrokeWidth = if (isEraser) strokeWidth * 8f else strokeWidth
                                        val newPath = DrawingPath(
                                            points = final,
                                            color = if (isEraser) Color.Transparent else penColor,
                                            strokeWidth = effectiveStrokeWidth,
                                            isEraser = isEraser
                                        )
                                        onPathAdded(newPath)

                                        // Actualizar cache inmediatamente para evitar parpadeo
                                        val bmp = cachedBitmap
                                        if (bmp != null) {
                                            val cvs = androidx.compose.ui.graphics.Canvas(bmp)
                                            val size = androidx.compose.ui.geometry.Size(canvasW, canvasH)
                                            drawScope.draw(
                                                density = density,
                                                layoutDirection = layoutDirection,
                                                canvas = cvs,
                                                size = size
                                            ) {
                                                val p = newPath
                                                val blendMode = if (p.isEraser) androidx.compose.ui.graphics.BlendMode.Clear else androidx.compose.ui.graphics.BlendMode.SrcOver
                                                val color = if (p.isEraser) Color.Transparent else p.color
                                                
                                                fun toPx(o: Offset): Offset = Offset(o.x * size.width, o.y * size.height)

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
                                                            color = color,
                                                            style = Stroke(width = p.strokeWidth * size.width),
                                                            blendMode = blendMode
                                                        )
                                                    }
                                                    p.points.size == 1 -> {
                                                        val pp = toPx(p.points.first())
                                                        drawCircle(
                                                            color = color,
                                                            radius = (p.strokeWidth * size.width) / 2f,
                                                            center = pp,
                                                            blendMode = blendMode
                                                        )
                                                    }
                                                }
                                            }
                                            bitmapVersion++
                                        }
                                    }
                                    currentPath.clear()
                                }
                            }
                        } else Modifier
                    )
            ) {
                // 1. Draw the PDF Bitmap
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawImage(
                        bitmap.asImageBitmap(),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    )
                }

                // 2. Draw Annotations (Offscreen layer for blending)
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            // This is CRITICAL: It creates an offscreen buffer.
                            // BlendMode.Clear will clear pixels in this buffer (transparency),
                            // revealing the PDF bitmap drawn below in the parent Box.
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                ) {
                    // Draw saved paths from cache
                    // Usamos bitmapVersion para forzar recomposición cuando el bitmap cambia internamente
                    key(bitmapVersion) {
                        cachedBitmap?.let {
                            drawImage(it)
                        }
                    }

                    // Draw current path being drawn
                    if (currentPath.isNotEmpty()) {
                        val isEraser = selectedTool == "eraser"
                        val effectiveStrokeWidth = if (isEraser) strokeWidth * 8f else strokeWidth
                        val blendMode = if (isEraser) androidx.compose.ui.graphics.BlendMode.Clear else androidx.compose.ui.graphics.BlendMode.SrcOver
                        val color = if (isEraser) Color.Transparent else penColor

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
                                color = color,
                                style = Stroke(width = effectiveStrokeWidth * size.width),
                                blendMode = blendMode
                            )
                        } else {
                            val pp = toPx(currentPath.first())
                            drawCircle(
                                color = color,
                                radius = (effectiveStrokeWidth * size.width) / 2f,
                                center = pp,
                                blendMode = blendMode
                            )
                        }

                        // Cursor visual para la goma: círculo gris translúcido del tamaño exacto
                        if (isEraser) {
                            val lastPoint = toPx(currentPath.last())
                            val baseRadius = (effectiveStrokeWidth * size.width) / 2f
                            
                            // Círculo de relleno
                            drawCircle(
                                color = Color.Gray.copy(alpha = 0.4f),
                                radius = baseRadius,
                                center = lastPoint,
                                blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver
                            )
                            // Borde fino para mayor precisión
                            drawCircle(
                                color = Color.DarkGray.copy(alpha = 0.8f),
                                radius = baseRadius,
                                center = lastPoint,
                                style = Stroke(width = 1.dp.toPx()),
                                blendMode = androidx.compose.ui.graphics.BlendMode.SrcOver
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
