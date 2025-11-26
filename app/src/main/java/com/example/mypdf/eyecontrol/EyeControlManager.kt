package com.example.mypdf.eyecontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.RectF
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.mypdf.strings
import java.util.concurrent.Executors

private const val TAG = "EyeControl"

/**
 * Datos de debug de la detección facial
 */
data class FaceDebugInfo(
    val hasFace: Boolean = false,
    val faceRect: RectF? = null,
    val leftEyeProb: Float? = null,
    val rightEyeProb: Float? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotationDegrees: Int = 0
)

/**
 * Estado del controlador de ojos
 */
data class EyeControlState(
    val enabled: Boolean = false,
    val faceDetected: Boolean = false,
    val winkState: WinkState = WinkState.IDLE,
    val winkProgress: Float = 0f,
    val debugInfo: FaceDebugInfo = FaceDebugInfo()
)

/**
 * Controlador de la cámara para detección de guiños SIN preview (versión de producción).
 */
class EyeCameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onWinkDetected: () -> Unit,
    private val onStateUpdated: (Boolean, WinkState) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: EyeTrackingAnalyzer? = null
    private val executor = Executors.newSingleThreadExecutor()
    
    private val winkDetector = WinkDetector(
        winkHoldTimeMs = 500L,
        cooldownTimeMs = 1000L,
        onWinkDetected = onWinkDetected
    )
    
    private val calibrationManager = EyeCalibrationManager(context)
    
    init {
        // Cargar calibración si existe
        val calibration = calibrationManager.loadCalibration()
        if (calibration.isCalibrated) {
            winkDetector.updateThresholds(calibration.getThresholds())
        }
    }
    
    fun updateCalibration(calibration: EyeCalibrationData) {
        if (calibration.isCalibrated) {
            winkDetector.updateThresholds(calibration.getThresholds())
        }
    }
    
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        
        provider.unbindAll()
        
        // Crear analyzer
        analyzer = EyeTrackingAnalyzer { leftEye, rightEye, hasFace ->
            winkDetector.processEyeState(leftEye, rightEye, hasFace)
            onStateUpdated(winkDetector.faceDetected, winkDetector.currentState)
        }
        
        // Image analysis use case
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        
        imageAnalysis.setAnalyzer(executor, analyzer!!)
        
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
        
        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error binding camera use cases", e)
        }
    }
    
    fun stopCamera() {
        cameraProvider?.unbindAll()
        analyzer?.close()
        analyzer = null
        winkDetector.reset()
    }
    
    fun release() {
        stopCamera()
        executor.shutdown()
    }
}

/**
 * Controlador de la cámara para detección de guiños CON preview (versión de debug).
 */
