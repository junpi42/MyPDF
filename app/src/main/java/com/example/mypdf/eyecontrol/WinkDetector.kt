package com.example.mypdf.eyecontrol

/**
 * Estado del detector de guiño.
 */
enum class WinkState {
    /** Esperando a que se detecte un guiño */
    IDLE,
    /** Un ojo cerrado y otro abierto, contando tiempo */
    WINK_STARTED,
    /** Ha pasado el tiempo mínimo de guiño, esperando que abra ambos ojos */
    WAITING_FOR_OPEN,
    /** En periodo de cooldown, ignora nuevos guiños */
    COOLDOWN
}

/**
 * Detector de guiños con máquina de estados.
 * 
 * Detecta cuando un usuario mantiene un ojo cerrado por [winkHoldTimeMs] milisegundos
 * y luego abre ambos ojos. Incluye un periodo de cooldown para evitar falsos positivos.
 * 
 * @param winkHoldTimeMs Tiempo que debe mantenerse el guiño para ser válido (default: 2000ms)
 * @param cooldownTimeMs Tiempo de espera entre guiños válidos (default: 1000ms)
 * @param onWinkDetected Callback cuando se detecta un guiño válido
 */
class WinkDetector(
    private val winkHoldTimeMs: Long = 2000L,
    private val cooldownTimeMs: Long = 1000L,
    private val onWinkDetected: () -> Unit
) {
    private var state: WinkState = WinkState.IDLE
    private var winkStartTime: Long = 0L
    private var cooldownStartTime: Long = 0L
    
    // Información del último estado para diagnóstico
    var currentState: WinkState = WinkState.IDLE
        private set
    var faceDetected: Boolean = false
        private set
    
    // Umbrales personalizables (pueden ser actualizados desde calibración)
    var leftOpenThreshold: Float = DEFAULT_EYE_OPEN_THRESHOLD
    var leftClosedThreshold: Float = DEFAULT_EYE_CLOSED_THRESHOLD
    var rightOpenThreshold: Float = DEFAULT_EYE_OPEN_THRESHOLD
    var rightClosedThreshold: Float = DEFAULT_EYE_CLOSED_THRESHOLD
    
    /**
     * Umbral de probabilidad para considerar un ojo cerrado.
     * ML Kit devuelve valores de 0.0 (cerrado) a 1.0 (abierto).
     */
    companion object {
        const val DEFAULT_EYE_CLOSED_THRESHOLD = 0.3f
        const val DEFAULT_EYE_OPEN_THRESHOLD = 0.7f
        
        // Aliases para compatibilidad
        const val EYE_CLOSED_THRESHOLD = DEFAULT_EYE_CLOSED_THRESHOLD
        const val EYE_OPEN_THRESHOLD = DEFAULT_EYE_OPEN_THRESHOLD
    }
    
    /**
     * Actualiza los umbrales con valores de calibración personalizada.
     */
    fun updateThresholds(thresholds: EyeThresholds) {
        leftOpenThreshold = thresholds.leftOpenThreshold
        leftClosedThreshold = thresholds.leftClosedThreshold
        rightOpenThreshold = thresholds.rightOpenThreshold
        rightClosedThreshold = thresholds.rightClosedThreshold
    }
    
    /**
     * Procesa el estado de los ojos en cada frame.
     * 
     * @param leftEyeOpenProbability Probabilidad de que el ojo izquierdo esté abierto (0.0-1.0)
     * @param rightEyeOpenProbability Probabilidad de que el ojo derecho esté abierto (0.0-1.0)
     * @param hasFace Si hay cara detectada en el frame
     */
    fun processEyeState(
        leftEyeOpenProbability: Float?,
        rightEyeOpenProbability: Float?,
        hasFace: Boolean
    ) {
        faceDetected = hasFace
        
        if (!hasFace || leftEyeOpenProbability == null || rightEyeOpenProbability == null) {
            // No hay cara, solo resetear si no estamos esperando a que abra los ojos
            if (state != WinkState.WAITING_FOR_OPEN) {
                resetToIdle()
            }
            return
        }
        
        val leftOpen = leftEyeOpenProbability > leftOpenThreshold
        val leftClosed = leftEyeOpenProbability < leftClosedThreshold
        val rightOpen = rightEyeOpenProbability > rightOpenThreshold
        val rightClosed = rightEyeOpenProbability < rightClosedThreshold
        
        val bothOpen = leftOpen && rightOpen
        val oneClosedOneOpen = (leftClosed && rightOpen) || (leftOpen && rightClosed)
        
        val now = System.currentTimeMillis()
        
        when (state) {
            WinkState.IDLE -> {
                if (oneClosedOneOpen) {
                    // Inicio de un posible guiño
                    state = WinkState.WINK_STARTED
                    currentState = state
                    winkStartTime = now
                }
            }
            
            WinkState.WINK_STARTED -> {
                if (oneClosedOneOpen) {
                    // Sigue guiñando, verificar si ha pasado el tiempo
                    if (now - winkStartTime >= winkHoldTimeMs) {
                        state = WinkState.WAITING_FOR_OPEN
                        currentState = state
                    }
                } else {
                    // Dejó de guiñar antes de tiempo, volver a idle
                    resetToIdle()
                }
            }
            
            WinkState.WAITING_FOR_OPEN -> {
                if (bothOpen) {
                    // ¡Guiño completo! Disparar evento y entrar en cooldown
                    onWinkDetected()
                    state = WinkState.COOLDOWN
                    currentState = state
                    cooldownStartTime = now
                }
                // Nota: si sigue con un ojo cerrado, esperamos pacientemente
            }
            
            WinkState.COOLDOWN -> {
                if (now - cooldownStartTime >= cooldownTimeMs) {
                    resetToIdle()
                }
                // Durante el cooldown, ignoramos todo
            }
        }
    }
    
    private fun resetToIdle() {
        state = WinkState.IDLE
        currentState = state
        winkStartTime = 0L
    }
    
    /**
     * Resetea el detector a su estado inicial.
     */
    fun reset() {
        resetToIdle()
        cooldownStartTime = 0L
    }
}
