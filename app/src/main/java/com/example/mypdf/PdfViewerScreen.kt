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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(file: File, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    // Estado del afinador
    var tunerActive by remember { mutableStateOf(false) }
    val tuner = remember { AudioTuner() }
    val tuningResult by tuner.tuningState.collectAsState()

    // Estado para el selector de frecuencia
    var showFrequencySelector by remember { mutableStateOf(false) }

    // Launcher para solicitar permiso de micrófono
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            tunerActive = true
            tuner.startTuning(scope)
        }
    }

    // Activar modo inmersivo y restaurarlo al salir
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

    // Lista de bitmaps precargados (una entrada por página)
    val bitmaps = remember { mutableStateListOf<Bitmap?>() }

    // Estado para controlar si las páginas están cargando
    var pagesLoading by remember { mutableStateOf(true) }

    // Asegurar tamaño de la lista al pageCount
    LaunchedEffect(pageCount) {
        if (pageCount > 0) {
            bitmaps.clear()
            repeat(pageCount) { bitmaps.add(null) }
        }
    }

    // Tamaño de pantalla en px (para ajustar el ancho de cada página)
    val config = LocalConfiguration.current
    val screenWidthPx = (config.screenWidthDp * context.resources.displayMetrics.density).toInt()

    // Si cambia el ancho de pantalla, forzar re-render de todas las páginas al nuevo ancho
    LaunchedEffect(screenWidthPx, pageCount) {
        if (pageCount > 0 && bitmaps.size == pageCount) {
            pagesLoading = true
            for (i in 0 until pageCount) {
                val old = bitmaps[i]
                if (old != null && !old.isRecycled) try { old.recycle() } catch (_: Exception) {}
                bitmaps[i] = null
            }
        }
    }

    // Precarga: renderizar TODAS las páginas al ancho objetivo en background (paralelo controlado)
    LaunchedEffect(pageCount, screenWidthPx) {
        if (pageCount > 0 && screenWidthPx > 0 && bitmaps.size == pageCount) {
            pagesLoading = true
            val maxParallel = minOf(4, Runtime.getRuntime().availableProcessors())
            val semaphore = Semaphore(maxParallel)
            val jobs = mutableListOf<kotlinx.coroutines.Job>()

            for (i in 0 until pageCount) {
                if (bitmaps[i] == null) {
                    val job = launch(Dispatchers.Default) {
                        semaphore.withPermit {
                            val bmp = holder.renderPageToWidth(i, screenWidthPx)
                            if (bmp != null && i < bitmaps.size) {
                                // Cambiar estado en el hilo principal
                                withContext(Dispatchers.Main) {
                                    bitmaps[i] = bmp
                                }
                            }
                        }
                    }
                    jobs.add(job)
                }
            }

            // Esperar a que todas las páginas terminen de cargarse
            jobs.forEach { it.join() }
            pagesLoading = false
        }
    }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Mostrar pantalla de carga mientras se precargan las páginas
            if (pagesLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        val loadedCount = bitmaps.count { it != null }
                        Text(
                            text = "Cargando páginas: $loadedCount / $pageCount",
                            color = Color.White
                        )
                    }
                }
            } else {
                // Lista con todas las páginas precargadas, una debajo de la otra
                if (pageCount <= 0) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Cargando…", color = Color.White)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 0.dp),

                        ) {
                        items(pageCount) { index ->
                            val bmp = if (index < bitmaps.size) bitmaps[index] else null
                            PdfPageItem(index = index, bitmap = bmp)
                        }
                    }
                }
            }

            // Barra superior con botón atrás y total de páginas (siempre visible)
            val loadedCount by remember(bitmaps) { derivedStateOf { bitmaps.count { it != null } } }
            TopAppBar(
                title = {
                    Text(
                        text = if (pagesLoading) "Cargando..." else "$pageCount páginas",
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

            // Botón flotante del afinador (abajo a la derecha)
            FloatingActionButton(
                onClick = {
                    if (tunerActive) {
                        tunerActive = false
                        tuner.stopTuning()
                    } else {
                        // Solicitar permiso si es necesario
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

            // Panel del afinador (cuando está activo)
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
                        // Indicador visual de afinación
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

                        // Información de la nota detectada
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

                                // Mostrar cents de diferencia
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

                // Popup del selector de frecuencia
                if (showFrequencySelector) {
                    FrequencySelectorPopup(
                        tuner = tuner,
                        onDismiss = { showFrequencySelector = false }
                    )
                }
            }
        }
    }

    // Cierre explícito del holder y reciclado de bitmaps al salir de la pantalla
    DisposableEffect(holder) {
        onDispose {
            tuner.stopTuning()
            holder.close()
            bitmaps.forEach { bmp ->
                try { if (bmp != null && !bmp.isRecycled) bmp.recycle() } catch (_: Exception) {}
            }
            bitmaps.clear()
        }
    }
}

@Composable
private fun PdfPageItem(
    index: Int,
    bitmap: Bitmap?
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
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Cargando página ${index + 1}…", color = Color.White)
        }
    }
}

private class PdfRendererHolder(file: File) {
    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(pfd)

    val pageCount: Int get() = renderer.pageCount

    fun renderPageToWidth(index: Int, targetW: Int): Bitmap? {
        return try {
            val page = renderer.openPage(index)
            val srcW = page.width
            val srcH = page.height
            val scale = targetW.toFloat() / srcW
            val outW = (srcW * scale).toInt().coerceAtLeast(1)
            val outH = (srcH * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun close() {
        try {
            renderer.close()
            pfd.close()
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

                // Control de frecuencia base (Hz)
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
                Divider(color = Color.LightGray)
                Spacer(modifier = Modifier.height(16.dp))

                // Selector de ambiente
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