class EyeCameraControllerWithPreview(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onWinkDetected: () -> Unit,
    private val onStateUpdated: (Boolean, WinkState, FaceDebugInfo) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: EyeTrackingAnalyzerWithDebug? = null
    private val executor = Executors.newSingleThreadExecutor().also {
        Log.d(TAG, "Executor creado: $it")
    }
    
    var previewView: PreviewView? = null
        private set
    
    private val winkDetector = WinkDetector(
        winkHoldTimeMs = 500L,
        cooldownTimeMs = 1000L,
        onWinkDetected = onWinkDetected
    )
    
    fun startCamera(previewView: PreviewView) {
        Log.d(TAG, "=== startCamera() llamado ===")
        this.previewView = previewView
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        Log.d(TAG, "Esperando ProcessCameraProvider...")
        cameraProviderFuture.addListener({
            try {
                Log.d(TAG, "ProcessCameraProvider obtenido, configurando cámara...")
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting camera provider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }
    
    private fun bindCameraUseCases(previewView: PreviewView) {
        val provider = cameraProvider
        if (provider == null) {
            Log.e(TAG, "cameraProvider es NULL!")
            return
        }
        
        Log.d(TAG, "Desvinculando use cases anteriores...")
        provider.unbindAll()
        
        // Preview use case
        Log.d(TAG, "Creando Preview use case...")
        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        
        // Create analyzer with debug info
        Log.d(TAG, "Creando EyeTrackingAnalyzerWithDebug...")
        analyzer = EyeTrackingAnalyzerWithDebug { debugInfo ->
            winkDetector.processEyeState(debugInfo.leftEyeProb, debugInfo.rightEyeProb, debugInfo.hasFace)
            onStateUpdated(winkDetector.faceDetected, winkDetector.currentState, debugInfo)
        }
        
        // Image analysis use case - IMPORTANTE: NO usar setOutputImageFormat para que funcione con ML Kit
        Log.d(TAG, "Creando ImageAnalysis use case (640x480, formato YUV por defecto)...")
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            // NO añadir setOutputImageFormat - debe ser YUV_420_888 para ML Kit
            .build()
        
        Log.d(TAG, "ImageAnalysis creado: $imageAnalysis (hashCode=${imageAnalysis.hashCode()})")
        Log.d(TAG, "Analyzer a asignar: ${analyzer!!} (hashCode=${analyzer.hashCode()})")
        Log.d(TAG, "Executor para analyzer: $executor, isShutdown=${executor.isShutdown}, isTerminated=${executor.isTerminated}")
        Log.d(TAG, "Asignando analyzer al ImageAnalysis...")
        
        // Wrapper para verificar que el analyzer recibe frames
        val wrappedAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
            Log.d(TAG, ">>> WRAPPER: Frame recibido! w=${imageProxy.width}, h=${imageProxy.height} <<<")
            analyzer!!.analyze(imageProxy)
        }
        
        imageAnalysis.setAnalyzer(executor, wrappedAnalyzer)
        Log.d(TAG, "Analyzer (wrapped) asignado correctamente al ImageAnalysis ${imageAnalysis.hashCode()}")
        
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
        
        try {
            Log.d(TAG, "Vinculando al lifecycle: Preview + ImageAnalysis (hashCode=${imageAnalysis.hashCode()})...")
            Log.d(TAG, "LifecycleOwner: $lifecycleOwner, state=${lifecycleOwner.lifecycle.currentState}")
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            Log.d(TAG, "✓ Cámara vinculada exitosamente!")
            Log.d(TAG, "✓ ImageAnalysis vinculado: ${imageAnalysis.hashCode()}")
            Log.d(TAG, "✓ Preview vinculado: ${preview.hashCode()}")
            
            // Eliminado el acceso directo a imageAnalysis.camera/preview.camera que rompía el build
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error binding camera use cases", e)
            e.printStackTrace()
        }
    }
    
    fun stopCamera() {
        Log.d(TAG, "stopCamera() llamado")
        cameraProvider?.unbindAll()
        analyzer?.close()
        analyzer = null
        winkDetector.reset()
    }
    
    fun release() {
        stopCamera()
        executor.shutdown()
    }
}

/**
 * Composable para el botón de control por guiño en el modo concierto.
 * - Tap: Activa/desactiva el control por guiño
 * - Long press: Abre el menú de calibración
 */
@Composable
fun EyeControlButton(
    eyeControlEnabled: Boolean,
    faceDetected: Boolean,
    winkState: WinkState,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val s = strings()
    val buttonScale by animateFloatAsState(
        targetValue = if (eyeControlEnabled && winkState == WinkState.WINK_STARTED) 1.1f else 1f,
        animationSpec = tween(200),
        label = "buttonScale"
    )
    
    val iconColor by animateColorAsState(
        targetValue = when {
            !eyeControlEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
            winkState == WinkState.WAITING_FOR_OPEN -> Color(0xFF4CAF50)
            winkState == WinkState.WINK_STARTED -> Color(0xFFFFC107)
            faceDetected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        },
        animationSpec = tween(300),
        label = "iconColor"
    )
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .scale(buttonScale),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (eyeControlEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = if (eyeControlEnabled) s.eyeControlDisable else s.eyeControlEnable,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
        }
        
        if (eyeControlEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            winkState == WinkState.WAITING_FOR_OPEN -> Color(0xFF4CAF50)
                            winkState == WinkState.WINK_STARTED -> Color(0xFFFFC107)
                            faceDetected -> Color(0xFF4CAF50)
                            else -> Color(0xFFE53935)
                        }
                    )
            )
        }
    }
}

/**
 * Preview de cámara con overlay de debug
 */
