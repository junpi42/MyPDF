package com.example.mypdf

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import kotlin.math.roundToInt

private const val TAG = "PDF_TIMING"

data class DrawingPath(
    val points: List<Offset>, // puntos normalizados [0..1]
    val color: Color,
    val strokeWidth: Float,   // ancho normalizado relativo al ancho del lienzo
    val isEraser: Boolean = false
)

data class PageAnnotations(
    val pageIndex: Int,
    val paths: MutableList<DrawingPath> = mutableListOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(file: File, onBack: () -> Unit) {
    val startTime = remember { System.nanoTime() }
    Log.i(TAG, "⏱️ PdfViewerScreen STARTED for: ${file.name}")

    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    var selectedTool by remember { mutableStateOf("none") } // "none", "pen", "eraser"
    var penColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableStateOf(0.006f) }
    var smoothingEnabled by remember { mutableStateOf(true) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Estados globales para zoom y pan
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }

    val annotations = remember { mutableMapOf<Int, PageAnnotations>() }

    // ===== archivo de anotaciones + guardado diferido =====
    val annFile = remember(file.path) { File(file.parentFile, file.nameWithoutExtension + ".ann.json") }
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
                FileOutputStream(tmp).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
                if (annFile.exists()) annFile.delete()
                tmp.renameTo(annFile)
            }
        }
    }

    // pantalla completa
    DisposableEffect(Unit) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        }
    }

    // Holder con caché LRU y utilidades de render
    val holderStart = System.nanoTime()
    val holder = remember(file.path) { PdfRendererHolder(file) }
    Log.i(TAG, "✅ Holder initialized in ${(System.nanoTime() - holderStart) / 1_000_000} ms")

    var pageCount by remember { mutableStateOf(0) }
    val pageCountStart = System.nanoTime()
    LaunchedEffect(Unit) {
        pageCount = holder.pageCount
        Log.i(TAG, "📄 Page count: $pageCount in ${(System.nanoTime() - pageCountStart) / 1_000_000} ms")
    }

    val pageBitmaps = remember { mutableStateListOf<Bitmap?>() }

    var initialLoading by remember { mutableStateOf(true) }
    val readyThreshold by remember(pageCount) { mutableStateOf(kotlin.math.min(3, kotlin.math.max(pageCount, 0))) }
    var showInitialOverlay by remember { mutableStateOf(true) }

    LaunchedEffect(pageCount) {
        if (pageCount > 0) {
            pageBitmaps.clear()
            repeat(pageCount) { pageBitmaps.add(null) }
            showInitialOverlay = true
            initialLoading = true
        }
    }

    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * context.resources.displayMetrics.density).toInt()

    LaunchedEffect(screenWidthPx) {
        if (pageCount > 0 && screenWidthPx > 0) {
            holder.clearCache()
            for (i in 0 until pageCount) {
                pageBitmaps.getOrNull(i)?.let { old ->
                    if (!old.isRecycled) runCatching { old.recycle() }
                }
                if (i < pageBitmaps.size) pageBitmaps[i] = null
            }
            initialLoading = false
        }
    }

    // ===== Render: precarga de primeras páginas (quick + hi primera) =====
    val preloadStart = System.nanoTime()
    LaunchedEffect(pageCount, screenWidthPx) {
        if (pageCount > 0 && screenWidthPx > 0) {
            val quickW = kotlin.math.min(screenWidthPx, 600)
            val bootCount = kotlin.math.min(readyThreshold + 1, pageCount)
            val semaphore = Semaphore(1) // evita colisiones al abrir páginas

            coroutineScope {
                repeat(bootCount) { idx ->
                    if (pageBitmaps.getOrNull(idx) == null) {
                        launch {
                            semaphore.withPermit {
                                val bmpQuick = withContext(Dispatchers.Default) {
                                    holder.renderPageQuick(idx, quickW)
                                }
                                if (bmpQuick != null && idx < pageBitmaps.size) {
                                    val prev = pageBitmaps[idx]
                                    pageBitmaps[idx] = bmpQuick
                                    if (prev != null && prev != bmpQuick && !prev.isRecycled) runCatching { prev.recycle() }
                                }
                            }
                        }
                    }
                }
            }
            if (pageCount > 0) {
                val hi = withContext(Dispatchers.Default) { holder.renderPageToWidth(0, screenWidthPx) }
                if (hi != null && 0 < pageBitmaps.size) {
                    val prev = pageBitmaps[0]
                    pageBitmaps[0] = hi
                    if (prev != null && prev != hi && !prev.isRecycled) runCatching { prev.recycle() }
                }
            }
            Log.i(TAG, "🚀 Initial preload in ${(System.nanoTime() - preloadStart) / 1_000_000} ms")
        }
    }

    LaunchedEffect(pageBitmaps, readyThreshold) {
        snapshotFlow { pageBitmaps.count { it != null } }
            .collectLatest { loaded ->
                if (loaded >= readyThreshold) {
                    showInitialOverlay = false
                    Log.i(TAG, "✨ Viewer READY in ${(System.nanoTime() - startTime) / 1_000_000} ms")
                }
            }
    }

    LaunchedEffect(pageCount) {
        if (pageCount > 0) {
            delay(1500)
            if (pageBitmaps.count { it != null } > 0) showInitialOverlay = false
        }
    }

    val listState = rememberLazyListState()

    var isScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { isScrolling = it }
    }

    // Render bajo demanda y prefetch
    val inFlightJobs = remember { mutableStateMapOf<String, Job>() }
    LaunchedEffect(listState, pageCount, screenWidthPx) {
        if (pageCount <= 0 || screenWidthPx <= 0) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collectLatest { visibles ->
                if (visibles.isEmpty()) return@collectLatest
                val window = 2
                val targetsQuick = buildSet {
                    visibles.forEach { v -> for (i in (v - window)..(v + window)) if (i in 0 until pageCount) add(i) }
                }
                val targetsHi = visibles.toSet()
                val quickW = kotlin.math.min(screenWidthPx, 600)
                val sem = Semaphore(2)

                // Cancelar jobs que ya no son necesarios
                val stillNeeded = (targetsQuick + targetsHi)
                inFlightJobs.keys.toList().forEach { key ->
                    val idx = key.substringAfter(':').toIntOrNull()
                    if (idx == null || idx !in stillNeeded) inFlightJobs.remove(key)?.cancel()
                }

                // Quick
                targetsQuick.forEach { idx ->
                    if (pageBitmaps.getOrNull(idx) == null) {
                        val key = "q:$idx"
                        if (inFlightJobs["h:$idx"]?.isActive == true) return@forEach
                        inFlightJobs[key]?.cancel()
                        inFlightJobs[key] = launch {
                            val t0 = System.nanoTime()
                            sem.withPermit {
                                val bmp = withContext(Dispatchers.Default) { holder.renderPageQuick(idx, quickW) }
                                if (bmp != null && idx < pageBitmaps.size && pageBitmaps[idx] == null) {
                                    pageBitmaps[idx] = bmp
                                    Log.i(TAG, "⚡ Quick page $idx in ${(System.nanoTime() - t0) / 1_000_000} ms")
                                }
                            }
                        }
                    }
                }

                // Alta resolución
                if (!isScrolling) {
                    targetsHi.forEach { idx ->
                        val keyHi = "h:$idx"
                        if (inFlightJobs[keyHi]?.isActive == true) return@forEach
                        inFlightJobs["q:$idx"]?.cancel()
                        inFlightJobs[keyHi] = launch {
                            val t0 = System.nanoTime()
                            val bmp = withContext(Dispatchers.Default) { holder.renderPageToWidth(idx, screenWidthPx) }
                            if (bmp != null && idx < pageBitmaps.size) {
                                val prev = pageBitmaps[idx]
                                if (prev == null || prev.width < bmp.width * 0.9f) {
                                    pageBitmaps[idx] = bmp
                                    if (prev != null && prev != bmp && !prev.isRecycled) runCatching { prev.recycle() }
                                    Log.i(TAG, "🎯 High page $idx in ${(System.nanoTime() - t0) / 1_000_000} ms")
                                }
                            }
                        }
                    }
                }
            }
    }

    // Utilidad de borrado por proximidad
    fun distancePointToSegment(p: Offset, a: Offset, b: Offset): Float {
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

    fun erasePathsAt(pageIndex: Int, eraserPoints: List<Offset>, threshold: Float) {
        val page = annotations.getOrPut(pageIndex) { PageAnnotations(pageIndex) }
        if (page.paths.isEmpty() || eraserPoints.size < 2) return
        val toRemove = mutableSetOf<Int>()
        page.paths.forEachIndexed { idx, path ->
            val pts = path.points
            if (pts.size < 2) return@forEachIndexed
            var hit = false
            loop@ for (i in 0 until pts.size - 1) {
                val a = pts[i]; val b = pts[i + 1]
                for (e in eraserPoints) {
                    if (distancePointToSegment(e, a, b) <= threshold) { hit = true; break@loop }
                }
            }
            if (hit) toRemove.add(idx)
        }
        if (toRemove.isNotEmpty()) {
            toRemove.sortedDescending().forEach { page.paths.removeAt(it) }
            scheduleSave()
        }
    }

    Surface(color = Color(0xFF424242), modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showInitialOverlay && pageCount > 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        val loadedCount = pageBitmaps.count { it != null }
                        Text("Preparando visor: $loadedCount / $pageCount", color = Color.White)
                    }
                }
            } else {
                if (pageCount <= 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Cargando…", color = Color.White)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (selectedTool == "none") {
                                    Modifier.pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val changes = event.changes

                                                when {
                                                    changes.size >= 2 -> {
                                                        // Gesto con 2+ dedos: zoom o pan
                                                        val p1 = changes[0].position
                                                        val p2 = changes[1].position

                                                        val dx = p2.x - p1.x
                                                        val dy = p2.y - p1.y
                                                        val currentDistance = kotlin.math.sqrt(dx * dx + dy * dy)

                                                        // Umbral MUY BAJO: 20px (funciona incluso con dedos muy cercanos)
                                                        if (currentDistance >= 20f) {
                                                            val p1Prev = changes[0].previousPosition
                                                            val p2Prev = changes[1].previousPosition
                                                            val dxPrev = p2Prev.x - p1Prev.x
                                                            val dyPrev = p2Prev.y - p1Prev.y
                                                            val prevDistance = kotlin.math.sqrt(dxPrev * dxPrev + dyPrev * dyPrev)

                                                            if (prevDistance > 0f) {
                                                                // Calcular factor de zoom
                                                                val zoomFactor = currentDistance / prevDistance

                                                                // Aplicar zoom incluso con cambios mínimos
                                                                if (kotlin.math.abs(zoomFactor - 1f) > 0.001f) {
                                                                    val newScale = (scale * zoomFactor).coerceIn(1f, 4f)
                                                                    scale = newScale

                                                                    changes.forEach { it.consume() }
                                                                }

                                                                // Pan horizontal cuando hay zoom y no hay cambio de escala significativo
                                                                if (scale > 1f && kotlin.math.abs(zoomFactor - 1f) < 0.02f) {
                                                                    val centerX = (p1.x + p2.x) / 2f
                                                                    val centerXPrev = (p1Prev.x + p2Prev.x) / 2f
                                                                    val panDelta = centerX - centerXPrev

                                                                    val maxX = (size.width * (scale - 1f)) / 2f
                                                                    offsetX = (offsetX + panDelta).coerceIn(-maxX, maxX)
                                                                }
                                                            }
                                                        }

                                                        // Resetear offset si volvemos a escala 1
                                                        if (scale <= 1f) {
                                                            offsetX = 0f
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else Modifier
                            )
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = 0f // Solo pan horizontal, el vertical lo maneja el scroll
                                ),
                            contentPadding = PaddingValues(top = 64.dp, bottom = 16.dp),
                            userScrollEnabled = true, // Siempre permitir scroll vertical
                        ) {
                        items(pageCount) { index ->
                            val bmp = pageBitmaps.getOrNull(index)
                            PdfPageItem(
                                index = index,
                                bitmap = bmp,
                                showLoadingLabel = !isScrolling,
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
                                    val thr = (strokeWidth * 1.5f).coerceAtLeast(0.003f)
                                    erasePathsAt(index, eraserPoints, thr)
                                }
                            )
                            // Separador entre páginas
                            if (index < pageCount - 1) {
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                    }
                }
            }

            // top bar (volver, info)
            TopAppBar(
                title = {
                    Text(
                        text = if (initialLoading) "Cargando..." else "Editando • $pageCount páginas",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0x66000000),
                    titleContentColor = Color.White
                )
            )

            // Barra de herramientas en el lado izquierdo
            LeftToolBar(
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
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .padding(start = 16.dp)
            )

            if (showColorPicker) {
                ColorPickerDialog(
                    currentColor = penColor,
                    onColorSelected = { penColor = it },
                    onDismiss = { showColorPicker = false }
                )
            }
        }
    }

    // ===== cargar anotaciones guardadas =====
    val annotationsStart = System.nanoTime()
    LaunchedEffect(annFile.path) {
        withContext(Dispatchers.IO) {
            if (annFile.exists()) {
                runCatching {
                    val text = FileInputStream(annFile).use { it.readBytes().toString(Charsets.UTF_8) }
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
                            val wNorm = jpath.optDouble("w", 0.005).toFloat()
                            val colorInt = jpath.optInt("c", 0xFF000000.toInt())
                            val isE = jpath.optBoolean("e", false)
                            val ptsArr = jpath.optJSONArray("pts") ?: JSONArray()
                            val pts = mutableListOf<Offset>()
                            for (pIdx in 0 until ptsArr.length()) {
                                val pair = ptsArr.getJSONArray(pIdx)
                                pts.add(Offset(pair.optDouble(0, 0.0).toFloat(), pair.optDouble(1, 0.0).toFloat()))
                            }
                            list.add(DrawingPath(pts, Color(colorInt), wNorm, isE))
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
        Log.i(TAG, "📝 Annotations loaded in ${(System.nanoTime() - annotationsStart) / 1_000_000} ms")
    }

    // ===== onDispose: cancelar renders, guardar y limpiar =====
    DisposableEffect(holder) {
        onDispose {
            // Guardar última página vista
            runCatching {
                val prefs = context.getSharedPreferences("reader_state", android.content.Context.MODE_PRIVATE)
                prefs.edit().putInt("last_page::${file.absolutePath}", listState.firstVisibleItemIndex.coerceAtLeast(0)).apply()
            }

            // Cancelar renders activos
            runCatching {
                inFlightJobs.values.forEach { it.cancel() }
                inFlightJobs.clear()
            }

            // Cerrar holder
            holder.close()

            // Liberar bitmaps
            scope.launch(Dispatchers.Default) {
                delay(32)
                pageBitmaps.forEach { bmp ->
                    if (bmp != null && !bmp.isRecycled) runCatching { bmp.recycle() }
                }
                pageBitmaps.clear()
            }

            // Guardado final bloqueante
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
                FileOutputStream(tmp).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
                if (annFile.exists()) annFile.delete()
                tmp.renameTo(annFile)
            }

            Log.i(TAG, "🏁 PdfViewerScreen CLOSED after ${(System.nanoTime() - startTime) / 1_000_000} ms")
        }
    }
}

// ======================================================
//  ITEMS Y COMPONENTES
// ======================================================

@Composable
private fun PdfPageItem(
    index: Int,
    bitmap: Bitmap?,
    showLoadingLabel: Boolean,
    editMode: Boolean = false,
    annotations: PageAnnotations = PageAnnotations(index),
    selectedTool: String = "none",
    penColor: Color = Color.Red,
    strokeWidth: Float = 0.006f,
    smoothingEnabled: Boolean = true,
    onPathAdded: (DrawingPath) -> Unit = {},
    onErase: (List<Offset>) -> Unit = {}
) {
    var currentPath by remember { mutableStateOf<MutableList<Offset>>(mutableListOf()) }
    var canvasW by remember { mutableStateOf(0f) }
    var canvasH by remember { mutableStateOf(0f) }

    fun toNorm(o: Offset): Offset = if (canvasW > 0f && canvasH > 0f) Offset(o.x / canvasW, o.y / canvasH) else o
    fun toPx(o: Offset): Offset = Offset(o.x * canvasW, o.y * canvasH)

    // Función de suavizado (Chaikin's algorithm)
    fun smoothPath(points: List<Offset>, iterations: Int = 2): List<Offset> {
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
                result.add(q)
                result.add(r)
            }
            result.add(smoothed.last())
            smoothed = result
        }
        return smoothed
    }

    if (bitmap != null) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF303030)) // Fondo gris oscuro para cada página
            .padding(8.dp) // Padding para crear separación visual
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .background(Color.White) // Fondo blanco para la página
                    .then(
                        if (editMode && (selectedTool == "pen" || selectedTool == "eraser")) {
                            // Solo gestos de dibujo cuando hay herramienta activa
                            Modifier.pointerInput(selectedTool, penColor, strokeWidth, canvasW, canvasH) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPath = mutableListOf(toNorm(offset))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPath = currentPath.toMutableList().apply {
                                            add(toNorm(change.position))
                                        }
                                    },
                                    onDragEnd = {
                                        if (currentPath.size > 1) {
                                            val finalPath = if (smoothingEnabled && selectedTool == "pen") {
                                                smoothPath(currentPath.toList())
                                            } else {
                                                currentPath.toList()
                                            }

                                            if (selectedTool == "eraser") {
                                                onErase(finalPath)
                                            } else {
                                                onPathAdded(DrawingPath(finalPath, penColor, strokeWidth, false))
                                            }
                                        }
                                        currentPath = mutableListOf()
                                    }
                                )
                            }
                        } else Modifier
                    )
            ) {
                canvasW = size.width
                canvasH = size.height

                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                )

                if (editMode) {
                    annotations.paths.forEach { drawingPath ->
                        if (drawingPath.points.size > 1) {
                            val path = Path().apply {
                                val first = toPx(drawingPath.points.first())
                                moveTo(first.x, first.y)
                                drawingPath.points.drop(1).forEach { point ->
                                    val p = toPx(point)
                                    lineTo(p.x, p.y)
                                }
                            }
                            drawPath(path = path, color = drawingPath.color, style = Stroke(width = drawingPath.strokeWidth * canvasW))
                        }
                    }

                    if (currentPath.size > 1 && selectedTool == "pen") {
                        val path = Path().apply {
                            val first = toPx(currentPath.first())
                            moveTo(first.x, first.y)
                            currentPath.drop(1).forEach { point ->
                                val p = toPx(point)
                                lineTo(p.x, p.y)
                            }
                        }
                        drawPath(path = path, color = penColor, style = Stroke(width = strokeWidth * canvasW))
                    }
                }
            }

            if (editMode) {
                Text(
                    text = "Página ${index + 1}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            if (showLoadingLabel) {
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.8f), strokeWidth = 2.dp)
            }
        }
    }
}

