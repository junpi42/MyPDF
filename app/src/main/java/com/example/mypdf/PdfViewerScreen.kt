package com.example.mypdf

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector


import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap

private const val TAG = "PDF_TIMING"

data class DrawingPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

data class PageAnnotations(
    val pageIndex: Int,
    val paths: MutableList<DrawingPath> = mutableListOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    file: File,
    onBack: () -> Unit,
    isDarkMode: Boolean
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    val tunner = remember { AudioTuner() }

    // ===== MODO DIA / NOCHE =====
    val darkMode = isDarkMode

    var selectedTool by remember { mutableStateOf("none") }
    var penColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableFloatStateOf(0.006f) }
    var smoothingEnabled by remember { mutableStateOf(true) }
    var showColorPicker by remember { mutableStateOf(false) }

    // afinador
    var tunerOn by remember { mutableStateOf(false) }
    var concertModeOn by remember { mutableStateOf(false) }
    var showTunerSettings by remember { mutableStateOf(false) }

    // zoom / pan
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isPinching by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        tunerOn = granted
        if (!granted) runCatching { tunner.stopTuning(clearState = false) }
    }

    fun ensureMicPermission(onGranted: () -> Unit) {
        val ok = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) onGranted() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(tunerOn) {
        if (tunerOn) {
            val ok = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!ok) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                tunerOn = false
            } else {
                try {
                    tunner.startTuning(scope)
                } catch (e: Throwable) {
                    Log.w(TAG, "Error starting tuner: ${e.message}")
                    tunerOn = false
                }
            }
        } else {
            runCatching { tunner.stopTuning(clearState = false) }
        }
    }

    // status bar según modo
    DisposableEffect(darkMode) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !darkMode
        activity.window.statusBarColor =
            (if (darkMode) Color.Black else Color(0xFF111111)).toArgb()

        onDispose {
            runCatching { tunner.stopTuning(clearState = false) }
        }
    }

    // ===== PDF / renderizado =====
    val holder = remember(file.path) { PdfRendererHolder(file) }
    val pageCount = holder.pageCount
    val pageBitmaps = remember {
        mutableStateListOf<Bitmap?>().apply {
            repeat(pageCount) { add(null) }
        }
    }

    val config = LocalConfiguration.current
    val screenWidthPx =
        (config.screenWidthDp * context.resources.displayMetrics.density).toInt()
    val maxTargetWidthPx = 1920

    // precarga rápida
    LaunchedEffect(pageCount, screenWidthPx) {
        if (pageCount <= 0 || screenWidthPx <= 0) return@LaunchedEffect
        val effectiveWidth = min(screenWidthPx, maxTargetWidthPx)
        val quickW = min(effectiveWidth, 600)
        val boot = min(3, pageCount)
        val sem = Semaphore(1)
        coroutineScope {
            repeat(boot) { i ->
                if (pageBitmaps[i] == null) {
                    launch {
                        sem.withPermit {
                            val bmp = withContext(Dispatchers.Default) {
                                holder.renderPageQuick(i, quickW)
                            }
                            if (bmp != null) pageBitmaps[i] = bmp
                        }
                    }
                }
            }
        }
    }

    val listState = rememberLazyListState()

    // render hi-res bajo demanda
    LaunchedEffect(listState, pageCount, screenWidthPx) {
        if (pageCount <= 0 || screenWidthPx <= 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collectLatest { visibles ->
                if (visibles.isEmpty()) return@collectLatest
                val hiW = min(screenWidthPx, maxTargetWidthPx)
                visibles.take(2).forEach { idx ->
                    launch {
                        val bmp = withContext(Dispatchers.Default) {
                            holder.renderPageToWidth(idx, hiW)
                        }
                        bmp?.let {
                            if (idx in 0 until pageCount) {
                                val prev = pageBitmaps[idx]
                                if (prev == null || prev.width < it.width * 0.9f) {
                                    pageBitmaps[idx] = it
                                    if (prev != null && !prev.isRecycled) prev.recycle()
                                }
                            }
                        }
                    }
                }
            }
    }

    // ===== anotaciones =====
    val annotations = remember { mutableMapOf<Int, PageAnnotations>() }
    val annFile = remember(file.path) {
        File(file.parentFile, file.nameWithoutExtension + ".ann.json")
    }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    val scheduleSave: () -> Unit = {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(400)
            runCatching {
                val pages = JSONArray()
                annotations.toSortedMap().forEach { (idx, page) ->
                    val jPaths = JSONArray()
                    page.paths.forEach { p ->
                        val pts = JSONArray().also { arr ->
                            p.points.forEach { o ->
                                arr.put(JSONArray().put(o.x).put(o.y))
                            }
                        }
                        jPaths.put(
                            JSONObject()
                                .put("e", p.isEraser)
                                .put("c", p.color.toArgb())
                                .put("w", p.strokeWidth)
                                .put("pts", pts)
                        )
                    }
                    pages.put(JSONObject().put("index", idx).put("paths", jPaths))
                }
                val root = JSONObject().put("version", 1).put("pages", pages)
                val tmp = File(annFile.parentFile, annFile.name + ".tmp")
                FileOutputStream(tmp).use {
                    it.write(root.toString().toByteArray(Charsets.UTF_8))
                }
                if (annFile.exists()) annFile.delete()
                tmp.renameTo(annFile)
            }
        }
    }

    // cargar anotaciones
    LaunchedEffect(annFile.path) {
        withContext(Dispatchers.IO) {
            if (!annFile.exists()) return@withContext
            runCatching {
                val text = FileInputStream(annFile).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                val root = JSONObject(text)
                val pages = root.optJSONArray("pages") ?: JSONArray()
                val loaded = mutableMapOf<Int, PageAnnotations>()
                for (i in 0 until pages.length()) {
                    val jp = pages.getJSONObject(i)
                    val idx = jp.optInt("index", i)
                    val jPaths = jp.optJSONArray("paths") ?: JSONArray()
                    val list = mutableListOf<DrawingPath>()
                    for (k in 0 until jPaths.length()) {
                        val jpath = jPaths.getJSONObject(k)
                        val w = jpath.optDouble("w", 0.006).toFloat()
                        val colorInt = jpath.optInt("c", 0xFF000000.toInt())
                        val isE = jpath.optBoolean("e", false)
                        val ptsArr = jpath.optJSONArray("pts") ?: JSONArray()
                        val pts = mutableListOf<Offset>()
                        for (pIdx in 0 until ptsArr.length()) {
                            val pair = ptsArr.getJSONArray(pIdx)
                            pts.add(
                                Offset(
                                    pair.optDouble(0, 0.0).toFloat(),
                                    pair.optDouble(1, 0.0).toFloat()
                                )
                            )
                        }
                        list.add(DrawingPath(pts, Color(colorInt), w, isE))
                    }
                    loaded[idx] = PageAnnotations(idx, list)
                }
                withContext(Dispatchers.Main) {
                    annotations.clear()
                    annotations.putAll(loaded)
                }
            }
        }
    }

    val topBarHeight = 64.dp
    val screenBg = if (darkMode) Color(0xFF0E0F12) else Color(0xFFF5F6F8)

    Surface(modifier = Modifier.fillMaxSize(), color = screenBg) {
        Box(Modifier.fillMaxSize()) {

            // ===== contenido PDF con zoom/pan =====
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarHeight)
                    .then(
                        if (selectedTool == "none") {
                            Modifier.pointerInput(selectedTool) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    // si hay zoom diferente de 1, consideramos pinch
                                    isPinching = zoom != 1f

                                    val newScale = (scale * zoom).coerceIn(1f, 4f)

                                    if (kotlin.math.abs(newScale - scale) > 0.001f) {
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f

                                        val focusX = (centroid.x - centerX - offsetX) / scale
                                        val focusY = (centroid.y - centerY - offsetY) / scale

                                        scale = newScale

                                        offsetX = centroid.x - centerX - focusX * scale
                                        offsetY = centroid.y - centerY - focusY * scale
                                    }

                                    if (scale > 1f) {
                                        val maxX = (size.width * (scale - 1f)) / 2f
                                        val maxY = (size.height * (scale - 1f)) / 2f
                                        offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                        offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                    } else {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                        } else Modifier
                    )
            ) {
                LazyColumn(
                    state = listState,
                    // si estás pellizcando, desactiva scroll para evitar saltos
                    userScrollEnabled = !isPinching,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(pageCount) { index ->
                        val bmp = pageBitmaps.getOrNull(index)
                        PdfPageItem(
                            index = index,
                            bitmap = bmp,
                            showLoadingLabel = bmp == null,
                            editMode = true,
                            annotations = annotations.getOrPut(index) { PageAnnotations(index) },
                            selectedTool = selectedTool,
                            penColor = penColor,
                            strokeWidth = strokeWidth,
                            smoothingEnabled = smoothingEnabled,
                            onPathAdded = { path ->
                                annotations.getOrPut(index) { PageAnnotations(index) }.paths.add(path)
                                scheduleSave()
                            },
                            onErase = { eraserPoints ->
                                val page = annotations.getOrPut(index) { PageAnnotations(index) }
                                val toRemove = mutableSetOf<Int>()
                                page.paths.forEachIndexed { pIdx, p ->
                                    val pts = p.points
                                    if (pts.size < 2) return@forEachIndexed
                                    var hit = false
                                    for (i in 0 until pts.size - 1) {
                                        val a = pts[i]; val b = pts[i + 1]
                                        if (eraserPoints.any { e ->
                                                distancePointToSegment(e, a, b) <= (strokeWidth * 100)
                                            }
                                        ) {
                                            hit = true; break
                                        }
                                    }
                                    if (hit) toRemove.add(pIdx)
                                }
                                if (toRemove.isNotEmpty()) {
                                    toRemove.sortedDescending()
                                        .forEach { page.paths.removeAt(it) }
                                    scheduleSave()
                                }
                            },
                            darkMode = darkMode
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            // ===== barra superior =====
            StyledTopBar(
                onBack = onBack,
                tunerOn = tunerOn,
                concertModeOn = concertModeOn,
                onTunerClick = {
                    if (!tunerOn) {
                        ensureMicPermission { tunerOn = true }
                    } else {
                        tunerOn = false

                    }
                },
                onConcertClick = { concertModeOn = !concertModeOn },
                darkMode = darkMode,

            )

            // banner afinador
            if (tunerOn) {
                TunnerSmall(
                    tunner = tunner,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = (topBarHeight - 44.dp) / 2)
                        .fillMaxWidth(0.7f)
                        .height(44.dp),
                    onClick = { showTunerSettings = true }
                )

            }

            // barra izquierda
            StyledLeftToolBar(
                selectedTool = selectedTool,
                penColor = penColor,
                strokeWidth = strokeWidth,
                smoothingEnabled = smoothingEnabled,
                onSelectTool = { selectedTool = it },
                onColorClick = { showColorPicker = true },
                onStrokeChange = { strokeWidth = it },
                onToggleSmoothing = { smoothingEnabled = !smoothingEnabled },
                onUndo = {
                    val currentPage = listState.firstVisibleItemIndex
                    annotations[currentPage]?.paths?.removeLastOrNull()
                    scheduleSave()
                },
                darkMode = darkMode,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = topBarHeight)
                    .fillMaxHeight()
            )

            if (showColorPicker) {
                ColorPickerDialog(
                    currentColor = penColor,
                    onColorSelected = { penColor = it },
                    onDismiss = { showColorPicker = false }
                )
            }

            if (showTunerSettings) {
                TunerSettingsDialog(
                    tuner = tunner,
                    onDismiss = { showTunerSettings = false }
                )
            }


        }
    }
}

