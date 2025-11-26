package com.example.mypdf.eyecontrol

import android.annotation.SuppressLint
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions

private const val TAG = "EyeTrackingAnalyzer"

/**
 * Analizador de imágenes para detectar caras y estado de los ojos usando ML Kit.
 * 
 * @param onEyeStateUpdated Callback llamado en cada frame con el estado de los ojos.
 *                          Recibe: (leftEyeOpenProb, rightEyeOpenProb, hasFace)
 */
class EyeTrackingAnalyzer(
    private val onEyeStateUpdated: (Float?, Float?, Boolean) -> Unit
) : ImageAnalysis.Analyzer {
    
    private val detector: FaceDetector
    private var frameCount = 0
    
    init {
        Log.d(TAG, "Inicializando EyeTrackingAnalyzer")
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .enableTracking()
            .build()
        
        detector = FaceDetection.getClient(options)
        Log.d(TAG, "Detector ML Kit creado")
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        frameCount++
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            Log.w(TAG, "Frame $frameCount: mediaImage es NULL")
            imageProxy.close()
            return
        }
        
        val rotation = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height
        
        if (frameCount % 30 == 0) {
            Log.d(TAG, "Frame $frameCount: ${width}x${height}, rotación=$rotation°")
        }
        
        val image = InputImage.fromMediaImage(
            mediaImage,
            rotation
        )
        
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (frameCount % 30 == 0) {
                    Log.d(TAG, "Frame $frameCount: SUCCESS - ${faces.size} caras detectadas")
                }
                processDetectedFaces(faces)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Frame $frameCount: ERROR en detector - ${e.message}", e)
                onEyeStateUpdated(null, null, false)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
    
    private fun processDetectedFaces(faces: List<Face>) {
        if (faces.isEmpty()) {
            onEyeStateUpdated(null, null, false)
            return
        }
        
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        
        if (primaryFace == null) {
            onEyeStateUpdated(null, null, false)
            return
        }
        
        val leftEyeOpenProb = primaryFace.leftEyeOpenProbability
        val rightEyeOpenProb = primaryFace.rightEyeOpenProbability
        
        if (frameCount % 30 == 0) {
            Log.d(TAG, "Cara detectada: bbox=${primaryFace.boundingBox}, leftEye=$leftEyeOpenProb, rightEye=$rightEyeOpenProb")
        }
        
        onEyeStateUpdated(leftEyeOpenProb, rightEyeOpenProb, true)
    }
    
    fun close() {
        Log.d(TAG, "Cerrando detector. Total frames procesados: $frameCount")
        detector.close()
    }
}

/**
 * Analizador de imágenes con información de debug para detectar caras y estado de los ojos.
 * Incluye información adicional sobre la imagen y la posición de la cara.
 * 
 * @param onDebugInfoUpdated Callback llamado en cada frame con información de debug completa.
 */