// ======================================================
//  PDF HOLDER
// ======================================================

private class PdfRendererHolder(file: File) {
    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(pfd)

    @Volatile private var closed = false
    private val renderMutex = Mutex()

    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceAtLeast(8 * 1024)
    private val cache = object : LruCache<Int, Bitmap>(maxKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    val pageCount: Int get() = renderer.pageCount

    // ARGB_8888 por defecto (más compatible con PdfRenderer)
    suspend fun renderPageQuick(index: Int, quickTargetW: Int): Bitmap? {
        cache.get(index)?.let { existing -> if (existing.width >= quickTargetW * 0.95f) return existing }
        return renderInternal(index, quickTargetW, Bitmap.Config.ARGB_8888)
    }

    suspend fun renderPageToWidth(index: Int, targetW: Int): Bitmap? {
        cache.get(index)?.let { existing -> if (existing.width >= targetW * 0.95f) return existing }
        return renderInternal(index, targetW, Bitmap.Config.ARGB_8888)
    }

    private suspend fun renderInternal(index: Int, targetW: Int, config: Bitmap.Config): Bitmap? {
        suspend fun attempt(conf: Bitmap.Config): Bitmap? {
            if (closed) return null
            var page: PdfRenderer.Page? = null
            return try {
                renderMutex.withLock {
                    if (closed) return null
                    if (index !in 0 until renderer.pageCount) return null
                    page = renderer.openPage(index)
                    val srcW = page!!.width
                    val srcH = page!!.height
                    val scale = (targetW.toFloat() / srcW).coerceIn(0.1f, 8f)
                    val outW = (srcW * scale).toInt().coerceAtLeast(1)
                    val outH = (srcH * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(outW, outH, conf)
                    page!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(index, bitmap)
                    bitmap
                }
            } catch (e: IllegalArgumentException) {
                Log.w("PDFPERF", "render failed idx=$index w=$targetW with $conf: ${e.message}")
                null
            } catch (e: Throwable) {
                Log.w("PDFPERF", "render failed idx=$index w=$targetW: ${e.javaClass.simpleName}: ${e.message}")
                null
            } finally {
                runCatching { page?.close() }
            }
        }

        var bmp = attempt(config)
        if (bmp == null && config != Bitmap.Config.ARGB_8888) {
            bmp = attempt(Bitmap.Config.ARGB_8888)
        }
        return bmp
    }

    fun clearCache() { runCatching { cache.evictAll() } }

    fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        runCatching { cache.evictAll() }
    }
}

// ======================================================
//  BARRA IZQUIERDA (herramientas de edición)
// ======================================================

@Composable
private fun LeftToolBar(
    selectedTool: String,
    penColor: Color,
    strokeWidth: Float,
    smoothingEnabled: Boolean,
    onSelectTool: (String) -> Unit,
    onColorClick: () -> Unit,
    onStrokeChange: (Float) -> Unit,
    onToggleSmoothing: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(80.dp),
        color = Color(0xFF2F343A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón de navegación (desactivar herramientas)
            ToolButton(
                icon = Icons.Default.TouchApp,
                label = "Navegar",
                selected = selectedTool == "none",
                onClick = { onSelectTool("none") }
            )

            ToolButton(
                icon = Icons.Default.Edit,
                label = "Lápiz",
                selected = selectedTool == "pen",
                onClick = { onSelectTool("pen") }
            )
            ToolButton(
                icon = Icons.Default.Delete,
                label = "Borrador",
                selected = selectedTool == "eraser",
                onClick = { onSelectTool("eraser") }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.width(48.dp))
            Spacer(Modifier.height(8.dp))

            // Selector de color
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = penColor,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onColorClick() },
                shadowElevation = 2.dp
            ) {}

