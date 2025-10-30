package com.example.mypdf

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Popup
import android.util.LruCache
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

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

    val lastPage = remember(file.path) { getLastPage(context, file).coerceAtLeast(0) }
    var didScrollToLast by remember(file.path) { mutableStateOf(false) }

    LaunchedEffect(pageCount, lastPage, showInitialOverlay) {
        if (pageCount > 0 && !didScrollToLast) {
            val target = lastPage.coerceIn(0, pageCount - 1)
            if (!showInitialOverlay) {
                try { listState.scrollToItem(target) } catch (_: Exception) {}
                didScrollToLast = true
            }
        }
    }

    LaunchedEffect(listState, pageCount) {
        if (pageCount <= 0) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .collectLatest { idx ->
                saveLastPage(context, file, idx.coerceIn(0, pageCount - 1))
            }
    }

    val maxParallel = minOf(3, Runtime.getRuntime().availableProcessors())
    val semaphore = remember { Semaphore(maxParallel) }

    LaunchedEffect(listState, pageCount, screenWidthPx) {
        if (pageCount <= 0 || screenWidthPx <= 0) return@LaunchedEffect

        snapshotFlow {
            val first = listState.firstVisibleItemIndex
            val visibles = listState.layoutInfo.visibleItemsInfo.map { it.index }.toSet()
            Pair(first, visibles)
        }.collectLatest { (firstIndex, visibleSet) ->
            initialLoading = pageBitmaps.count { it != null } == 0

            val prefetchBefore = 3
            val prefetchAfter = 8
            val start = (firstIndex - prefetchBefore).coerceAtLeast(0)
            val end = (firstIndex + prefetchAfter).coerceAtMost(pageCount - 1)

            val prioritized = buildList {
                addAll(visibleSet.sorted())
                var offset = 1
                while (true) {
                    val p = firstIndex + offset
                    val m = firstIndex - offset
                    var added = false
                    if (p <= end) { add(p); added = true }
                    if (m >= start) { add(m); added = true }
                    if (!added) break
                    offset++
                }
            }.distinct().filter { it in start..end }

            coroutineScope {
                val quickW = minOf(screenWidthPx, 600)
                prioritized.forEach { idx ->
                    if (pageBitmaps.getOrNull(idx) == null) {
                        launch(Dispatchers.Default) {
                            val bmpQuick = holder.renderPageQuick(idx, quickW)
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

            coroutineScope {
                val hiPriority = buildSet {
                    addAll(visibleSet)
                    val next = (visibleSet.maxOrNull() ?: firstIndex) + 1
                    if (next in 0 until pageCount) add(next)
                }
                hiPriority.forEach { idx ->
                    launch {
                        semaphore.withPermit {
                            val bmpHi = withContext(Dispatchers.Default) {
                                holder.renderPageToWidth(idx, screenWidthPx)
                            }
                            if (bmpHi != null) {
                                withContext(Dispatchers.Main) {
                                    if (idx < pageBitmaps.size) {
                                        val current = pageBitmaps[idx]
                                        val shouldReplace = current == null || (current.width < (screenWidthPx * 0.95f))
                                        if (shouldReplace) {
                                            pageBitmaps[idx] = bmpHi
                                            if (current != null && current != bmpHi && !current.isRecycled) try { current.recycle() } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
                            PdfPageItem(index = index, bitmap = bmp, showLoadingLabel = !isScrolling)
                        }
                    }
                }
            }

            TopAppBar(
                title = {
                    Text(
                        text = if (initialLoading) "Cargando..." else "$pageCount páginas",
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

            FloatingActionButton(
                onClick = {
                    if (tunerActive) {
                        tunerActive = false
                        tuner.stopTuning()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = if (tunerActive) Color(0xFFFF6B6B) else Color(0xFF4ECDC4)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = if (tunerActive) "Desactivar afinador" else "Activar afinador",
                    tint = Color.White
                )
            }

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

    DisposableEffect(holder) {
        onDispose {
            try { saveLastPage(context, file, listState.firstVisibleItemIndex) } catch (_: Exception) {}
            tuner.stopTuning()
            holder.close()
            pageBitmaps.forEach { bmp ->
                try { if (bmp != null && !bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
            }
            pageBitmaps.clear()
        }
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    bitmap: Bitmap?,
    showLoadingLabel: Boolean
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Página ${index + 1}",
            modifier = Modifier.fillMaxWidth()
        )
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
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap?, newValue: Bitmap?) {
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
        try { kotlinx.coroutines.delay(80) } catch (_: Exception) {}
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
