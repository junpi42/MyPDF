package com.example.mypdf.eyecontrol

import android.content.Context
import android.content.SharedPreferences
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val PREFS_NAME = "eye_calibration"
private const val KEY_NORMAL_LEFT = "normal_left"
private const val KEY_NORMAL_RIGHT = "normal_right"
private const val KEY_WINK_LEFT_OPEN = "wink_left_open"
private const val KEY_WINK_LEFT_CLOSED = "wink_left_closed"
private const val KEY_WINK_RIGHT_OPEN = "wink_right_open"
private const val KEY_WINK_RIGHT_CLOSED = "wink_right_closed"
private const val KEY_IS_CALIBRATED = "is_calibrated"

/**
 * Datos de calibración del usuario.
 */
data class EyeCalibrationData(
    val normalLeftEye: Float = 0.85f,
    val normalRightEye: Float = 0.85f,
    val winkLeftOpen: Float = 0.85f,    // Ojo izquierdo cuando guiña derecho
    val winkLeftClosed: Float = 0.15f,  // Ojo izquierdo cerrado
    val winkRightOpen: Float = 0.85f,   // Ojo derecho cuando guiña izquierdo
    val winkRightClosed: Float = 0.15f, // Ojo derecho cerrado
    val isCalibrated: Boolean = false
) {
    /**
     * Calcula los umbrales personalizados basados en la calibración.
     */
    fun getThresholds(): EyeThresholds {
        return if (isCalibrated) {
            EyeThresholds(
                leftOpenThreshold = (normalLeftEye + winkRightOpen) / 2f,
                leftClosedThreshold = (normalLeftEye + winkLeftClosed) / 2f,
                rightOpenThreshold = (normalRightEye + winkLeftOpen) / 2f,
                rightClosedThreshold = (normalRightEye + winkRightClosed) / 2f
            )
        } else {
            // Valores por defecto
            EyeThresholds()
        }
    }
}

/**
 * Umbrales calculados para la detección de guiños.
 */
data class EyeThresholds(
    val leftOpenThreshold: Float = 0.7f,
    val leftClosedThreshold: Float = 0.3f,
    val rightOpenThreshold: Float = 0.7f,
    val rightClosedThreshold: Float = 0.3f
)

/**
 * Estado de la calibración.
 */
enum class CalibrationStep {
    MENU,           // Menú inicial
    WAITING_START,  // Esperando que el usuario pulse Start
    NORMAL_EYES,    // Paso 1: Ojos normales
    WINK_LEFT,      // Paso 2: Guiño izquierdo
    WINK_RIGHT,     // Paso 3: Guiño derecho
    COMPLETE        // Calibración completada
}

/**
 * Gestor de calibración que guarda/carga datos de SharedPreferences.
 */
class EyeCalibrationManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun saveCalibration(data: EyeCalibrationData) {
        prefs.edit().apply {
            putFloat(KEY_NORMAL_LEFT, data.normalLeftEye)
            putFloat(KEY_NORMAL_RIGHT, data.normalRightEye)
            putFloat(KEY_WINK_LEFT_OPEN, data.winkLeftOpen)
            putFloat(KEY_WINK_LEFT_CLOSED, data.winkLeftClosed)
            putFloat(KEY_WINK_RIGHT_OPEN, data.winkRightOpen)
            putFloat(KEY_WINK_RIGHT_CLOSED, data.winkRightClosed)
            putBoolean(KEY_IS_CALIBRATED, true)
            apply()
        }
    }
    
    fun loadCalibration(): EyeCalibrationData {
        return EyeCalibrationData(
            normalLeftEye = prefs.getFloat(KEY_NORMAL_LEFT, 0.85f),
            normalRightEye = prefs.getFloat(KEY_NORMAL_RIGHT, 0.85f),
            winkLeftOpen = prefs.getFloat(KEY_WINK_LEFT_OPEN, 0.85f),
            winkLeftClosed = prefs.getFloat(KEY_WINK_LEFT_CLOSED, 0.15f),
            winkRightOpen = prefs.getFloat(KEY_WINK_RIGHT_OPEN, 0.85f),
            winkRightClosed = prefs.getFloat(KEY_WINK_RIGHT_CLOSED, 0.15f),
            isCalibrated = prefs.getBoolean(KEY_IS_CALIBRATED, false)
        )
    }
    
    fun clearCalibration() {
        prefs.edit().clear().apply()
    }
}

/**
 * Diálogo de menú de calibración con animación elegante.
 */
