package com.example.mypdf

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
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
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    // Edición siempre activa
    val editMode = true
    var selectedTool by remember { mutableStateOf("pen") }
    var penColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableStateOf(0.006f) }
    var showColorPicker by remember { mutableStateOf(false) }

    // ===== anotaciones en memoria =====
    val annotations = remember { mutableMapOf<Int, PageAnnotations>() }

    // ===== archivo de anotaciones + guardado diferido (MOVED ARRIBA) =====
    val annFile = remember(file.path) { File(file.parentFile, file.nameWithoutExtension + ".ann.json") }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    val scheduleSave: () -> Unit = {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(400)
            try {
                val pages = JSONArray()
                annotations.toSortedMap().forEach { (idx, page) ->
                    val jPage = JSONObject()
                    jPage.put("index", idx)
                    val jPaths = JSONArray()
                    page.paths.forEach { p ->
                        val jP = JSONObject()
                        jP.put("e", p.isEraser)
                        jP.put("c", p.color.toArgb())
                        jP.put("w", p.strokeWidth) // normalizado
                        val pts = JSONArray()
                        p.points.forEach { o ->
                            val pair = JSONArray()
                            pair.put(o.x)
                            pair.put(o.y)
                            pts.put(pair)
                        }
                        jP.put("pts", pts)
                        jPaths.put(jP)
                    }
                    jPage.put("paths", jPaths)
                    pages.put(jPage)
                }
                val root = JSONObject()
                root.put("version", 1)
                root.put("pages", pages)
                val tmp = File(annFile.parentFile, annFile.name + ".tmp")
                FileOutputStream(tmp).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
                if (annFile.exists()) annFile.delete()
                tmp.renameTo(annFile)
            } catch (_: Exception) {}
        }
    }

    // ===== afinador =====
    var tunerActive by remember { mutableStateOf(false) }
    val tuner = remember { AudioTuner() }
    val tuningResult by tuner.tuningState.collectAsState()
    var showFrequencySelector by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            tunerActive = true
            tuner.startTuning(scope)
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
    val holder = remember(file.path) { PdfRendererHolder(file) }

    var pageCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { pageCount = holder.pageCount }

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
            initialLoading = true
            holder.clearCache()
            for (i in 0 until pageCount) {
                val old = pageBitmaps.getOrNull(i)
                if (old != null && !old.isRecycled) try { old.recycle() } catch (_: Exception) {}
                if (i < pageBitmaps.size) pageBitmaps[i] = null
            }
            initialLoading = false
        }
    }

    LaunchedEffect(pageCount, screenWidthPx) {
        if (pageCount > 0 && screenWidthPx > 0) {
            val quickW = kotlin.math.min(screenWidthPx, 600)
            val bootCount = kotlin.math.min(readyThreshold + 1, pageCount)
            val semaphore = Semaphore(kotlin.math.min(2, Runtime.getRuntime().availableProcessors()))
            coroutineScope {
                repeat(bootCount) { idx ->
                    if (pageBitmaps.getOrNull(idx) == null) {
                        launch {
                            semaphore.withPermit {
                                val bmpQuick = withContext(Dispatchers.Default) {
                                    holder.renderPageQuick(idx, quickW)
                                }
                                if (bmpQuick != null) {
                                    withContext(Dispatchers.Main) {
                                        if (idx < pageBitmaps.size) {
                                            val prev = pageBitmaps[idx]
                                            pageBitmaps[idx] = bmpQuick
                                            if (prev != null && prev != bmpQuick && !prev.isRecycled) try { prev.recycle() } catch (_: Exception) {}
                                        }
                                    }
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
                    if (prev != null && prev != hi && !prev.isRecycled) try { prev.recycle() } catch (_: Exception) {}
                }
            }
        }
    }

    LaunchedEffect(pageBitmaps, readyThreshold) {
        snapshotFlow { pageBitmaps.count { it != null } }
            .collectLatest { loaded ->
                if (loaded >= readyThreshold) showInitialOverlay = false
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

    // Utilidad de borrado por proximidad (distancia punto-segmento en coords normalizadas)
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
        // Para cada path, si cualquier segmento está cerca de cualquier punto del borrador, marcar para borrar
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
            // eliminar de atrás hacia delante para mantener índices
            toRemove.sortedDescending().forEach { page.paths.removeAt(it) }
            scheduleSave()
        }
    }

    // ...existing code...

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showInitialOverlay && pageCount > 0) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        val loadedCount = pageBitmaps.count { it != null }
                        Text(
                            text = "Preparando visor: $loadedCount / $pageCount",
                            color = Color.White
                        )
                    }
                }
            } else {
                if (pageCount <= 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Cargando…", color = Color.White)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 0.dp),
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
                                onPathAdded = { path ->
                                    annotations.getOrPut(index) { PageAnnotations(index) }.paths.add(path)
                                    scheduleSave()
                                },
                                onErase = { eraserPoints ->
                                    // Usa el ancho actual como radio del borrador; un poco más amplio
                                    val thr = (strokeWidth * 1.5f).coerceAtLeast(0.003f)
                                    erasePathsAt(index, eraserPoints, thr)
                                }
                            )
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

            // Solo mantenemos el FAB del afinador
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        if (tunerActive) {
                            tunerActive = false
                            tuner.stopTuning()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    containerColor = if (tunerActive) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = if (tunerActive) "Desactivar afinador" else "Activar afinador",
                        tint = Color.White
                    )
                }
            }

            // Barra de herramientas siempre visible (edición siempre activa)
            RightToolBar(
                selectedTool = selectedTool,
                penColor = penColor,
                strokeWidth = strokeWidth,
                onSelectTool = { selectedTool = it },
                onColorClick = { showColorPicker = true },
                onStrokeChange = { strokeWidth = it },
                onUndo = {
                    val currentPage = listState.firstVisibleItemIndex
                    annotations[currentPage]?.paths?.removeLastOrNull()
                    scheduleSave()
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(end = 90.dp)
            )

            // selector de color
            if (showColorPicker) {
                ColorPickerDialog(
                    currentColor = penColor,
                    onColorSelected = { penColor = it },
                    onDismiss = { showColorPicker = false }
                )
            }

            // afinador
            if (tunerActive) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 80.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xE6000000)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(
                                    color = when {
                                        tuningResult == null -> Color.Gray
                                        tuningResult?.errorMessage != null -> Color(0xFFFF6B6B)
                                        tuningResult?.isInTune == true -> Color(0xFF4CAF50)
                                        else -> Color(0xFFFF6B6B)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            showFrequencySelector = true
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when {
                                    tuningResult?.errorMessage != null -> tuningResult?.errorMessage ?: ""
                                    tuningResult?.isInTune == true -> "✓ AFINADO"
                                    tuningResult != null -> "DESAFINADO"
                                    else -> "Escuchando..."
                                },
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        tuningResult?.let { result ->
                            if (result.errorMessage == null) {
                                Text(
                                    text = "Nota: ${result.targetNote}",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = String.format("%.1f Hz", result.detectedFrequency),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 16.sp
                                )

                                val centsText = if (result.centsOff > 0) {
                                    String.format("+%.0f cents (alto)", result.centsOff)
                                } else {
                                    String.format("%.0f cents (bajo)", result.centsOff)
                                }

                                Text(
                                    text = centsText,
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                if (showFrequencySelector) {
                    FrequencySelectorPopup(
                        tuner = tuner,
                        onDismiss = { showFrequencySelector = false }
                    )
                }
            }
        }
    }

    // ===== cargar anotaciones guardadas =====
    LaunchedEffect(annFile.path) {
        withContext(Dispatchers.IO) {
            if (annFile.exists()) {
                try {
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
                            val isE = jpath.optBoolean("e", false)
                            val colorInt = jpath.optInt("c", 0xFF000000.toInt())
                            val wNorm = jpath.optDouble("w", 0.005).toFloat()
                            val ptsArr = jpath.optJSONArray("pts") ?: JSONArray()
                            val pts = mutableListOf<Offset>()
                            for (pIdx in 0 until ptsArr.length()) {
                                val pair = ptsArr.getJSONArray(pIdx)
                                val x = pair.optDouble(0, 0.0).toFloat()
                                val y = pair.optDouble(1, 0.0).toFloat()
                                pts.add(Offset(x, y))
                            }
                            list.add(
                                DrawingPath(
                                    points = pts,
                                    color = Color(colorInt),
                                    strokeWidth = wNorm,
                                    isEraser = isE
                                )
                            )
                        }
                        loaded[idx] = PageAnnotations(idx, list)
                    }
                    withContext(Dispatchers.Main) {
                        annotations.clear()
                        annotations.putAll(loaded)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // ===== onDispose: guardar y limpiar =====
    DisposableEffect(holder) {
        onDispose {
            try { saveLastPage(context, file, listState.firstVisibleItemIndex) } catch (_: Exception) {}
            tuner.stopTuning()
            holder.close()
            pageBitmaps.forEach { bmp ->
                try { if (bmp != null && !bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
            }
            pageBitmaps.clear()
            // guardado final bloqueante
            runCatching {
                val pages = JSONArray()
                annotations.toSortedMap().forEach { (idx, page) ->
                    val jPage = JSONObject()
                    jPage.put("index", idx)
                    val jPaths = JSONArray()
                    page.paths.forEach { p ->
                        val jP = JSONObject()
                        jP.put("e", p.isEraser)
                        jP.put("c", p.color.toArgb())
                        jP.put("w", p.strokeWidth)
                        val pts = JSONArray()
                        p.points.forEach { o ->
                            val pair = JSONArray(); pair.put(o.x); pair.put(o.y); pts.put(pair)
                        }
                        jP.put("pts", pts)
                        jPaths.put(jP)
                    }
                    jPage.put("paths", jPaths)
                    pages.put(jPage)
                }
                val root = JSONObject(); root.put("version", 1); root.put("pages", pages)
                val tmp = File(annFile.parentFile, annFile.name + ".tmp")
                FileOutputStream(tmp).use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
                if (annFile.exists()) annFile.delete(); tmp.renameTo(annFile)
            }
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
    selectedTool: String = "pen",
    penColor: Color = Color.Red,
    strokeWidth: Float = 0.006f,
    onPathAdded: (DrawingPath) -> Unit = {},
    onErase: (List<Offset>) -> Unit = {}
) {
    var currentPath by remember { mutableStateOf<MutableList<Offset>>(mutableListOf()) }
    var canvasW by remember { mutableStateOf(0f) }
    var canvasH by remember { mutableStateOf(0f) }

    fun toNorm(o: Offset): Offset = if (canvasW > 0f && canvasH > 0f) Offset(o.x / canvasW, o.y / canvasH) else o
    fun toPx(o: Offset): Offset = Offset(o.x * canvasW, o.y * canvasH)

    if (bitmap != null) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .then(
                        if (editMode) {
                            Modifier.pointerInput(selectedTool, penColor, strokeWidth, canvasW, canvasH) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPath = mutableListOf(toNorm(offset))
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPath = currentPath.toMutableList().apply { add(toNorm(change.position)) }
                                    },
                                    onDragEnd = {
                                        if (currentPath.size > 1) {
                                            if (selectedTool == "eraser") {
                                                onErase(currentPath.toList())
                                            } else {
                                                onPathAdded(
                                                    DrawingPath(
                                                        points = currentPath.toList(),
                                                        color = penColor,
                                                        strokeWidth = strokeWidth,
                                                        isEraser = false
                                                    )
                                                )
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
                    topLeft = Offset.Zero
                )

                if (editMode) {
                    // dibuja paths guardados
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

                            drawPath(
                                path = path,
                                color = drawingPath.color,
                                style = Stroke(width = drawingPath.strokeWidth * canvasW)
                            )
                        }
                    }

                    // Si estamos dibujando con lápiz, mostrar trazo actual; si es borrador, no pintar blanco
                    if (currentPath.size > 1 && selectedTool == "pen") {
                        val path = Path().apply {
                            val first = toPx(currentPath.first())
                            moveTo(first.x, first.y)
                            currentPath.drop(1).forEach { point ->
                                val p = toPx(point)
                                lineTo(p.x, p.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = penColor,
                            style = Stroke(width = strokeWidth * canvasW)
                        )
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

    private val renderMutex = Mutex()

    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceAtLeast(8 * 1024)
    private val cache = object : LruCache<Int, Bitmap>(maxKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return (value.byteCount / 1024)
        }
    }

    val pageCount: Int get() = renderer.pageCount

    suspend fun renderPageQuick(index: Int, quickTargetW: Int): Bitmap? {
        cache.get(index)?.let { existing ->
            if (existing.width >= quickTargetW * 0.95f) return existing
        }
        return renderInternal(index, quickTargetW, Bitmap.Config.RGB_565)
    }

    suspend fun renderPageToWidth(index: Int, targetW: Int): Bitmap? {
        cache.get(index)?.let { existing ->
            if (existing.width >= targetW * 0.95f) return existing
        }
        return renderInternal(index, targetW, Bitmap.Config.ARGB_8888)
    }

    private suspend fun renderInternal(index: Int, targetW: Int, config: Bitmap.Config): Bitmap? {
        suspend fun attempt(): Bitmap? {
            var page: PdfRenderer.Page? = null
            return try {
                renderMutex.withLock {
                    page = renderer.openPage(index)
                    val srcW = page!!.width
                    val srcH = page!!.height
                    val scale = (targetW.toFloat() / srcW).coerceAtLeast(0.1f)
                    val outW = (srcW * scale).toInt().coerceAtLeast(1)
                    val outH = (srcH * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(outW, outH, config)
                    page!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(index, bitmap)
                    bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                try { page?.close() } catch (_: Exception) {}
            }
        }
        val first = attempt()
        if (first != null) return first
        try { delay(80) } catch (_: Exception) {}
        return attempt()
    }

    fun clearCache() {
        try {
            cache.evictAll()
        } catch (_: Exception) {}
    }

    fun close() {
        try {
            renderer.close()
            pfd.close()
            cache.evictAll()
        } catch (_: Exception) { }
    }
}

// ======================================================
//  POPUP AFINADOR
// ======================================================

@Composable
fun FrequencySelectorPopup(
    tuner: AudioTuner,
    onDismiss: () -> Unit
) {
    val currentFreq by tuner.baseFrequency.collectAsState()
    val isNoisy by tuner.noisyEnvironment.collectAsState()

    Popup(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(min = 280.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ajustes del afinador",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Frecuencia base",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color.Black
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { tuner.decrementBaseFrequency(1.0) },
                        modifier = Modifier.size(50.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("-", fontSize = 30.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "${currentFreq.toInt()} Hz",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.width(80.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = { tuner.incrementBaseFrequency(1.0) },
                        modifier = Modifier.size(50.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("+", fontSize = 30.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tipo de ambiente",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { tuner.setNoisyEnvironment(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isNoisy) Color(0xFF4CAF50) else Color.Gray
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(" Entorno Silencioso", fontSize = 20.sp)
                        }
                    }

                    Button(
                        onClick = { tuner.setNoisyEnvironment(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isNoisy) Color(0xFFFF6B6B) else Color.Gray
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {

                            Text("Entorno Ruidoso", fontSize = 20.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}

// ======================================================
//  BARRA DERECHA (lápiz / borrador)
// ======================================================

@Composable
private fun RightToolBar(
    selectedTool: String,
    penColor: Color,
    strokeWidth: Float,
    onSelectTool: (String) -> Unit,
    onColorClick: () -> Unit,
    onStrokeChange: (Float) -> Unit,
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

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = penColor,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onColorClick() },
                shadowElevation = 2.dp
            ) {}

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
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

            Spacer(Modifier.weight(1f))

            ToolButton(
                icon = Icons.Default.Undo,
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
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
            )
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
                                    .padding(4.dp)
                                    .clickable {
                                        onColorSelected(color)
                                        onDismiss()
                                    },
                                shadowElevation = if (color == currentColor) 4.dp else 0.dp,
                                border = if (color == currentColor) androidx.compose.foundation.BorderStroke(
                                    2.dp,
                                    Color.Black
                                ) else null
                            ) {}
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
