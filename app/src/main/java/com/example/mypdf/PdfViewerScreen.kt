package com.example.mypdf

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.geometry.Rect
import com.example.mypdf.eyecontrol.EyeControlButton
import com.example.mypdf.eyecontrol.EyeControlHelpCard
import com.example.mypdf.eyecontrol.WinkState
import com.example.mypdf.eyecontrol.rememberEyeControlState
import com.example.mypdf.eyecontrol.EyeCalibrationDialog
import com.example.mypdf.eyecontrol.EyeCalibrationManager
import com.example.mypdf.eyecontrol.CalibrationStep
import com.example.mypdf.eyecontrol.EyeCalibrationFlow

private const val TAG = "PDF_TIMING"
private const val STYLUS_VISIBILITY_TIMEOUT_MS = 20_000L

private data class ViewerTutorialTargets(
    val toolbox: Rect? = null,
    val tunerButton: Rect? = null,
    val concertButton: Rect? = null,
    val tunerDisplay: Rect? = null,
    val backButton: Rect? = null,
    val winkButton: Rect? = null
)


@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Suppress("ComposeBoxWithConstraintsScopeUnused", "DEPRECATION", "EXPERIMENTAL_API_USAGE")
@Composable
fun PdfViewerScreen(
    deviceType: DeviceType,
    file: File,
    onBack: () -> Unit,
    isDarkMode: Boolean,
    isDaltonic: Boolean,
    onToggleDaltonic: () -> Unit,
    language: Language,
    tutorialState: TutorialState,
    onTutorialStateChange: (TutorialState) -> Unit,
    onTutorialComplete: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    val tunner = remember { AudioTuner() }

    // Stack de undo: hasta 7 snapshots
    val undoStack = remember { mutableStateListOf<Map<Int, PageAnnotations>>() }

    // Stylus state - nuevo sistema basado en detección por uso
    var stylusDetected by remember { mutableStateOf(false) }
    var stylusButtonTool by remember { mutableStateOf(StylusTool.MARKER) }
    var previousTool by remember { mutableStateOf("none") }
    var stylusLastSeenAt by remember { mutableLongStateOf(0L) }

    // ===== MODO DIA / NOCHE =====
    val darkMode = isDarkMode

    // Palette State
    val paletteColors = remember { mutableStateListOf(Color.Red, Color.Blue, Color.Black) }
    var selectedPaletteIndex by remember { mutableIntStateOf(0) }
    val currentColor = paletteColors[selectedPaletteIndex]

    var selectedTool by remember { mutableStateOf("none") }
    // markerStrokeWidth is for MARKER
    var markerStrokeWidth by remember { mutableFloatStateOf(0.006f) }
    // highlighterStrokeWidth is for HIGHLIGHTER
    var highlighterStrokeWidth by remember { mutableFloatStateOf(0.02f) }

    var eraserRadiusNorm by remember { mutableFloatStateOf(0.03f) }
    var smoothingEnabled by remember { mutableStateOf(true) }
    var showColorPicker by remember { mutableStateOf(false) }

    // afinador
    var tunerOn by remember { mutableStateOf(false) }
    var concertModeOn by rememberSaveable { mutableStateOf(false) }
    var showTunerSettings by remember { mutableStateOf(false) }
    var tunerExtendedMode by remember { mutableStateOf(false) }
    
    // Eye control para modo concierto
    var eyeControlEnabled by remember { mutableStateOf(false) }
    var showEyeControlHelp by remember { mutableStateOf(false) }
    var hasShownEyeControlHelp by remember { mutableStateOf(false) }
    var showCalibrationMenu by remember { mutableStateOf(false) }
    var calibrationStep by remember { mutableStateOf(CalibrationStep.MENU) }
    
    // Calibration manager
    val calibrationManager = remember { EyeCalibrationManager(context) }
    var isCalibrated by remember { mutableStateOf(calibrationManager.loadCalibration().isCalibrated) }

    // Tutorial Targets
    var tutorialTargets by remember { mutableStateOf(ViewerTutorialTargets()) }

    fun resolveTarget(step: TutorialStep): Rect? = when (step) {
        TutorialStep.TOOLBOX -> tutorialTargets.toolbox
        TutorialStep.TUNER_BUTTON -> tutorialTargets.tunerButton
        TutorialStep.TUNER_ACTIVE -> tutorialTargets.tunerDisplay
        TutorialStep.TUNER_MENU -> tutorialTargets.tunerDisplay
        TutorialStep.CONCERT_MODE -> tutorialTargets.concertButton
        TutorialStep.WINK_DETECTOR -> tutorialTargets.winkButton
        TutorialStep.WINK_CALIBRATION -> tutorialTargets.winkButton
        TutorialStep.EXIT_CONCERT -> tutorialTargets.backButton
        else -> null
    }

    fun advanceTutorial(next: TutorialStep) {
        onTutorialStateChange(tutorialState.copy(step = next, targetRect = resolveTarget(next)))
    }

    LaunchedEffect(tutorialTargets, tutorialState.step) {
        val resolved = resolveTarget(tutorialState.step)
        if (resolved != tutorialState.targetRect) {
            onTutorialStateChange(tutorialState.copy(targetRect = resolved))
        }
    }

    // zoom / pan
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isPinching by remember { mutableStateOf(false) }

    // Handle Back Press
    BackHandler(enabled = true) {
        if (concertModeOn) {
            eyeControlEnabled = false  // Desactivar eye control al salir
            concertModeOn = false
        } else {
            onBack()
        }
    }

    // Metronome State
    var metronomeOn by remember { mutableStateOf(false) }
    var metronomeBpm by remember { mutableIntStateOf(60) }
    var metronomeTimeSignature by remember { mutableStateOf(4 to 4) }
    var showMetronomeSettings by remember { mutableStateOf(false) }
    var hasOpenedMetronomeSettings by remember { mutableStateOf(false) }
    
    // Metronome Engine
    val metronomeEngine = remember { MetronomeEngine() }
    
    // Cleanup metronome on dispose
    DisposableEffect(Unit) {
        onDispose {
            metronomeEngine.release()
        }
    }
    
    // Stop metronome when entering concert mode or exiting
    LaunchedEffect(concertModeOn) {
        if (concertModeOn && metronomeOn) {
            metronomeOn = false
            metronomeEngine.stop()
        }
    }



    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        tunerOn = granted
        if (!granted) runCatching { tunner.stopTuning(clearState = false) }
    }
    
    // Permission launcher para cámara (eye control)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            eyeControlEnabled = true
            if (!hasShownEyeControlHelp) {
                showEyeControlHelp = true
                hasShownEyeControlHelp = true
            }
        } else {
            eyeControlEnabled = false
        }
    }
    
    fun ensureCameraPermission(onGranted: () -> Unit) {
        val ok = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) onGranted() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
        @Suppress("DEPRECATION")
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
        val quickW = min(3, pageCount)
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
    // Archivo de anotaciones asociado al PDF
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
                            p.points.forEach { o -> arr.put(JSONArray().put(o.x).put(o.y)) }
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

    fun snapshotAnnotations(): Map<Int, PageAnnotations> {
        val copy = mutableMapOf<Int, PageAnnotations>()
        annotations.forEach { (idx, page) ->
            val pathsCopy = page.paths.map { p -> DrawingPath(p.points.toList(), p.color, p.strokeWidth, p.isEraser) }.toMutableList()
            copy[idx] = PageAnnotations(page.pageIndex, pathsCopy)
        }
        return copy
    }

    fun pushUndo() {
        // Guardamos snapshot previo
        val snap = snapshotAnnotations()
        // Evitar duplicados consecutivos
        if (undoStack.isNotEmpty() && undoStack.last() == snap) return
        undoStack.add(snap)
        // Mantener máximo 7
        while (undoStack.size > 7) undoStack.removeAt(0)
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val last = undoStack.removeAt(undoStack.size - 1)
        // Restaurar
        annotations.clear()
        last.forEach { (idx, page) ->
            val pathsCopy = page.paths.map { p -> DrawingPath(p.points.toList(), p.color, p.strokeWidth, p.isEraser) }.toMutableList()
            annotations[idx] = PageAnnotations(page.pageIndex, pathsCopy)
        }
        scheduleSave()
        return true
    }

    // Función para activar/toggle la herramienta del stylus
    fun activateStylusButtonTool() {
        val targetTool = stylusButtonTool.toToolString()
        Log.d(TAG, "activateStylusButtonTool: targetTool=$targetTool, selectedTool=$selectedTool, previousTool=$previousTool")

        if (selectedTool == targetTool) {
            // Si YA estás usando la herramienta del stylus,
            // presionar el botón te vuelve a la herramienta anterior
            selectedTool = previousTool
            Log.d(TAG, "Stylus button: volviendo a previousTool=$previousTool")
        } else {
            // Si NO estás usando la herramienta del stylus,
            // presionar el botón te cambia a ella y guarda la anterior
            previousTool = selectedTool
            selectedTool = targetTool
            Log.d(TAG, "Stylus button: cambiando a targetTool=$targetTool")
        }
    }

    // Función para manejar la pulsación del botón del stylus (desde el pen físico)
    val handleStylusButtonPress: () -> Unit = {
        Log.d(TAG, "handleStylusButtonPress INVOCADO")
        activateStylusButtonTool()
    }

    // Tutorial Logic
    LaunchedEffect(tunerOn) {
        if (tunerOn && tutorialState.step == TutorialStep.TUNER_BUTTON) {
            advanceTutorial(TutorialStep.TUNER_ACTIVE)
        }
    }

    LaunchedEffect(showTunerSettings) {
        if (showTunerSettings && tutorialState.step == TutorialStep.TUNER_ACTIVE) {
            advanceTutorial(TutorialStep.TUNER_MENU)
        } else if (!showTunerSettings && tutorialState.step == TutorialStep.TUNER_MENU) {
             advanceTutorial(TutorialStep.CONCERT_MODE)
        }
    }

    LaunchedEffect(concertModeOn) {
        if (concertModeOn && tutorialState.step == TutorialStep.CONCERT_MODE) {
            advanceTutorial(TutorialStep.WINK_DETECTOR)
        }
    }

    LaunchedEffect(eyeControlEnabled) {
        if (eyeControlEnabled && tutorialState.step == TutorialStep.WINK_DETECTOR) {
            advanceTutorial(TutorialStep.WINK_CALIBRATION)
        }
    }

    LaunchedEffect(showCalibrationMenu) {
        if (!showCalibrationMenu && tutorialState.step == TutorialStep.WINK_CALIBRATION) {
            advanceTutorial(TutorialStep.EXIT_CONCERT)
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

    val screenBg = MaterialTheme.colorScheme.background
    
    // Flag para controlar si estamos en medio de un gesto de borrado (para agrupar undo)
    var isErasingGesture by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = screenBg) {
        Box(Modifier.fillMaxSize()) {
            // Lambdas locales necesarias para la top bar
            val onTunerClickLocal: () -> Unit = {
                if (!tunerOn) {
                    ensureMicPermission { tunerOn = true }
                } else {
                    tunerOn = false
                }
            }

            val onConcertClickLocal: () -> Unit = { concertModeOn = !concertModeOn }

            val onUpdateTutorialTargetLocal: ((ViewerTutorialTargets) -> ViewerTutorialTargets) -> Unit = { transform ->
                tutorialTargets = transform(tutorialTargets)
            }

            // Top bar para tablets
            if (deviceType != DeviceType.PHONE) {
                StyledTopBar(
                    onBack = onBack,
                    tunerOn = tunerOn,
                    concertModeOn = concertModeOn,
                    highlightBack = false,
                    onTunerClick = onTunerClickLocal,
                    onConcertClick = onConcertClickLocal,
                    showUndo = LocalConfiguration.current.screenWidthDp >= 600,
                    onUndo = { undo() },
                    darkMode = darkMode,
                    onTunerPositioned = { rect -> onUpdateTutorialTargetLocal { vt -> vt.copy(tunerButton = rect) } },
                    onConcertPositioned = { rect -> onUpdateTutorialTargetLocal { vt -> vt.copy(concertButton = rect) } },
                    onBackPositioned = { rect -> onUpdateTutorialTargetLocal { vt -> vt.copy(backButton = rect) } },
                    metronomeOn = metronomeOn,
                    onMetronomeClick = {
                        if (metronomeOn) {
                            metronomeOn = false
                            metronomeEngine.stop()
                        } else {
                            if (!hasOpenedMetronomeSettings) {
                                hasOpenedMetronomeSettings = true
                                showMetronomeSettings = true
                            }
                            metronomeOn = true
                            val pattern = when (metronomeTimeSignature.first) {
                                2 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK)
                                3 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK)
                                4 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.MEDIUM, AccentLevel.WEAK)
                                6 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK, AccentLevel.MEDIUM, AccentLevel.WEAK, AccentLevel.WEAK)
                                else -> List(metronomeTimeSignature.first) { if (it == 0) AccentLevel.STRONG else AccentLevel.WEAK }
                            }
                            metronomeEngine.start(scope, metronomeBpm, pattern)
                        }
                    },
                    onMetronomeLongClick = {
                        if (!metronomeOn) {
                            metronomeOn = true
                            val pattern = when (metronomeTimeSignature.first) {
                                2 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK)
                                3 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK)
                                4 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.MEDIUM, AccentLevel.WEAK)
                                6 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK, AccentLevel.MEDIUM, AccentLevel.WEAK, AccentLevel.WEAK)
                                else -> List(metronomeTimeSignature.first) { if (it == 0) AccentLevel.STRONG else AccentLevel.WEAK }
                            }
                            metronomeEngine.start(scope, metronomeBpm, pattern)
                        }
                        showMetronomeSettings = true
                    },
                    centerContent = {
                        if (tunerOn) {
                            TunnerSmall(
                                tunner = tunner,
                                isDaltonic = isDaltonic,
                                modifier = Modifier
                                    .then(if (tunerExtendedMode) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.8f))
                                    .height(44.dp)
                                    .onGloballyPositioned { coords -> onUpdateTutorialTargetLocal { vt -> vt.copy(tunerDisplay = coords.boundsInRoot()) } },
                                onClick = { showTunerSettings = true }
                            )
                        }
                    }
                )
            }

            if (concertModeOn) {
                // ===== MODO CONCIERTO =====
                fun goToNextPage() {
                    val currentIndex = listState.firstVisibleItemIndex
                    if (currentIndex < pageCount - 1) {
                        scope.launch {
                            listState.animateScrollToItem(currentIndex + 1)
                        }
                    }
                }

                val eyeControlState = rememberEyeControlState(
                    enabled = eyeControlEnabled,
                    onWinkDetected = { goToNextPage() }
                )

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
                        items(count = pageCount) { index ->
                            val bmp = pageBitmaps.getOrNull(index)
                            PdfPageItem(
                                index = index,
                                bitmap = bmp,
                                showLoadingLabel = bmp == null,
                                editMode = false,
                                annotations = annotations.getOrPut(index) { PageAnnotations(index) },
                                selectedTool = "none",
                                currentColor = currentColor,
                                currentStrokeWidth = markerStrokeWidth,
                                eraserRadiusNorm = eraserRadiusNorm,
                                smoothingEnabled = smoothingEnabled,
                                onPathAdded = {},
                                onErase = {},
                                darkMode = darkMode
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }

                // Botón de casa
                val highlightBack = tutorialState.step == TutorialStep.EXIT_CONCERT
                val backScale = if (highlightBack) {
                    val infiniteTransition = rememberInfiniteTransition(label = "backPulseScale")
                    infiniteTransition.animateFloat(
                        initialValue = 1.5f, targetValue = 1.8f,
                        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                        label = "scale"
                    ).value
                } else 1f

                val backColor = if (highlightBack) {
                    val infiniteTransition = rememberInfiniteTransition(label = "backPulseColor")
                    infiniteTransition.animateColor(
                        initialValue = if (darkMode) Color.White else Color(0xFF111111),
                        targetValue = MaterialTheme.colorScheme.primary,
                        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse),
                        label = "color"
                    ).value
                } else if (darkMode) Color.White else Color(0xFF111111)

                val homeIconColor by animateColorAsState(
                    targetValue = if (highlightBack) backColor else when {
                        !eyeControlEnabled -> if (darkMode) Color.White else Color(0xFF111111)
                        eyeControlState.winkState == WinkState.WAITING_FOR_OPEN -> Color(0xFF4CAF50)
                        eyeControlState.winkState == WinkState.WINK_STARTED -> Color(0xFFFFC107)
                        eyeControlState.faceDetected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    animationSpec = tween(300), label = "homeIconColor"
                )

                IconButton(
                    onClick = {
                        if (highlightBack) advanceTutorial(TutorialStep.FINISHED)
                        eyeControlEnabled = false
                        concertModeOn = false
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .scale(backScale)
                        .onGloballyPositioned { tutorialTargets = tutorialTargets.copy(backButton = it.boundsInRoot()) }
                ) {
                    Icon(Icons.Default.Home, contentDescription = strings().backDescription, tint = homeIconColor)
                }

                LaunchedEffect(showEyeControlHelp) {
                    if (showEyeControlHelp) { delay(5000); showEyeControlHelp = false }
                }

                EyeControlButton(
                    eyeControlEnabled = eyeControlEnabled,
                    faceDetected = eyeControlState.faceDetected,
                    winkState = eyeControlState.winkState,
                    onClick = {
                        if (eyeControlEnabled) { eyeControlEnabled = false; showEyeControlHelp = false }
                        else ensureCameraPermission {
                            eyeControlEnabled = true
                            if (!hasShownEyeControlHelp) { showEyeControlHelp = true; hasShownEyeControlHelp = true }
                        }
                    },
                    onLongClick = { showCalibrationMenu = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .onGloballyPositioned { tutorialTargets = tutorialTargets.copy(winkButton = it.boundsInRoot()) }
                )

                EyeCalibrationDialog(
                    visible = showCalibrationMenu,
                    onDismiss = { showCalibrationMenu = false },
                    onStartCalibration = { showCalibrationMenu = false; calibrationStep = CalibrationStep.NORMAL_EYES },
                    onResetCalibration = { calibrationManager.clearCalibration(); isCalibrated = false; showCalibrationMenu = false },
                    isCalibrated = isCalibrated
                )

                EyeCalibrationFlow(
                    visible = calibrationStep != CalibrationStep.MENU,
                    onComplete = { isCalibrated = true; calibrationStep = CalibrationStep.MENU },
                    onCancel = { calibrationStep = CalibrationStep.MENU }
                )

                if (eyeControlEnabled) {
                    EyeControlHelpCard(
                        visible = showEyeControlHelp,
                        faceDetected = eyeControlState.faceDetected,
                        winkState = eyeControlState.winkState,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)
                    )
                }
            } else {
                // ===== MODO EDICIÓN =====
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val width = with(density) { maxWidth.toPx() }
                    val height = with(density) { maxHeight.toPx() }

                    // Detectar botón del stylus globalmente
                    var stylusButtonWasPressed by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInteropFilter { event ->
                                // Detectar botón del stylus globalmente
                                if (event.getToolType(0) == android.view.MotionEvent.TOOL_TYPE_STYLUS) {
                                    val buttonPressed = (event.buttonState and android.view.MotionEvent.BUTTON_STYLUS_PRIMARY) != 0 ||
                                                      (event.buttonState and android.view.MotionEvent.BUTTON_STYLUS_SECONDARY) != 0

                                    // Detectar transición de no-presionado a presionado
                                    if (buttonPressed && !stylusButtonWasPressed) {
                                        Log.d(TAG, "Botón del stylus DETECTADO GLOBALMENTE - ejecutando activateStylusButtonTool")
                                        activateStylusButtonTool()
                                    }
                                    stylusButtonWasPressed = buttonPressed
                                }
                                false
                            }
                    ) {

                    if (deviceType == DeviceType.PHONE) {
                        PdfEditModePhone(
                            listState = listState,
                            isPinching = isPinching,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            pageCount = pageCount,
                            pageBitmaps = pageBitmaps,
                            annotations = annotations,
                            selectedTool = selectedTool,
                            currentColor = currentColor,
                            markerStrokeWidth = markerStrokeWidth,
                            eraserRadiusNorm = eraserRadiusNorm,
                            smoothingEnabled = smoothingEnabled,
                            onPathAdded = { index, path ->
                                pushUndo()
                                val finalColor = if (selectedTool == "highlighter") currentColor.copy(alpha = 0.5f) else currentColor
                                annotations.getOrPut(index) { PageAnnotations(index) }.paths.add(path.copy(color = finalColor))
                                scheduleSave()
                            },
                            onEraseStart = { _ ->
                                if (!isErasingGesture) { pushUndo(); isErasingGesture = true }
                            },
                            onErase = { index, eraserPoints ->
                                val page = annotations.getOrPut(index) { PageAnnotations(index) }
                                if (performErase(page, eraserPoints, eraserRadiusNorm)) scheduleSave()
                            },
                            onEraseEnd = { _ -> isErasingGesture = false },
                            darkMode = darkMode,
                            paletteColors = paletteColors,
                            selectedPaletteIndex = selectedPaletteIndex,
                            highlighterStrokeWidth = highlighterStrokeWidth,
                            showColorPicker = showColorPicker,
                            language = language,
                            onBack = onBack,
                            tunerOn = tunerOn,
                            concertModeOn = concertModeOn,
                            onTunerClick = { if (!tunerOn) ensureMicPermission { tunerOn = true } else tunerOn = false },
                            onConcertClick = { concertModeOn = !concertModeOn },
                            tunner = tunner,
                            isDaltonic = isDaltonic,
                            tunerExtendedMode = tunerExtendedMode,
                            showTunerSettings = showTunerSettings,
                            onToggleDaltonic = onToggleDaltonic,
                            onToggleExtendedMode = { tunerExtendedMode = !tunerExtendedMode },
                            onUpdateTutorialTarget = { transform -> tutorialTargets = transform(tutorialTargets) },
                            onSelectTool = { selectedTool = it },
                            onPaletteSlotClicked = { index -> if (selectedPaletteIndex == index) showColorPicker = true else selectedPaletteIndex = index },
                            onStrokeChange = { value ->
                                when (selectedTool) {
                                    "marker" -> markerStrokeWidth = value
                                    "highlighter" -> highlighterStrokeWidth = value
                                    "eraser" -> eraserRadiusNorm = value.coerceIn(0.015f, 0.1f)
                                }
                            },
                            onToggleSmoothing = { smoothingEnabled = !smoothingEnabled },
                            onColorSelected = { paletteColors[selectedPaletteIndex] = it },
                            onDismissColorPicker = { showColorPicker = false },
                            onDismissTunerSettings = { showTunerSettings = false },
                            onTransform = { zoom, centroid, panX, panY ->
                                isPinching = zoom != 1f
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                if (kotlin.math.abs(newScale - scale) > 0.001f) {
                                    val centerX = width / 2f; val centerY = height / 2f
                                    val focusX = (centroid.x - centerX - offsetX) / scale
                                    val focusY = (centroid.y - centerY - offsetY) / scale
                                    scale = newScale
                                    offsetX = centroid.x - centerX - focusX * scale
                                    offsetY = centroid.y - centerY - focusY * scale
                                }
                                if (scale > 1f) {
                                    val maxX = (width * (scale - 1f)) / 2f; val maxY = (height * (scale - 1f)) / 2f
                                    offsetX = (offsetX + panX).coerceIn(-maxX, maxX)
                                    offsetY = (offsetY + panY).coerceIn(-maxY, maxY)
                                } else { scale = 1f; offsetX = 0f; offsetY = 0f }
                            },
                            onShowTunerSettings = { showTunerSettings = true },
                            onUndo = { undo() },
                            hasUndo = undoStack.isNotEmpty(),
                            onStylusDetected = { stylusDetected = true },
                            onStylusButtonPressed = handleStylusButtonPress
                        )
                    } else {
                        PdfEditModeTablet(
                            undoStack = undoStack,
                            listState = listState,
                            isPinching = isPinching,
                            scale = scale,
                            offsetX = offsetX,
                            offsetY = offsetY,
                            pageCount = pageCount,
                            pageBitmaps = pageBitmaps,
                            annotations = annotations,
                            selectedTool = selectedTool,
                            currentColor = currentColor,
                            markerStrokeWidth = markerStrokeWidth,
                            eraserRadiusNorm = eraserRadiusNorm,
                            smoothingEnabled = smoothingEnabled,
                            onPathAdded = { index, path ->
                                pushUndo()
                                val finalColor = if (selectedTool == "highlighter") currentColor.copy(alpha = 0.5f) else currentColor
                                annotations.getOrPut(index) { PageAnnotations(index) }.paths.add(path.copy(color = finalColor))
                                scheduleSave()
                            },
                            onEraseStart = { _ ->
                                if (!isErasingGesture) { pushUndo(); isErasingGesture = true }
                            },
                            onErase = { index, eraserPoints ->
                                val page = annotations.getOrPut(index) { PageAnnotations(index) }
                                if (performErase(page, eraserPoints, eraserRadiusNorm)) scheduleSave()
                            },
                            onEraseEnd = { _ -> isErasingGesture = false },
                            darkMode = darkMode,
                            paletteColors = paletteColors,
                            selectedPaletteIndex = selectedPaletteIndex,
                            highlighterStrokeWidth = highlighterStrokeWidth,
                            showColorPicker = showColorPicker,
                            language = language,
                            onBack = onBack,
                            tunerOn = tunerOn,
                            concertModeOn = concertModeOn,
                            onTunerClick = { if (!tunerOn) ensureMicPermission { tunerOn = true } else tunerOn = false },
                            onConcertClick = { concertModeOn = !concertModeOn },
                            tunner = tunner,
                            isDaltonic = isDaltonic,
                            tunerExtendedMode = tunerExtendedMode,
                            showTunerSettings = showTunerSettings,
                            onToggleDaltonic = onToggleDaltonic,
                            onToggleExtendedMode = { tunerExtendedMode = !tunerExtendedMode },
                            onUpdateTutorialTarget = { transform -> tutorialTargets = transform(tutorialTargets) },
                            onSelectTool = { selectedTool = it },
                            onPaletteSlotClicked = { index -> if (selectedPaletteIndex == index) showColorPicker = true else selectedPaletteIndex = index },
                            onStrokeChange = { value ->
                                when (selectedTool) {
                                    "marker" -> markerStrokeWidth = value
                                    "highlighter" -> highlighterStrokeWidth = value
                                    "eraser" -> eraserRadiusNorm = value.coerceIn(0.015f, 0.1f)
                                }
                            },
                            onToggleSmoothing = { smoothingEnabled = !smoothingEnabled },
                            onColorSelected = { paletteColors[selectedPaletteIndex] = it },
                            onDismissColorPicker = { showColorPicker = false },
                            onDismissTunerSettings = { showTunerSettings = false },
                            onTransform = { zoom, centroid, panX, panY ->
                                isPinching = zoom != 1f
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                if (kotlin.math.abs(newScale - scale) > 0.001f) {
                                    val centerX = width / 2f; val centerY = height / 2f
                                    val focusX = (centroid.x - centerX - offsetX) / scale
                                    val focusY = (centroid.y - centerY - offsetY) / scale
                                    scale = newScale
                                    offsetX = centroid.x - centerX - focusX * scale
                                    offsetY = centroid.y - centerY - focusY * scale
                                }
                                if (scale > 1f) {
                                    val maxX = (width * (scale - 1f)) / 2f; val maxY = (height * (scale - 1f)) / 2f
                                    offsetX = (offsetX + panX).coerceIn(-maxX, maxX)
                                    offsetY = (offsetY + panY).coerceIn(-maxY, maxY)
                                } else { scale = 1f; offsetX = 0f; offsetY = 0f }
                            },
                            onShowTunerSettings = { showTunerSettings = true },
                            onUndo = { undo() },
                            // Stylus parameters
                            stylusDetected = stylusDetected,
                            stylusButtonTool = stylusButtonTool,
                            onStylusDetected = { stylusDetected = true },
                            onStylusButtonClick = { activateStylusButtonTool() },
                            onStylusButtonToolChange = { stylusButtonTool = it },
                            onStylusButtonPressed = handleStylusButtonPress,
                            // Callbacks para cambio de tamaño de herramientas
                            onMarkerStrokeChange = { markerStrokeWidth = it },
                            onHighlighterStrokeChange = { highlighterStrokeWidth = it },
                            onEraserRadiusChange = { eraserRadiusNorm = it.coerceIn(0.015f, 0.1f) }
                        )
                    }
                    }
                }
            }

            TutorialOverlay(
                state = tutorialState,
                isTablet = deviceType != DeviceType.PHONE,
                onNext = {
                    when (tutorialState.step) {
                        TutorialStep.TOOLBOX -> advanceTutorial(TutorialStep.TUNER_BUTTON)
                        TutorialStep.TUNER_BUTTON -> { advanceTutorial(TutorialStep.TUNER_ACTIVE); if (!tunerOn) ensureMicPermission { tunerOn = true } }
                        TutorialStep.TUNER_ACTIVE -> { advanceTutorial(TutorialStep.TUNER_MENU); showTunerSettings = true }
                        TutorialStep.TUNER_MENU -> { showTunerSettings = false; advanceTutorial(TutorialStep.CONCERT_MODE) }
                        TutorialStep.FINISHED -> onTutorialComplete()
                        else -> {}
                    }
                },
                onDismiss = onTutorialComplete
            )
        }

        // Metronome Settings Panel
        if (showMetronomeSettings) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { showMetronomeSettings = false },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(modifier = Modifier.padding(top = 80.dp)) {
                    MetronomeSettingsPanel(
                        visible = showMetronomeSettings,
                        bpm = metronomeBpm,
                        timeSignature = metronomeTimeSignature,
                        onBpmChange = { newBpm -> 
                            metronomeBpm = newBpm
                            if (metronomeOn) {
                                val pattern = when (metronomeTimeSignature.first) {
                                    1 -> listOf(AccentLevel.STRONG)
                                    2 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK)
                                    3 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK)
                                    4 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.MEDIUM, AccentLevel.WEAK)
                                    else -> List(metronomeTimeSignature.first) { if (it == 0) AccentLevel.STRONG else AccentLevel.WEAK }
                                }
                                metronomeEngine.updateConfig(newBpm, pattern)
                            }
                        },
                        onTimeSignatureChange = { newSig ->
                            metronomeTimeSignature = newSig
                            val pattern = when (newSig.first) {
                                1 -> listOf(AccentLevel.STRONG)
                                2 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK)
                                3 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.WEAK)
                                4 -> listOf(AccentLevel.STRONG, AccentLevel.WEAK, AccentLevel.MEDIUM, AccentLevel.WEAK)
                                else -> List(newSig.first) { if (it == 0) AccentLevel.STRONG else AccentLevel.WEAK }
                            }
                            if (metronomeOn) {
                                metronomeEngine.updateConfig(metronomeBpm, pattern)
                            }
                        },
                        onDismiss = { showMetronomeSettings = false }
                    )
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun PdfEditModeTablet(
    undoStack: List<Map<Int, PageAnnotations>>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    isPinching: Boolean,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    pageCount: Int,
    pageBitmaps: List<Bitmap?>,
    annotations: Map<Int, PageAnnotations>,
    selectedTool: String,
    currentColor: Color,
    markerStrokeWidth: Float,
    eraserRadiusNorm: Float,
    smoothingEnabled: Boolean,
    onPathAdded: (Int, DrawingPath) -> Unit,
    onEraseStart: (Int) -> Unit,
    onErase: (Int, List<androidx.compose.ui.geometry.Offset>) -> Unit,
    onEraseEnd: (Int) -> Unit,
    darkMode: Boolean,
    paletteColors: List<Color>,
    selectedPaletteIndex: Int,
    highlighterStrokeWidth: Float,
    showColorPicker: Boolean,
    language: Language,
    onBack: () -> Unit,
    tunerOn: Boolean,
    concertModeOn: Boolean,
    onTunerClick: () -> Unit,
    onConcertClick: () -> Unit,
    tunner: AudioTuner,
    isDaltonic: Boolean,
    // Callbacks para cambio de tamaño de herramientas
    onMarkerStrokeChange: (Float) -> Unit = {},
    onHighlighterStrokeChange: (Float) -> Unit = {},
    onEraserRadiusChange: (Float) -> Unit = {},
    tunerExtendedMode: Boolean,
    showTunerSettings: Boolean,
    onToggleDaltonic: () -> Unit,
    onToggleExtendedMode: () -> Unit,
    onUpdateTutorialTarget: ((ViewerTutorialTargets) -> ViewerTutorialTargets) -> Unit,
    onSelectTool: (String) -> Unit,
    onPaletteSlotClicked: (Int) -> Unit,
    onStrokeChange: (Float) -> Unit,
    onToggleSmoothing: () -> Unit,
    onColorSelected: (Color) -> Unit,
    onDismissColorPicker: () -> Unit,
    onDismissTunerSettings: () -> Unit,
    onTransform: (Float, androidx.compose.ui.geometry.Offset, Float, Float) -> Unit,
    onShowTunerSettings: () -> Unit,
    onUndo: () -> Unit,
    // Stylus parameters - nuevo sistema basado en detección por uso
    stylusDetected: Boolean = false,
    stylusButtonTool: StylusTool = StylusTool.MARKER,
    onStylusDetected: () -> Unit = {},
    onStylusButtonClick: () -> Unit = {},
    onStylusButtonToolChange: (StylusTool) -> Unit = {},
    onStylusButtonPressed: () -> Unit = {}
) {
    val topBarHeight = 64.dp
    val config = LocalConfiguration.current
    val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT

    // Posición de la barra de herramientas para el tutorial
    var toolboxBounds by remember { mutableStateOf<Rect?>(null) }
    
    // Posición de los botones individuales
    var markerButtonBounds by remember { mutableStateOf<Rect?>(null) }
    var highlighterButtonBounds by remember { mutableStateOf<Rect?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topBarHeight)
            .then(
                if (selectedTool == "none") {
                    Modifier.pointerInput(selectedTool) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            onTransform(zoom, centroid, pan.x, pan.y)
                        }
                    }
                } else Modifier
            )
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = !isPinching && selectedTool == "none",
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
            items(count = pageCount) { index ->
                val bmp = pageBitmaps.getOrNull(index)
                PdfPageItem(
                    index = index,
                    bitmap = bmp,
                    showLoadingLabel = bmp == null,
                    editMode = true,
                    annotations = annotations[index] ?: PageAnnotations(index),
                    selectedTool = selectedTool,
                    currentColor = currentColor,
                    currentStrokeWidth = when (selectedTool) {
                        "marker" -> markerStrokeWidth
                        "highlighter" -> highlighterStrokeWidth
                        else -> markerStrokeWidth
                    },
                    eraserRadiusNorm = eraserRadiusNorm,
                    smoothingEnabled = smoothingEnabled,
                    onPathAdded = { path -> onPathAdded(index, path) },
                    onErase = { offsets -> onErase(index, offsets) },
                    onEraseStart = { onEraseStart(index) },
                    onEraseEnd = { onEraseEnd(index) },
                    darkMode = darkMode,
                    onStylusDetected = onStylusDetected,
                    onStylusButtonPressed = onStylusButtonPressed
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        // Barra lateral de herramientas sólo en modo edición
        val config = LocalConfiguration.current
        val isPortrait = config.orientation == Configuration.ORIENTATION_PORTRAIT
        
        Box(
            modifier = Modifier
                .fillMaxHeight(if (isPortrait) 0.75f else 1f)
                .align(Alignment.CenterStart)
        ) {
            StyledLeftToolBar(
                selectedTool = selectedTool,
                paletteColors = paletteColors,
                selectedPaletteIndex = selectedPaletteIndex,
                strokeWidth = when (selectedTool) {
                    "marker" -> markerStrokeWidth
                    "highlighter" -> highlighterStrokeWidth
                    "eraser" -> eraserRadiusNorm
                    else -> 0f
                },
                smoothingEnabled = smoothingEnabled,
                onSelectTool = onSelectTool,
                onPaletteSlotClicked = onPaletteSlotClicked,
                onStrokeChange = onStrokeChange,
                onToggleSmoothing = onToggleSmoothing,
                darkMode = darkMode,
                language = language,
                onToolboxPositioned = { rect -> 
                    toolboxBounds = rect
                    onUpdateTutorialTarget { it.copy(toolbox = rect) } 
                },
                hasUndo = undoStack.isNotEmpty(),
                onUndo = onUndo,
                onMarkerLongPress = { },
                onHighlighterLongPress = { },
                onMarkerButtonPositioned = { rect -> markerButtonBounds = rect },
                onHighlighterButtonPositioned = { rect -> highlighterButtonBounds = rect },
                showSlider = false,
                // Stylus parameters - nuevo sistema
                stylusDetected = stylusDetected,
                stylusButtonTool = stylusButtonTool,
                onStylusButtonClick = onStylusButtonClick,
                onStylusButtonToolChange = onStylusButtonToolChange,
                // Parámetros para los popups de tamaño
                markerStrokeWidth = markerStrokeWidth,
                highlighterStrokeWidth = highlighterStrokeWidth,
                eraserRadiusNorm = eraserRadiusNorm,
                onMarkerStrokeChange = onMarkerStrokeChange,
                onHighlighterStrokeChange = onHighlighterStrokeChange,
                onEraserRadiusChange = onEraserRadiusChange
            )
        }

        if (showColorPicker) {
            ColorPickerDialog(
                currentColor = currentColor,
                onColorSelected = onColorSelected,
                onDismiss = onDismissColorPicker
            )
        }

        if (showTunerSettings) {
            TunerSettingsDialog(
                tuner = tunner,
                isDaltonic = isDaltonic,
                onToggleDaltonic = onToggleDaltonic,
                extendedMode = tunerExtendedMode,
                onToggleExtendedMode = onToggleExtendedMode,
                onDismiss = onDismissTunerSettings
            )
        }
    }
}
@Suppress("UNUSED_PARAMETER")
@Composable
private fun PdfEditModePhone(
    listState: androidx.compose.foundation.lazy.LazyListState,
    isPinching: Boolean,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    pageCount: Int,
    pageBitmaps: List<Bitmap?>,
    annotations: Map<Int, PageAnnotations>,
    selectedTool: String,
    currentColor: Color,
    markerStrokeWidth: Float,
    eraserRadiusNorm: Float,
    smoothingEnabled: Boolean,
    onPathAdded: (Int, DrawingPath) -> Unit,
    onEraseStart: (Int) -> Unit,
    onErase: (Int, List<androidx.compose.ui.geometry.Offset>) -> Unit,
    onEraseEnd: (Int) -> Unit,
    darkMode: Boolean,
    paletteColors: List<Color>,
    selectedPaletteIndex: Int,
    highlighterStrokeWidth: Float,
    showColorPicker: Boolean,
    language: Language,
    onBack: () -> Unit,
    tunerOn: Boolean,
    concertModeOn: Boolean,
    onTunerClick: () -> Unit,
    onConcertClick: () -> Unit,
    tunner: AudioTuner,
    isDaltonic: Boolean,
    tunerExtendedMode: Boolean,
    showTunerSettings: Boolean,
    onToggleDaltonic: () -> Unit,
    onToggleExtendedMode: () -> Unit,
    onUpdateTutorialTarget: ((ViewerTutorialTargets) -> ViewerTutorialTargets) -> Unit,
    onSelectTool: (String) -> Unit,
    onPaletteSlotClicked: (Int) -> Unit,
    onStrokeChange: (Float) -> Unit,
    onToggleSmoothing: () -> Unit,
    onColorSelected: (Color) -> Unit,
    onDismissColorPicker: () -> Unit,
    onDismissTunerSettings: () -> Unit,
    onTransform: (Float, androidx.compose.ui.geometry.Offset, Float, Float) -> Unit,
    onShowTunerSettings: () -> Unit,
    onUndo: () -> Unit,
    hasUndo: Boolean,
    // Stylus parameters
    onStylusDetected: () -> Unit = {},
    onStylusButtonPressed: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            StyledTopBar(
                onBack = onBack,
                tunerOn = tunerOn,
                concertModeOn = concertModeOn,
                highlightBack = false,
                onTunerClick = onTunerClick,
                onConcertClick = onConcertClick,
                darkMode = darkMode,
                onTunerPositioned = { rect -> onUpdateTutorialTarget { it.copy(tunerButton = rect) } },
                onConcertPositioned = { rect -> onUpdateTutorialTarget { it.copy(concertButton = rect) } },
                onBackPositioned = { rect -> onUpdateTutorialTarget { it.copy(backButton = rect) } },
                centerContent = {
                    if (tunerOn) {
                        TunnerSmall(
                            tunner = tunner,
                            isDaltonic = isDaltonic,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .onGloballyPositioned { coords ->
                                    onUpdateTutorialTarget {
                                        it.copy(
                                            tunerDisplay = coords.boundsInRoot()
                                        )
                                    }
                                },
                            onClick = onShowTunerSettings
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Bottom Toolbar for Phone
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                // Tools: Marker, Highlighter, Eraser
                IconButton(onClick = { onSelectTool("marker") }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Marker",
                        tint = if (selectedTool == "marker") currentColor else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { onSelectTool("highlighter") }) {
                    Icon(
                        Icons.Default.Brush,
                        contentDescription = "Highlighter",
                        tint = if (selectedTool == "highlighter") currentColor else MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { onSelectTool("eraser") }) {
                    Icon(
                        Icons.Default.Delete, // Or eraser icon
                        contentDescription = "Eraser",
                        tint = if (selectedTool == "eraser") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.weight(1f))

                // Palette
                paletteColors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .padding(4.dp)
                            .background(
                                color = color,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                            .clickable { onPaletteSlotClicked(index) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (selectedTool == "none") {
                            Modifier.pointerInput(selectedTool) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    onTransform(zoom, centroid, pan.x, pan.y)
                                }
                            }
                        } else Modifier
                    )
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = !isPinching && selectedTool == "none",
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
                    items(count = pageCount) { index ->
                        val bmp = pageBitmaps.getOrNull(index)
                        PdfPageItem(
                            index = index,
                            bitmap = bmp,
                            showLoadingLabel = bmp == null,
                            editMode = true,
                            annotations = annotations[index] ?: PageAnnotations(index),
                            selectedTool = selectedTool,
                            currentColor = currentColor,
                            currentStrokeWidth = when (selectedTool) {
                                "marker" -> markerStrokeWidth
                                "highlighter" -> highlighterStrokeWidth
                                else -> markerStrokeWidth
                            },
                            eraserRadiusNorm = eraserRadiusNorm,
                            smoothingEnabled = smoothingEnabled,
                            onPathAdded = { path -> onPathAdded(index, path) },
                            onErase = { offsets -> onErase(index, offsets) },
                            onEraseStart = { onEraseStart(index) },
                            onEraseEnd = { onEraseEnd(index) },
                            darkMode = darkMode,
                            onStylusDetected = onStylusDetected,
                            onStylusButtonPressed = onStylusButtonPressed
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                if (showColorPicker) {
                    ColorPickerDialog(
                        currentColor = currentColor,
                        onColorSelected = onColorSelected,
                        onDismiss = onDismissColorPicker
                    )
                }

                if (showTunerSettings) {
                    TunerSettingsDialog(
                        tuner = tunner,
                        isDaltonic = isDaltonic,
                        onToggleDaltonic = onToggleDaltonic,
                        extendedMode = tunerExtendedMode,
                        onToggleExtendedMode = onToggleExtendedMode,
                        onDismiss = onDismissTunerSettings
                    )
                }
            }
        }
    }


    // moved helper functions to top-level below
}

private fun distancePointToSegment(
    p: androidx.compose.ui.geometry.Offset,
    a: androidx.compose.ui.geometry.Offset,
    b: androidx.compose.ui.geometry.Offset
): Float {
    val ax = a.x
    val ay = a.y
    val bx = b.x
    val by = b.y
    val vx = bx - ax
    val vy = by - ay
    val wx = p.x - ax
    val wy = p.y - ay
    val vv = vx * vx + vy * vy
    val t = if (vv > 0f) ((wx * vx + wy * vy) / vv).coerceIn(0f, 1f) else 0f
    val nx = ax + t * vx
    val ny = ay + t * vy
    val dx = p.x - nx
    val dy = p.y - ny
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun performErase(
    page: PageAnnotations,
    eraserPoints: List<androidx.compose.ui.geometry.Offset>,
    threshold: Float
): Boolean {
    val resultPaths = mutableListOf<DrawingPath>()
    var changed = false

    page.paths.forEach { p ->
        val pts = p.points
        if (pts.isEmpty()) {
            resultPaths.add(p)
            return@forEach
        }

        // Optimization: Check bounding box first
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        pts.forEach { pt ->
            if (pt.x < minX) minX = pt.x
            if (pt.x > maxX) maxX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.y > maxY) maxY = pt.y
        }
        // Expand bounds by threshold
        minX -= threshold
        maxX += threshold
        minY -= threshold
        maxY += threshold

        val inBounds = eraserPoints.any { e ->
            e.x >= minX && e.x <= maxX && e.y >= minY && e.y <= maxY
        }
        if (!inBounds) {
            resultPaths.add(p)
            return@forEach
        }

        val splitParts = mutableListOf<DrawingPath>()
        val currentSegmentPoints = mutableListOf<androidx.compose.ui.geometry.Offset>()
        var pathWasHit = false

        for (i in pts.indices) {
            val point = pts[i]
            // Check if point is hit
            val isPointHit = eraserPoints.any { e ->
                val dx = e.x - point.x
                val dy = e.y - point.y
                (dx * dx + dy * dy) <= (threshold * threshold)
            }

            if (isPointHit) {
                pathWasHit = true
                if (currentSegmentPoints.isNotEmpty()) {
                    splitParts.add(p.copy(points = currentSegmentPoints.toList()))
                    currentSegmentPoints.clear()
                }
                continue
            }

            // Point is valid
            if (currentSegmentPoints.isEmpty()) {
                currentSegmentPoints.add(point)
            } else {
                val prev = currentSegmentPoints.last()
                // Check segment prev-point
                val isSegmentHit = eraserPoints.any { e ->
                    distancePointToSegment(e, prev, point) <= threshold
                }

                if (isSegmentHit) {
                    pathWasHit = true
                    splitParts.add(p.copy(points = currentSegmentPoints.toList()))
                    currentSegmentPoints.clear()
                    currentSegmentPoints.add(point)
                } else {
                    currentSegmentPoints.add(point)
                }
            }
        }

        if (currentSegmentPoints.isNotEmpty()) {
            splitParts.add(p.copy(points = currentSegmentPoints.toList()))
        }

        if (pathWasHit) {
            changed = true
            resultPaths.addAll(splitParts)
        } else {
            resultPaths.add(p)
        }
    }

    if (changed) {
        page.paths.clear()
        page.paths.addAll(resultPaths)
    }
    return changed
}