@Composable
fun CameraDebugPreview(
    debugInfo: FaceDebugInfo,
    winkState: WinkState,
    modifier: Modifier = Modifier,
    onPreviewViewReady: (PreviewView) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    onPreviewViewReady(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Face detection overlay
        if (debugInfo.hasFace && debugInfo.faceRect != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                
                // Calcular la escala considerando la rotación
                val imgWidth = if (debugInfo.rotationDegrees == 90 || debugInfo.rotationDegrees == 270) {
                    debugInfo.imageHeight.toFloat()
                } else {
                    debugInfo.imageWidth.toFloat()
                }
                val imgHeight = if (debugInfo.rotationDegrees == 90 || debugInfo.rotationDegrees == 270) {
                    debugInfo.imageWidth.toFloat()
                } else {
                    debugInfo.imageHeight.toFloat()
                }
                
                if (imgWidth > 0 && imgHeight > 0) {
                    val scaleX = canvasWidth / imgWidth
                    val scaleY = canvasHeight / imgHeight
                    
                    val rect = debugInfo.faceRect
                    
                    // Mirror horizontalmente porque es cámara frontal
                    val left = canvasWidth - (rect.right * scaleX)
                    val right = canvasWidth - (rect.left * scaleX)
                    val top = rect.top * scaleY
                    val bottom = rect.bottom * scaleY
                    
                    // Dibujar rectángulo de cara
                    drawRect(
                        color = when (winkState) {
                            WinkState.WAITING_FOR_OPEN -> Color.Green
                            WinkState.WINK_STARTED -> Color.Yellow
                            else -> Color.Cyan
                        },
                        topLeft = Offset(left, top),
                        size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }
        
        // Debug info overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(
                text = if (debugInfo.hasFace) "✓ CARA DETECTADA" else "✗ SIN CARA",
                color = if (debugInfo.hasFace) Color.Green else Color.Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Rotación: ${debugInfo.rotationDegrees}°",
                color = Color.White,
                fontSize = 10.sp
            )
            
            Text(
                text = "Imagen: ${debugInfo.imageWidth}x${debugInfo.imageHeight}",
                color = Color.White,
                fontSize = 10.sp
            )
            
            if (debugInfo.leftEyeProb != null && debugInfo.rightEyeProb != null) {
                Spacer(Modifier.height(4.dp))
                
                val leftStatus = when {
                    debugInfo.leftEyeProb < 0.3f -> "CERRADO"
                    debugInfo.leftEyeProb > 0.7f -> "ABIERTO"
                    else -> "???"
                }
                val rightStatus = when {
                    debugInfo.rightEyeProb < 0.3f -> "CERRADO"
                    debugInfo.rightEyeProb > 0.7f -> "ABIERTO"
                    else -> "???"
                }
                
                Text(
                    text = "👁 Izq: ${String.format("%.2f", debugInfo.leftEyeProb)} ($leftStatus)",
                    color = if (debugInfo.leftEyeProb < 0.3f) Color.Yellow else Color.White,
                    fontSize = 11.sp
                )
                Text(
                    text = "👁 Der: ${String.format("%.2f", debugInfo.rightEyeProb)} ($rightStatus)",
                    color = if (debugInfo.rightEyeProb < 0.3f) Color.Yellow else Color.White,
                    fontSize = 11.sp
                )
            }
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                text = "Estado: ${winkState.name}",
                color = when (winkState) {
                    WinkState.IDLE -> Color.White
                    WinkState.WINK_STARTED -> Color.Yellow
                    WinkState.WAITING_FOR_OPEN -> Color.Green
                    WinkState.COOLDOWN -> Color.Cyan
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Composable de ayuda que muestra instrucciones para el control por guiño.
 */
@Composable
fun EyeControlHelpCard(
    visible: Boolean,
    faceDetected: Boolean,
    winkState: WinkState,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    
    val s = strings()
    val message = when {
        !faceDetected -> s.eyeControlNoFace
        winkState == WinkState.WAITING_FOR_OPEN -> s.eyeControlWaitingOpen
        winkState == WinkState.WINK_STARTED -> s.eyeControlWinkDetected
        winkState == WinkState.COOLDOWN -> s.eyeControlPageChanged
        else -> s.eyeControlHelp
    }
    
    val backgroundColor = when {
        !faceDetected -> MaterialTheme.colorScheme.errorContainer
        winkState == WinkState.WAITING_FOR_OPEN -> Color(0xFF4CAF50).copy(alpha = 0.2f)
        winkState == WinkState.WINK_STARTED -> Color(0xFFFFC107).copy(alpha = 0.2f)
        winkState == WinkState.COOLDOWN -> Color(0xFF4CAF50).copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp)
        )
    }
}

/**
 * Gestor del control por guiño SIN preview (versión de producción).
 * Incluye soporte para calibración.
 */
@Composable
fun rememberEyeControlState(
    enabled: Boolean,
    onWinkDetected: () -> Unit
): EyeControlState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var faceDetected by remember { mutableStateOf(false) }
    var winkState by remember { mutableStateOf(WinkState.IDLE) }
    
    val calibrationManager = remember { EyeCalibrationManager(context) }
    var calibrationData by remember { mutableStateOf(calibrationManager.loadCalibration()) }
    
    val controller = remember(lifecycleOwner) {
        EyeCameraController(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onWinkDetected = onWinkDetected,
            onStateUpdated = { face, state ->
                faceDetected = face
                winkState = state
            }
        )
    }
    
    // Actualizar calibración cuando cambie
    LaunchedEffect(calibrationData) {
        controller.updateCalibration(calibrationData)
    }
    
    // Solo liberar cuando el composable se desmonte completamente
    DisposableEffect(Unit) {
        onDispose {
            controller.release()
        }
    }
    
    // Iniciar/parar cámara según enabled
    LaunchedEffect(enabled) {
        if (enabled) {
            controller.startCamera()
        } else {
            controller.stopCamera()
            faceDetected = false
            winkState = WinkState.IDLE
        }
    }
    
    return EyeControlState(
        enabled = enabled,
        faceDetected = faceDetected,
        winkState = winkState
    )
}

/**
 * Gestor del control por guiño CON preview de debug.
 */
@Composable
fun rememberEyeControlStateWithPreview(
    enabled: Boolean,
    onWinkDetected: () -> Unit
): Pair<EyeControlState, @Composable (Modifier) -> Unit> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var faceDetected by remember { mutableStateOf(false) }
    var winkState by remember { mutableStateOf(WinkState.IDLE) }
    var debugInfo by remember { mutableStateOf(FaceDebugInfo()) }
    
    val controller = remember(lifecycleOwner) {
        Log.d(TAG, ">>> Creando EyeCameraControllerWithPreview <<<")
        EyeCameraControllerWithPreview(
            context = context,
            lifecycleOwner = lifecycleOwner,
            onWinkDetected = onWinkDetected,
            onStateUpdated = { face, state, debug ->
                faceDetected = face
                winkState = state
                debugInfo = debug
            }
        )
    }
    
    // Solo liberar cuando el composable se desmonte completamente
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, ">>> DisposableEffect onDispose - liberando controller <<<")
            controller.release()
        }
    }
    
    val previewComposable: @Composable (Modifier) -> Unit = { modifier ->
        if (enabled) {
            Log.d(TAG, ">>> previewComposable: enabled=true, mostrando CameraDebugPreview <<<")
            CameraDebugPreview(
                debugInfo = debugInfo,
                winkState = winkState,
                modifier = modifier,
                onPreviewViewReady = { previewView ->
                    Log.d(TAG, ">>> onPreviewViewReady llamado, iniciando cámara <<<")
                    controller.startCamera(previewView)
                }
            )
        }
    }
    
    // Parar cámara cuando se desactiva (pero no liberar el controller)
    LaunchedEffect(enabled) {
        Log.d(TAG, ">>> LaunchedEffect(enabled=$enabled) <<<")
        if (!enabled) {
            Log.d(TAG, ">>> enabled=false, parando cámara <<<")
            controller.stopCamera()
            faceDetected = false
            winkState = WinkState.IDLE
            debugInfo = FaceDebugInfo()
        }
    }
    
    return Pair(
        EyeControlState(
            enabled = enabled,
            faceDetected = faceDetected,
            winkState = winkState,
            debugInfo = debugInfo
        ),
        previewComposable
    )
}

/**
 * Verifica si el permiso de cámara está concedido.
 */
fun hasCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}