// ================= utilidades =================

private fun distancePointToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ax = a.x; val ay = a.y; val bx = b.x; val by = b.y
    val vx = bx - ax; val vy = by - ay
    val wx = p.x - ax; val wy = p.y - ay
    val vv = vx * vx + vy * vy
    val t = if (vv > 0f) ((wx * vx + wy * vy) / vv).coerceIn(0f, 1f) else 0f
    val nx = ax + t * vx
    val ny = ay + t * vy
    val dx = p.x - nx
    val dy = p.y - ny
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

// ===== PDF HOLDER =====
private class PdfRendererHolder(file: File) {
    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(pfd)

    @Volatile private var closed = false
    private val renderMutex = Mutex()

    private val maxKb =
        (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceAtLeast(8 * 1024)

    private fun cacheKey(pageIndex: Int, targetWidth: Int): Int {
        val clamped = targetWidth.coerceIn(200, 1920)
        return (pageIndex shl 16) or (clamped and 0xFFFF)
    }

    private val cache = object : LruCache<Int, Bitmap>(maxKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    val pageCount: Int get() = renderer.pageCount

    suspend fun renderPageQuick(index: Int, quickTargetW: Int): Bitmap? =
        renderInternal(index, quickTargetW)

    suspend fun renderPageToWidth(index: Int, targetW: Int): Bitmap? =
        renderInternal(index, targetW)

    private suspend fun renderInternal(index: Int, targetW: Int): Bitmap? {
        if (closed) return null

        val clampedTargetW = targetW.coerceIn(200, 1920)
        val key = cacheKey(index, clampedTargetW)

        cache.get(key)?.let { return it }

        return try {
            renderMutex.withLock {
                if (closed) return null
                if (index !in 0 until renderer.pageCount) return null

                cache.get(key)?.let { return it }

                val pageLocal = renderer.openPage(index)
                pageLocal.use { page ->   // <- se cierra solo al salir
                    val srcW = page.width
                    val srcH = page.height

                    val scale = (clampedTargetW.toFloat() / srcW).coerceIn(0.1f, 8f)
                    val outW = (srcW * scale).toInt().coerceAtLeast(1)
                    val outH = (srcH * scale).toInt().coerceAtLeast(1)

                    val bitmap = createBitmap(outW, outH)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    cache.put(key, bitmap)
                    bitmap
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "render failed idx=$index w=$targetW: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }


    

    fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        runCatching { cache.evictAll() }
    }
}

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
    var canvasW by remember { mutableFloatStateOf(0f) }
    var canvasH by remember { mutableFloatStateOf(0f) }
    var redrawTrigger by remember { mutableIntStateOf(0) }

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

// ===== DIALOGO DE COLOR =====
@Composable
fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column {
                val colors = listOf(
                    Color.Red, Color.Blue, Color.Green, Color.Yellow,
                    Color.Black, Color.Magenta, Color.Cyan, Color(0xFFFF6B6B),
                    Color(0xFF4ECDC4), Color(0xFF95E1D3),
                    Color(0xFFF38181), Color(0xFFAA96DA)
                )
                colors.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { color ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = color,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable {
                                        onColorSelected(color)
                                        onDismiss()
                                    },
                                shadowElevation = if (color == currentColor) 4.dp else 0.dp,
                                border = if (color == currentColor)
                                    androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
                                else null
                            ) {}
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

// ===== MINI WIDGET AFINADOR =====
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TunnerSmall(
    tunner: AudioTuner,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val state by tunner.tuningState.collectAsState(initial = null)

    val bg = when (val s = state) {
        null -> Color.DarkGray
        else -> when {
            s.isInTune -> Color(0xFF4CAF50)
            s.centsOff > 10.0 -> Color(0xFFE53935)
            s.centsOff < -10.0 -> Color(0xFF1E88E5)
            else -> Color(0xFFFFA000)
        }
    }

    // Color de triángulos con contraste automático
    val markerColor = if (bg.luminance() > 0.5f) Color.Black else Color.White

    val cents = (state?.centsOff ?: 0.0).coerceIn(-50.0, 50.0)
    val normalized = (cents / 50.0).toFloat().coerceIn(-1f, 1f)

    // Animación suave del desplazamiento horizontal
    val animNorm by animateFloatAsState(
        targetValue = normalized,
        animationSpec = tween(durationMillis = 80),
        label = "tuner_triangles"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Canvas pegado al borde real (sin padding)
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            val centerX = w / 2f
            val range = w * 0.42f
            val x = centerX + animNorm * range

            val triW = h * 0.32f
            val triH = h * 0.30f

            val inTuneCents = 12.0
            val maxCents = 50.0
            val normLimit = (inTuneCents / maxCents).toFloat()

            val tickLen = h * 0.18f
            val tickStroke = h * 0.04f
            val halfStroke = tickStroke / 2f

            // Triángulo superior (base en borde)
            val topTri = Path().apply {
                moveTo(x - triW / 2f, -halfStroke)
                lineTo(x + triW / 2f, -halfStroke)
                lineTo(x, triH)
                close()
            }

            // Triángulo inferior (base en borde)
            val bottomTri = Path().apply {
                moveTo(x - triW / 2f, h + halfStroke)
                lineTo(x + triW / 2f, h + halfStroke)
                lineTo(x, h - triH)
                close()
            }

            drawPath(topTri, color = markerColor)
            drawPath(bottomTri, color = markerColor)

            // Líneas de zona afinada (pegadas a borde)
            val xLeft  = centerX - normLimit * range
            val xRight = centerX + normLimit * range

            // Arriba
            drawLine(
                color = markerColor,
                start = Offset(xLeft, -halfStroke),
                end   = Offset(xLeft, tickLen),
                strokeWidth = tickStroke
            )
            drawLine(
                color = markerColor,
                start = Offset(xRight, -halfStroke),
                end   = Offset(xRight, tickLen),
                strokeWidth = tickStroke
            )

            // Abajo
            drawLine(
                color = markerColor,
                start = Offset(xLeft, h + halfStroke),
                end   = Offset(xLeft, h - tickLen),
                strokeWidth = tickStroke
            )
            drawLine(
                color = markerColor,
                start = Offset(xRight, h + halfStroke),
                end   = Offset(xRight, h - tickLen),
                strokeWidth = tickStroke
            )
        }

        // Texto encima con padding SOLO para la nota
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val note = state?.targetNote
                ?.replace(Regex("\\d+"), "")
                ?: "--"

            Text(
                text = note,
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }

}


// ===== DIALOGO AJUSTES AFINADOR =====
@Composable
fun TunerSettingsDialog(
    tuner: AudioTuner,
    onDismiss: () -> Unit
) {
    val a4 by tuner.baseFrequency.collectAsState(initial = 442.0)
    val noisy by tuner.noisyEnvironment.collectAsState(initial = false)
    var daltonic by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustes del afinador") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { tuner.decrementBaseFrequency(1.0) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Menos")
                    }
                    Text(
                        text = "A4: ${a4.toInt()} Hz",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = { tuner.incrementBaseFrequency(1.0) }) {
                        Icon(Icons.Default.Add, contentDescription = "Más")
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = noisy, onCheckedChange = { tuner.setNoisyEnvironment(it) })
                    Spacer(Modifier.width(4.dp))
                    Text("Ambiente ruidoso")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = daltonic, onCheckedChange = { daltonic = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Daltonismo (próximamente)")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

// ===== AFINADOR DE AGUJA =====
@Composable
fun NeedleTunerOverlay(
    tunner: AudioTuner,
    modifier: Modifier = Modifier
) {
    val result by tunner.tuningState.collectAsState(initial = null)
    val cents = (result?.centsOff ?: 0.0).coerceIn(-50.0, 50.0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            tonalElevation = 8.dp,
            color = Color(0xE0222222),
            modifier = Modifier
                .fillMaxWidth(0.45f)
                .fillMaxHeight()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                val w = size.width
                val h = size.height
                val radius = min(w, h) * 0.85f
                val topMargin = 4.dp.toPx()
                val center = Offset(w / 2f, radius + topMargin)

                val startAngle = 200f
                val endAngle = 340f
                val centerAngle = 270f
                val sweepLeft = centerAngle - startAngle
                val sweepRight = endAngle - centerAngle

                drawArc(
                    color = Color(0xFF1E88E5),
                    startAngle = startAngle,
                    sweepAngle = sweepLeft,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )

                drawArc(
                    color = Color(0xFFE53935),
                    startAngle = centerAngle,
                    sweepAngle = sweepRight,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx())
                )

                val innerR = radius * 0.55f
                val outerR = radius * 0.98f
                val leftTriAngle = centerAngle - 6f
                val rightTriAngle = centerAngle + 6f

                fun polar(angleDeg: Float, r: Float): Offset {
                    val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
                    return Offset(
                        center.x + kotlin.math.cos(rad) * r,
                        center.y + kotlin.math.sin(rad) * r
                    )
                }

                val triPath = Path().apply {
                    moveTo(polar(centerAngle, innerR).x, polar(centerAngle, innerR).y)
                    lineTo(polar(leftTriAngle, outerR).x, polar(leftTriAngle, outerR).y)
                    lineTo(polar(rightTriAngle, outerR).x, polar(rightTriAngle, outerR).y)
                    close()
                }
                drawPath(triPath, color = Color(0xFF4CAF50))

                val normalized = (cents / 50.0).toFloat().coerceIn(-1f, 1f)
                val needleAngle = centerAngle + normalized * 45f
                val needleRad = Math.toRadians(needleAngle.toDouble()).toFloat()
                val needleLength = radius * 0.9f
                val end = Offset(
                    x = center.x + kotlin.math.cos(needleRad) * needleLength,
                    y = center.y + kotlin.math.sin(needleRad) * needleLength
                )

                drawLine(
                    color = Color(0xFFE53935),
                    start = center,
                    end = end,
                    strokeWidth = 4.dp.toPx()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledTopBar(
    onBack: () -> Unit,
    tunerOn: Boolean,
    concertModeOn: Boolean,
    onTunerClick: () -> Unit,
    onConcertClick: () -> Unit,
    darkMode: Boolean

) {
    val barBg = if (darkMode) Color(0xFF111111) else Color(0xFFF0F1F3)
    val iconTint = if (darkMode) Color.White else Color(0xFF111111)

    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Volver",
                    tint = iconTint,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        actions = {


            IconButton(onClick = onTunerClick) {
                Box(modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = "Afinador",
                        tint = iconTint,
                        modifier = Modifier.matchParentSize()
                    )
                    if (tunerOn) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color(0xFFFF5252), RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            IconButton(onClick = onConcertClick) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Concert",
                    tint = if (concertModeOn) Color(0xFFFFC107) else iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = barBg,
            titleContentColor = iconTint,
            navigationIconContentColor = iconTint,
            actionIconContentColor = iconTint
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(0.5.dp, if (darkMode) Color(0xFF1C1C1C) else Color(0xFFE0E0E0))
            .shadow(8.dp)
    )
}

@Composable
fun StyledLeftToolBar(
    selectedTool: String,
    penColor: Color,
    strokeWidth: Float,
    smoothingEnabled: Boolean,
    onSelectTool: (String) -> Unit,
    onColorClick: () -> Unit,
    onStrokeChange: (Float) -> Unit,
    onToggleSmoothing: () -> Unit,
    onUndo: () -> Unit,
    darkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val toolbarBg = if (darkMode) Color(0xFF1B1E23) else Color(0xFFF0F1F3)

    Surface(
        modifier = modifier.width(88.dp),
        color = toolbarBg,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StyledToolButton(
                icon = Icons.Default.TouchApp,
                label = "Mover",
                selected = selectedTool == "none",
                onClick = { onSelectTool("none") },
                darkMode = darkMode
            )

            StyledToolButton(
                icon = Icons.Default.Edit,
                label = "Lápiz",
                selected = selectedTool == "pen",
                onClick = { onSelectTool("pen") },
                darkMode = darkMode
            )

            StyledToolButton(
                icon = Icons.Default.Delete,
                label = "Borrar",
                selected = selectedTool == "eraser",
                onClick = { onSelectTool("eraser") },
                darkMode = darkMode
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.width(60.dp),
                thickness = 1.dp,
                color = (if (darkMode) Color.White else Color.Black).copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = penColor,
                modifier = Modifier
                    .size(54.dp)
                    .clickable { onColorClick() },
                shadowElevation = 4.dp
            ) {}

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StyledToolButton(
                    icon = Icons.Default.Remove,
                    label = "Fino",
                    selected = strokeWidth < 0.005f,
                    onClick = { onStrokeChange(0.003f) },
                    compact = true,
                    darkMode = darkMode
                )
                StyledToolButton(
                    icon = Icons.Default.HorizontalRule,
                    label = "Medio",
                    selected = strokeWidth in 0.005f..0.008f,
                    onClick = { onStrokeChange(0.006f) },
                    compact = true,
                    darkMode = darkMode
                )
                StyledToolButton(
                    icon = Icons.Default.DragHandle,
                    label = "Grueso",
                    selected = strokeWidth > 0.008f,
                    onClick = { onStrokeChange(0.01f) },
                    compact = true,
                    darkMode = darkMode
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.width(60.dp),
                thickness = 1.dp,
                color = (if (darkMode) Color.White else Color.Black).copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(12.dp))

            StyledToolButton(
                icon = Icons.Default.Tune,
                label = "Suave",
                selected = smoothingEnabled,
                onClick = onToggleSmoothing,
                darkMode = darkMode
            )

            Spacer(modifier = Modifier.weight(1f))

            StyledToolButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = "Undo",
                selected = false,
                onClick = onUndo,
                darkMode = darkMode
            )
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
    darkMode: Boolean
) {
    val bg = when {
        selected && darkMode -> Color(0xFF2E466E)
        selected && !darkMode -> Color(0xFFDCE7FF)
        !selected && darkMode -> Color(0xFF272B33)
        else -> Color(0xFFE6E8EC)
    }

    val tint = when {
        selected && darkMode -> Color(0xFF90CAF9)
        selected && !darkMode -> Color(0xFF1D4ED8)
        !selected && darkMode -> Color(0xFFD0D3D8)
        else -> Color(0xFF2B2E34)
    }

    val size = if (compact) 42.dp else 56.dp

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = Modifier
            .size(size)
            .clickable { onClick() },
        shadowElevation = if (selected) 6.dp else 2.dp
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
            )
        }
    }
}