            // Tamaños de trazo
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(vertical = 8.dp)) {
                ToolButton(
                    icon = Icons.Default.Remove,
                    label = "Fino",
                    selected = strokeWidth < 0.005f,
                    onClick = { onStrokeChange(0.003f) },
                    compact = true
                )
                Spacer(Modifier.height(4.dp))
                ToolButton(
                    icon = Icons.Default.HorizontalRule,
                    label = "Medio",
                    selected = strokeWidth in 0.005f..0.008f,
                    onClick = { onStrokeChange(0.006f) },
                    compact = true
                )
                Spacer(Modifier.height(4.dp))
                ToolButton(
                    icon = Icons.Default.DragHandle,
                    label = "Grueso",
                    selected = strokeWidth > 0.008f,
                    onClick = { onStrokeChange(0.010f) },
                    compact = true
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.2f), modifier = Modifier.width(48.dp))
            Spacer(Modifier.height(8.dp))

            // Botón de suavizado (afinador)
            ToolButton(
                icon = Icons.Default.Tune,
                label = "Afinador",
                selected = smoothingEnabled,
                onClick = onToggleSmoothing
            )

            Spacer(Modifier.weight(1f))

            ToolButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = "Deshacer",
                selected = false,
                onClick = onUndo
            )
        }
    }
}

@Composable
private fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false
) {
    val bg = if (selected) Color(0xFFEFF6FF) else Color.Transparent
    val tint = if (selected) Color(0xFF1D4ED8) else Color.White
    val size = if (compact) 40.dp else 56.dp

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = Modifier
            .size(size)
            .clickable { onClick() }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(if (compact) 20.dp else 24.dp))
        }
    }
}

// ======================================================
//  DIALOGO DE COLOR
// ======================================================

@Composable
private fun ColorPickerDialog(
    currentColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar color") },
        text = {
            Column {
                val colors = listOf(
                    Color.Red, Color.Blue, Color.Green, Color.Yellow,
                    Color.Black, Color.Magenta, Color.Cyan, Color(0xFFFF6B6B),
                    Color(0xFF4ECDC4), Color(0xFF95E1D3), Color(0xFFF38181), Color(0xFFAA96DA)
                )
                colors.chunked(4).forEach { rowColors ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowColors.forEach { color ->
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