class EyeTrackingAnalyzerWithDebug(
    private val onDebugInfoUpdated: (FaceDebugInfo) -> Unit
) : ImageAnalysis.Analyzer {
    
    private val detector: FaceDetector
    private var frameCount = 0
    private var lastLogTime = 0L
    private val instanceId = System.identityHashCode(this)
    
    init {
        Log.d(TAG, "=== Inicializando EyeTrackingAnalyzerWithDebug (instance=$instanceId) ===")
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(0.1f)
            .enableTracking()
            .build()
        
        detector = FaceDetection.getClient(options)
        Log.d(TAG, "Detector ML Kit creado con modo ACCURATE, landmarks, minFaceSize=0.1")
    }
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        frameCount++
        val currentTime = System.currentTimeMillis()
        val shouldLog = (currentTime - lastLogTime) > 1000 // Log cada segundo
        
        // LOG INMEDIATO para ver si analyze() se llama - ESTE LOG ES CRÍTICO
        Log.d(TAG, ">>> analyze() LLAMADO en instance=$instanceId, frame #$frameCount <<<")
        Log.d(TAG, "    ImageProxy: w=${imageProxy.width}, h=${imageProxy.height}, format=${imageProxy.format}")
        
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            Log.w(TAG, "⚠️ Frame $frameCount: mediaImage es NULL! (formato=${imageProxy.format})")
            // Aún así enviamos info de debug para que se vea en la UI
            onDebugInfoUpdated(FaceDebugInfo(
                hasFace = false,
                imageWidth = imageProxy.width,
                imageHeight = imageProxy.height,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees
            ))
            imageProxy.close()
            return
        }
        
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val imageWidth = imageProxy.width
        val imageHeight = imageProxy.height
        
        if (shouldLog) {
            lastLogTime = currentTime
            Log.d(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Log.d(TAG, "Frame $frameCount recibido:")
            Log.d(TAG, "  - Tamaño imagen: ${imageWidth}x${imageHeight}")
            Log.d(TAG, "  - Rotación: ${rotationDegrees}°")
            Log.d(TAG, "  - Formato: ${mediaImage.format}")
        }
        
        val image = InputImage.fromMediaImage(
            mediaImage,
            rotationDegrees
        )
        
        detector.process(image)
            .addOnSuccessListener { faces ->
                if (shouldLog) {
                    Log.d(TAG, "  ✓ ML Kit SUCCESS: ${faces.size} cara(s) detectada(s)")
                    faces.forEachIndexed { index, face ->
                        Log.d(TAG, "    Cara $index: bbox=${face.boundingBox}")
                        Log.d(TAG, "    Cara $index: leftEye=${face.leftEyeOpenProbability}, rightEye=${face.rightEyeOpenProbability}")
                        Log.d(TAG, "    Cara $index: trackingId=${face.trackingId}")
                    }
                }
                processDetectedFaces(faces, imageWidth, imageHeight, rotationDegrees, shouldLog)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "  ✗ ML Kit ERROR: ${e.javaClass.simpleName} - ${e.message}")
                e.printStackTrace()
                onDebugInfoUpdated(FaceDebugInfo(
                    hasFace = false,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    rotationDegrees = rotationDegrees
                ))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
    
    private fun processDetectedFaces(faces: List<Face>, imageWidth: Int, imageHeight: Int, rotationDegrees: Int, shouldLog: Boolean) {
        if (faces.isEmpty()) {
            if (shouldLog) {
                Log.d(TAG, "  → No hay caras, enviando hasFace=false")
            }
            onDebugInfoUpdated(FaceDebugInfo(
                hasFace = false,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                rotationDegrees = rotationDegrees
            ))
            return
        }
        
        val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
        
        if (primaryFace == null) {
            onDebugInfoUpdated(FaceDebugInfo(
                hasFace = false,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                rotationDegrees = rotationDegrees
            ))
            return
        }
        
        val boundingBox = primaryFace.boundingBox
        val faceRect = RectF(
            boundingBox.left.toFloat(),
            boundingBox.top.toFloat(),
            boundingBox.right.toFloat(),
            boundingBox.bottom.toFloat()
        )
        
        if (shouldLog) {
            Log.d(TAG, "  → Enviando cara principal:")
            Log.d(TAG, "    - faceRect: $faceRect")
            Log.d(TAG, "    - leftEyeProb: ${primaryFace.leftEyeOpenProbability}")
            Log.d(TAG, "    - rightEyeProb: ${primaryFace.rightEyeOpenProbability}")
        }
        
        onDebugInfoUpdated(FaceDebugInfo(
            hasFace = true,
            faceRect = faceRect,
            leftEyeProb = primaryFace.leftEyeOpenProbability,
            rightEyeProb = primaryFace.rightEyeOpenProbability,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            rotationDegrees = rotationDegrees
        ))
    }
    
    fun close() {
        Log.d(TAG, "=== Cerrando EyeTrackingAnalyzerWithDebug. Total frames: $frameCount ===")
        detector.close()
    }
}
