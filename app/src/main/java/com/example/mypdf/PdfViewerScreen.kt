package com.example.mypdf

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.min

private const val TAG = "PDF_TIMING"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PdfViewerScreen(
    file: File,
    onBack: () -> Unit,
    isDarkMode: Boolean,
    language: Language
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
                        val pts = mutableListOf<androidx.compose.ui.geometry.Offset>()
                        for (pIdx in 0 until ptsArr.length()) {
                            val pair = ptsArr.getJSONArray(pIdx)
                            pts.add(
                                androidx.compose.ui.geometry.Offset(
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
    val screenBg = MaterialTheme.colorScheme.background

    Surface(modifier = Modifier.fillMaxSize(), color = screenBg) {
        Box(Modifier.fillMaxSize()) {

            if (concertModeOn) {
                // ===== MODO CONCIERTO: PDF a pantalla completa con zoom/pan =====
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
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
                ) {
                    LazyColumn(
                        state = listState,
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
                                editMode = false,
                                annotations = annotations.getOrPut(index) { PageAnnotations(index) },
                                selectedTool = "none",
                                penColor = penColor,
                                strokeWidth = strokeWidth,
                                smoothingEnabled = smoothingEnabled,
                                onPathAdded = {},
                                onErase = {},
                                darkMode = darkMode
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                // Botón flotante de casa para volver a edición
                IconButton(
                    onClick = { concertModeOn = false },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = strings().backDescription,
                        tint = if (darkMode) Color.White else Color(0xFF111111)
                    )
                }
            } else {
                // ===== MODO EDICIÓN ORIGINAL =====
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

                // Barra superior completa con botón de volver y modo concierto
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

                // Afinador pequeño sólo en modo edición
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

                // Barra lateral de herramientas sólo en modo edición
                StyledLeftToolBar(
                    selectedTool = selectedTool,
                    penColor = penColor,
                    strokeWidth = strokeWidth,
                    smoothingEnabled = smoothingEnabled,
                    onSelectTool = { selectedTool = it },
                    onColorClick = { showColorPicker = true },
                    onColorChanged = { penColor = it },
                    onStrokeChange = { strokeWidth = it },
                    onToggleSmoothing = { smoothingEnabled = !smoothingEnabled },
                    onUndo = {
                        val currentPage = listState.firstVisibleItemIndex
                        annotations[currentPage]?.paths?.removeLastOrNull()
                        scheduleSave()
                    },
                    darkMode = darkMode,
                    language = language,
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
}

private fun distancePointToSegment(
    p: androidx.compose.ui.geometry.Offset,
    a: androidx.compose.ui.geometry.Offset,
    b: androidx.compose.ui.geometry.Offset
): Float {
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