@Composable
fun EyeCalibrationDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onStartCalibration: () -> Unit,
    onResetCalibration: () -> Unit,
    isCalibrated: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)) + scaleIn(
            initialScale = 0.8f,
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(200)) + scaleOut(
            targetScale = 0.8f,
            animationSpec = tween(200)
        )
    ) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(24.dp)
                        .widthIn(max = 400.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Icono animado
                        val infiniteTransition = rememberInfiniteTransition(label = "icon")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .scale(scale),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "Control por Guiño",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (isCalibrated) 
                                "✓ Calibrado" 
                            else 
                                "Sin calibrar - Usa valores predeterminados",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCalibrated) 
                                Color(0xFF4CAF50) 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Botón de calibrar
                        CalibrationMenuButton(
                            icon = Icons.Default.Tune,
                            title = if (isCalibrated) "Recalibrar" else "Calibrar",
                            subtitle = "Ajusta la detección a tus ojos",
                            onClick = onStartCalibration
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Botón de resetear (solo si está calibrado)
                        if (isCalibrated) {
                            CalibrationMenuButton(
                                icon = Icons.Default.RestartAlt,
                                title = "Restablecer",
                                subtitle = "Volver a valores predeterminados",
                                onClick = onResetCalibration,
                                isDestructive = true
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        
                        // Botón de cerrar
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cerrar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationMenuButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    val containerColor = if (isDestructive) 
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    else 
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    
    val contentColor = if (isDestructive)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.primary
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Pantalla de calibración paso a paso.
 */
@Composable
fun CalibrationScreen(
    step: CalibrationStep,
    progress: Float,
    currentLeftEye: Float?,
    currentRightEye: Float?,
    onCancel: () -> Unit,
    onComplete: () -> Unit,
    onStartCalibration: () -> Unit = {},
    upcomingStep: CalibrationStep? = null
) {
    val stepInfo = when (step) {
        CalibrationStep.WAITING_START -> {
            when (upcomingStep) {
                CalibrationStep.NORMAL_EYES -> Triple(
                    "Preparación",
                    "Listo para calibrar",
                    "Posiciona tu cara frente a la cámara y pulsa Iniciar cuando estés listo"
                )
                CalibrationStep.WINK_LEFT -> Triple(
                    "Preparación",
                    "Listo para guiño izquierdo",
                    "Coloca tu cara y pulsa Iniciar para comenzar el guiño izquierdo"
                )
                CalibrationStep.WINK_RIGHT -> Triple(
                    "Preparación",
                    "Listo para guiño derecho",
                    "Coloca tu cara y pulsa Iniciar para comenzar el guiño derecho"
                )
                else -> Triple(
                    "Preparación",
                    "Listo para calibrar",
                    "Posiciona tu cara frente a la cámara y pulsa Iniciar cuando estés listo"
                )
            }
        }
        CalibrationStep.NORMAL_EYES -> Triple(
            "Paso 1 de 3",
            "Mira a la cámara",
            "Mantén los dos ojos abiertos y la cabeza quieta"
        )
        CalibrationStep.WINK_LEFT -> Triple(
            "Paso 2 de 3",
            "Guiño izquierdo",
            "Cierra solo el ojo izquierdo y mantén el derecho abierto"
        )
        CalibrationStep.WINK_RIGHT -> Triple(
            "Paso 3 de 3",
            "Guiño derecho",
            "Cierra solo el ojo derecho y mantén el izquierdo abierto"
        )
        CalibrationStep.COMPLETE -> Triple(
            "¡Completado!",
            "Calibración exitosa",
            "Tu perfil de detección ha sido guardado"
        )
        else -> Triple("", "", "")
    }
    
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1a1a2e),
                            Color(0xFF16213e)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .padding(32.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Indicador de paso
                Text(
                    text = stepInfo.first,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Animación del ojo
                CalibrationEyeAnimation(
                    step = step,
                    progress = progress
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Título
                Text(
                    text = stepInfo.second,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Instrucción
                Text(
                    text = stepInfo.third,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Barra de progreso (oculta en WAITING_START)
                if (step != CalibrationStep.COMPLETE && step != CalibrationStep.WAITING_START) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF4CAF50),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    
                    // Mostrar valores en tiempo real
                    if (currentLeftEye != null && currentRightEye != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            EyeValueIndicator(
                                label = "👁 Izq",
                                value = currentLeftEye,
                                isClosed = step == CalibrationStep.WINK_LEFT
                            )
                            EyeValueIndicator(
                                label = "👁 Der",
                                value = currentRightEye,
                                isClosed = step == CalibrationStep.WINK_RIGHT
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Botones
                when (step) {
                    CalibrationStep.WAITING_START -> {
                        // Botón de iniciar calibración
                        Button(
                            onClick = onStartCalibration,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Iniciar Calibración", fontSize = 18.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Cancelar")
                        }
                    }
                    CalibrationStep.COMPLETE -> {
                        Button(
                            onClick = onComplete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Finalizar", fontSize = 18.sp)
                        }
                    }
                    else -> {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EyeValueIndicator(
    label: String,
    value: Float,
    isClosed: Boolean
) {
    val color = when {
        isClosed && value < 0.3f -> Color(0xFF4CAF50)  // Correcto: cerrado
        !isClosed && value > 0.7f -> Color(0xFF4CAF50) // Correcto: abierto
        else -> Color(0xFFFFC107)                       // En proceso
    }
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = String.format("%.2f", value),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun CalibrationEyeAnimation(
    step: CalibrationStep,
    progress: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "eye")
    
    val leftEyeScale by infiniteTransition.animateFloat(
        initialValue = if (step == CalibrationStep.WINK_LEFT) 0.1f else 1f,
        targetValue = if (step == CalibrationStep.WINK_LEFT) 0.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leftEye"
    )
    
    val rightEyeScale by infiniteTransition.animateFloat(
        initialValue = if (step == CalibrationStep.WINK_RIGHT) 0.1f else 1f,
        targetValue = if (step == CalibrationStep.WINK_RIGHT) 0.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rightEye"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    Box(
        modifier = Modifier
            .size(150.dp)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        // Círculo de progreso
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF4CAF50),
            trackColor = Color.White.copy(alpha = 0.2f),
            strokeWidth = 6.dp
        )
        
        // Cara con ojos
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Ojo izquierdo
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scaleY = if (step == CalibrationStep.WINK_LEFT) 0.15f else 1f, scaleX = 1f)
                    .clip(CircleShape)
                    .background(Color.White)
            )
            
            // Ojo derecho
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scaleY = if (step == CalibrationStep.WINK_RIGHT) 0.15f else 1f, scaleX = 1f)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

/**
 * Controlador de calibración que maneja el flujo completo.
 */
@Composable
fun EyeCalibrationFlow(
    visible: Boolean,
    onComplete: (EyeCalibrationData) -> Unit,
    onCancel: () -> Unit
) {
    if (!visible) return
    
    val context = LocalContext.current
    val calibrationManager = remember { EyeCalibrationManager(context) }
    
    var currentStep by remember { mutableStateOf(CalibrationStep.WAITING_START) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentLeftEye by remember { mutableStateOf<Float?>(null) }
    var currentRightEye by remember { mutableStateOf<Float?>(null) }
    
    // Acumuladores para cada paso
    var normalLeftSum by remember { mutableFloatStateOf(0f) }
    var normalRightSum by remember { mutableFloatStateOf(0f) }
    var winkLeftOpenSum by remember { mutableFloatStateOf(0f) }
    var winkLeftClosedSum by remember { mutableFloatStateOf(0f) }
    var winkRightOpenSum by remember { mutableFloatStateOf(0f) }
    var winkRightClosedSum by remember { mutableFloatStateOf(0f) }
    var frameCount by remember { mutableIntStateOf(0) }
    // Paso pendiente que se iniciará cuando el usuario pulse Start
    var pendingStep by remember { mutableStateOf(CalibrationStep.NORMAL_EYES) }
    
    val targetFrames = 60 // ~2 segundos a 30fps
    
    // SOLO para encender/apagar la cámara (no capturado dentro de la lambda)
    val isCapturingStep = currentStep == CalibrationStep.NORMAL_EYES ||
            currentStep == CalibrationStep.WINK_LEFT ||
            currentStep == CalibrationStep.WINK_RIGHT

    // Usar un CoroutineScope para volver al hilo principal desde el executor
    val scope = rememberCoroutineScope()

    // Esta lambda se ejecuta probablemente en un hilo de fondo (executor)
    // Por eso no capturamos `isCapturingStep` aquí; leemos `currentStep` dentro
    val onEyeData: (Float?, Float?, Boolean) -> Unit = onEyeData@{ left, right, hasFace ->
        if (!hasFace || left == null || right == null) return@onEyeData

        scope.launch {
            val step = currentStep

            // Solo procesamos en los pasos de captura
            if (step != CalibrationStep.NORMAL_EYES &&
                step != CalibrationStep.WINK_LEFT &&
                step != CalibrationStep.WINK_RIGHT
            ) {
                return@launch
            }

            // Actualizar valores que ves en pantalla
            currentLeftEye = left
            currentRightEye = right

            // Acumular medias según el paso
            when (step) {
                CalibrationStep.NORMAL_EYES -> {
                    normalLeftSum += left
                    normalRightSum += right
                }
                CalibrationStep.WINK_LEFT -> {
                    winkLeftClosedSum += left   // Izquierdo cerrado
                    winkLeftOpenSum += right    // Derecho abierto
                }
                CalibrationStep.WINK_RIGHT -> {
                    winkRightOpenSum += left    // Izquierdo abierto
                    winkRightClosedSum += right // Derecho cerrado
                }
                else -> {}
            }

            frameCount++
            progress = frameCount.toFloat() / targetFrames

            if (frameCount >= targetFrames) {
                // Determinar siguiente paso
                val next = when (step) {
                    CalibrationStep.NORMAL_EYES -> CalibrationStep.WINK_LEFT
                    CalibrationStep.WINK_LEFT -> CalibrationStep.WINK_RIGHT
                    CalibrationStep.WINK_RIGHT -> CalibrationStep.COMPLETE
                    else -> CalibrationStep.COMPLETE
                }

                if (next == CalibrationStep.COMPLETE) {
                    // Terminó todo el proceso
                    currentStep = CalibrationStep.COMPLETE
                } else {
                    // Pausar y esperar a que el usuario pulse Start para el siguiente paso
                    pendingStep = next
                    currentStep = CalibrationStep.WAITING_START
                }

                frameCount = 0
                progress = 0f
            }
        }
    }
    
    // Usar el analizador de cámara
    CalibrationCameraAnalyzer(
        enabled = isCapturingStep,
        onEyeData = onEyeData
    )
    
    // Mostrar pantalla de calibración
    CalibrationScreen(
        step = currentStep,
        progress = progress,
        currentLeftEye = currentLeftEye,
        currentRightEye = currentRightEye,
        onCancel = onCancel,
        onStartCalibration = {
            // Usuario pulsa Start, comenzar la captura del paso pendiente
            currentStep = pendingStep
            frameCount = 0
            progress = 0f
        },
        onComplete = {
            // Calcular promedios y guardar
            val avgNormalLeft = normalLeftSum / targetFrames
            val avgNormalRight = normalRightSum / targetFrames
            val avgWinkLeftOpen = winkLeftOpenSum / targetFrames
            val avgWinkLeftClosed = winkLeftClosedSum / targetFrames
            val avgWinkRightOpen = winkRightOpenSum / targetFrames
            val avgWinkRightClosed = winkRightClosedSum / targetFrames
            
            val calibrationData = EyeCalibrationData(
                normalLeftEye = avgNormalLeft,
                normalRightEye = avgNormalRight,
                winkLeftOpen = avgWinkLeftOpen,
                winkLeftClosed = avgWinkLeftClosed,
                winkRightOpen = avgWinkRightOpen,
                winkRightClosed = avgWinkRightClosed,
                isCalibrated = true
            )
            
            calibrationManager.saveCalibration(calibrationData)
            onComplete(calibrationData)
        }
        ,
        upcomingStep = pendingStep
    )
}

/**
 * Composable que usa la cámara para capturar datos de ojos durante la calibración.
 * Reutiliza el patrón de EyeCameraController evitando cerrar el executor en cada cambio de enabled.
 */
@Composable
private fun CalibrationCameraAnalyzer(
    enabled: Boolean,
    onEyeData: (Float?, Float?, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val controller = remember(lifecycleOwner) {
        CalibrationCameraController(context, lifecycleOwner, onEyeData)
    }
    
    // Solo liberar cuando el composable se desmonte completamente
    DisposableEffect(Unit) {
        onDispose {
            controller.release()
        }
    }
    
    // Iniciar/parar cámara según enabled (sin cerrar executor)
    LaunchedEffect(enabled) {
        if (enabled) {
            controller.startCamera()
        } else {
            controller.stopCamera()
        }
    }
}

/**
 * Controlador de cámara simplificado para calibración.
 */
private class CalibrationCameraController(
    private val context: Context,
    private val lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    private val onEyeData: (Float?, Float?, Boolean) -> Unit
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: EyeTrackingAnalyzer? = null
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                android.util.Log.e("CalibrationCamera", "Error getting camera provider", e)
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }
    
    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        
        provider.unbindAll()
        
        // Crear analyzer que envía los datos de ojos
        analyzer = EyeTrackingAnalyzer { leftEye, rightEye, hasFace ->
            onEyeData(leftEye, rightEye, hasFace)
        }
        
        // Image analysis use case
        val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        
        imageAnalysis.setAnalyzer(executor, analyzer!!)
        
        val cameraSelector = androidx.camera.core.CameraSelector.Builder()
            .requireLensFacing(androidx.camera.core.CameraSelector.LENS_FACING_FRONT)
            .build()
        
        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        } catch (e: Exception) {
            android.util.Log.e("CalibrationCamera", "Error binding camera use cases", e)
        }
    }
    
    fun stopCamera() {
        cameraProvider?.unbindAll()
        analyzer?.close()
        analyzer = null
    }
    
    fun release() {
        stopCamera()
        executor.shutdown()
    }
}
