package com.example.mypdf

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt


enum class TutorialStep {
    NONE,
    INTRO_DIALOG,
    NEW_CATEGORY,
    FAB,
    IMPORT_PDF_MENU,
    LONG_PRESS_FILE,
    OPTIONS_MENU, // Wait for menu
    RENAME_OPTION,
    RENAME_DIALOG, // Wait for rename
    OPEN_FILE,
    TOOLBOX,
    TUNER_BUTTON,
    TUNER_ACTIVE,
    TUNER_MENU,
    CONCERT_MODE,
    WINK_DETECTOR,
    WINK_CALIBRATION,
    EXIT_CONCERT,
    FINISHED
}

data class TutorialState(
    val step: TutorialStep = TutorialStep.NONE,
    val targetRect: Rect? = null
)

@Composable
fun TutorialOverlay(
    state: TutorialState,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    isTablet: Boolean = false
) {
    if (state.step == TutorialStep.NONE) return

    val s = strings()
    val (title, subtitle) = when (state.step) {
        TutorialStep.INTRO_DIALOG -> s.tutorialIntroTitle to s.tutorialIntroBody
        TutorialStep.NEW_CATEGORY -> s.tutorialNewCategoryTitle to s.tutorialNewCategoryBody
        TutorialStep.FAB -> s.tutorialFabTitle to s.tutorialFabBody
        TutorialStep.IMPORT_PDF_MENU -> s.tutorialImportTitle to s.tutorialImportBody
        TutorialStep.LONG_PRESS_FILE -> s.tutorialLongPressTitle to s.tutorialLongPressBody
        TutorialStep.OPTIONS_MENU -> s.tutorialOptionsTitle to s.tutorialOptionsBody
        TutorialStep.RENAME_DIALOG -> s.tutorialRenameTitle to s.tutorialRenameBody
        TutorialStep.OPEN_FILE -> s.tutorialOpenFileTitle to s.tutorialOpenFileBody
        TutorialStep.TOOLBOX -> s.tutorialToolboxTitle to s.tutorialToolboxBody
        TutorialStep.TUNER_BUTTON -> s.tutorialTunerButtonTitle to s.tutorialTunerButtonBody
        TutorialStep.TUNER_ACTIVE -> s.tutorialTunerActiveTitle to s.tutorialTunerActiveBody
        TutorialStep.TUNER_MENU -> s.tutorialTunerMenuTitle to s.tutorialTunerMenuBody
        TutorialStep.CONCERT_MODE -> s.tutorialConcertModeTitle to s.tutorialConcertModeBody
        TutorialStep.WINK_DETECTOR -> s.tutorialWinkDetectorTitle to s.tutorialWinkDetectorBody
        TutorialStep.WINK_CALIBRATION -> s.tutorialWinkCalibrationTitle to s.tutorialWinkCalibrationBody
        TutorialStep.EXIT_CONCERT -> s.tutorialExitConcertTitle to s.tutorialExitConcertBody
        TutorialStep.FINISHED -> s.tutorialFinishedTitle to s.tutorialFinishedBody
        else -> "" to ""
    }

    if (state.step == TutorialStep.INTRO_DIALOG) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(subtitle) },
            confirmButton = {
                Button(onClick = onNext) {
                    Text(s.tutorialStart)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(s.tutorialSkip)
                }
            }
        )
    } else if (state.step == TutorialStep.FINISHED) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = { Text(subtitle) },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("Exit Tutorial")
                }
            }
        )
    } else {
        val forceInteraction = when (state.step) {
            TutorialStep.NEW_CATEGORY,
            TutorialStep.FAB,
            TutorialStep.IMPORT_PDF_MENU,
            TutorialStep.LONG_PRESS_FILE,
            TutorialStep.OPTIONS_MENU,
            TutorialStep.OPEN_FILE,
            TutorialStep.TUNER_BUTTON,
            TutorialStep.TUNER_ACTIVE,
            TutorialStep.TUNER_ACTIVE,
            TutorialStep.CONCERT_MODE,
            TutorialStep.WINK_DETECTOR,
            TutorialStep.WINK_CALIBRATION,
            TutorialStep.EXIT_CONCERT -> true
            else -> false
        }

        // En modo concierto no queremos que la X tape los botones de la esquina
        val showCloseButton = state.step != TutorialStep.CONCERT_MODE && state.step != TutorialStep.EXIT_CONCERT

        // Show tap to continue sólo cuando no haya interacción forzada
        val showTapToContinue = !forceInteraction

        TutorialBlocker(
            step = state.step,
            isTablet = isTablet,
            targetRect = state.targetRect,
            onSkip = onDismiss,
            onNext = if (forceInteraction) {{}} else onNext,
            message = title,
            secondaryMessage = subtitle,
            showTapToContinue = showTapToContinue,
            showCloseButton = showCloseButton
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TutorialBlocker(
    step: TutorialStep,
    isTablet: Boolean,
    targetRect: Rect?,
    onSkip: () -> Unit,
    onNext: () -> Unit = {},
    message: String,
    secondaryMessage: String? = null,
    showTapToContinue: Boolean = true,
    showCloseButton: Boolean = true
) {
    val density = LocalDensity.current
    val spotlightCorner = 20.dp
    val spacing = 16.dp
    var rootOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .pointerInteropFilter { motionEvent ->
                // For dialog steps where user needs to interact with AlertDialogs,
                // don't block any touch events since AlertDialogs render in separate windows
                val isDialogStep = step == TutorialStep.RENAME_DIALOG || 
                                   step == TutorialStep.OPTIONS_MENU ||
                                   step == TutorialStep.TUNER_MENU
                if (isDialogStep) {
                    return@pointerInteropFilter false
                }
                
                val position = Offset(motionEvent.x, motionEvent.y)
                
                // Calculate anchor rect in current coordinates
                val anchorRectLocal = if (targetRect != null && !targetRect.isEmpty) {
                    targetRect.translate(-rootOffset.x, -rootOffset.y)
                } else {
                    null
                }
                
                val isInsideSpotlight = anchorRectLocal?.contains(position) == true
                
                if (isInsideSpotlight) {
                    // Inside spotlight: return false to let the event pass through
                    false
                } else {
                    // Outside spotlight: consume the event
                    if (showTapToContinue && motionEvent.action == MotionEvent.ACTION_UP) {
                        onNext()
                    }
                    true
                }
            }
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)

        val fallback = fallbackRectForStep(
            step = step,
            isTablet = isTablet,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            density = density
        )
        
        val anchorRect = if (targetRect != null && !targetRect.isEmpty) {
            targetRect.translate(-rootOffset.x, -rootOffset.y)
        } else {
            fallback
        }

        Canvas(Modifier.fillMaxSize()) {
            val overlay = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                anchorRect.takeIf { !it.isEmpty }?.let { rect ->
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = rect.left,
                            top = rect.top,
                            right = rect.right,
                            bottom = rect.bottom,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(spotlightCorner.toPx())
                        )
                    )
                }
                fillType = PathFillType.EvenOdd
            }
            drawPath(path = overlay, color = Color.Black.copy(alpha = 0.75f), style = Fill)
            anchorRect.takeIf { !it.isEmpty }?.let { rect ->
                val size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
                drawRoundRect(
                    color = accent.copy(alpha = 0.25f),
                    topLeft = Offset(rect.left, rect.top),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(spotlightCorner.toPx()),
                    style = Fill
                )
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(rect.left, rect.top),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(spotlightCorner.toPx()),
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        if (showCloseButton) {
            IconButton(
                onClick = onSkip,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(Icons.Filled.Close, contentDescription = strings().tutorialExit, tint = Color.White)
            }
        }

        anchorRect.takeIf { !it.isEmpty }?.let { rect ->
            StickyNote(
                rect = rect,
                message = message,
                secondaryMessage = secondaryMessage,
                spacing = spacing,
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                onNext = onNext,
                showTapToContinue = showTapToContinue
            )
        }
    }
}

private fun fallbackRectForStep(
    step: TutorialStep,
    isTablet: Boolean,
    screenWidthPx: Float,
    screenHeightPx: Float,
    density: androidx.compose.ui.unit.Density
): Rect {
    fun rectAt(
        centerXFraction: Float,
        centerYFraction: Float,
        widthDp: Dp = 140.dp,
        heightDp: Dp = 90.dp
    ): Rect {
        val widthPx = with(density) { widthDp.toPx() }
        val heightPx = with(density) { heightDp.toPx() }
        val cx = screenWidthPx * centerXFraction
        val cy = screenHeightPx * centerYFraction
        return Rect(
            cx - widthPx / 2f,
            cy - heightPx / 2f,
            cx + widthPx / 2f,
            cy + heightPx / 2f
        )
    }

    return if (isTablet) {
        when (step) {
            TutorialStep.NEW_CATEGORY -> rectAt(0.12f, 0.18f, widthDp = 160.dp, heightDp = 72.dp) // sidebar
            TutorialStep.FAB, TutorialStep.IMPORT_PDF_MENU -> rectAt(0.9f, 0.85f)
            TutorialStep.LONG_PRESS_FILE,
            TutorialStep.OPTIONS_MENU,
            TutorialStep.RENAME_DIALOG,
            TutorialStep.OPEN_FILE -> rectAt(0.62f, 0.55f)
            TutorialStep.TOOLBOX -> rectAt(0.12f, 0.55f, widthDp = 120.dp, heightDp = 180.dp) // barra lateral de edición
            TutorialStep.TUNER_BUTTON -> rectAt(0.88f, 0.12f, widthDp = 140.dp, heightDp = 72.dp)
            TutorialStep.TUNER_ACTIVE,
            TutorialStep.TUNER_MENU -> rectAt(0.62f, 0.25f, widthDp = 220.dp, heightDp = 96.dp)
            TutorialStep.CONCERT_MODE,
            TutorialStep.WINK_DETECTOR,
            TutorialStep.WINK_CALIBRATION,
            TutorialStep.EXIT_CONCERT -> rectAt(0.88f, 0.12f, widthDp = 140.dp, heightDp = 72.dp)
            else -> rectAt(0.6f, 0.5f)
        }
    } else {
        when (step) {
            TutorialStep.NEW_CATEGORY -> rectAt(0.18f, 0.18f)
            TutorialStep.FAB, TutorialStep.IMPORT_PDF_MENU -> rectAt(0.86f, 0.82f)
            TutorialStep.LONG_PRESS_FILE,
            TutorialStep.OPTIONS_MENU,
            TutorialStep.RENAME_DIALOG,
            TutorialStep.OPEN_FILE -> rectAt(0.5f, 0.55f)
            TutorialStep.TOOLBOX -> rectAt(0.5f, 0.88f)
            TutorialStep.TUNER_BUTTON -> rectAt(0.9f, 0.12f, widthDp = 120.dp, heightDp = 72.dp)
            TutorialStep.TUNER_ACTIVE,
            TutorialStep.TUNER_MENU -> rectAt(0.5f, 0.25f)
            TutorialStep.CONCERT_MODE,
            TutorialStep.WINK_DETECTOR,
            TutorialStep.WINK_CALIBRATION,
            TutorialStep.EXIT_CONCERT -> rectAt(0.88f, 0.12f)
            else -> rectAt(0.5f, 0.5f)
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.StickyNote(
    rect: Rect,
    message: String,
    secondaryMessage: String?,
    spacing: Dp,
    screenWidthPx: Float,
    screenHeightPx: Float,
    onNext: () -> Unit,
    showTapToContinue: Boolean = true
) {
    val density = LocalDensity.current
    var noteSize by remember { mutableStateOf(IntSize.Zero) }
    val spacingPx = with(density) { spacing.toPx() }
    val measuredWidthPx = if (noteSize.width > 0) noteSize.width.toFloat() else with(density) { 240.dp.toPx() }
    val measuredHeightPx = if (noteSize.height > 0) noteSize.height.toFloat() else with(density) { 140.dp.toPx() }

    // Calculate available space
    val spaceTop = rect.top - spacingPx
    val spaceBottom = screenHeightPx - rect.bottom - spacingPx
    val spaceLeft = rect.left - spacingPx
    val spaceRight = screenWidthPx - rect.right - spacingPx

    // Determine best position
    // Priority: Top, Bottom, Left, Right
    // For vertical placement, try to center horizontally.
    // For horizontal placement, try to center vertically.

    var finalX = 0f
    var finalY = 0f

    val candidates = mutableListOf<Pair<String, Pair<Float, Float>>>()
    if (spaceTop >= measuredHeightPx) {
        val cx = rect.left + rect.width / 2f - measuredWidthPx / 2f
        val cy = rect.top - spacingPx - measuredHeightPx
        candidates += "top" to (cx to cy)
    }
    if (spaceBottom >= measuredHeightPx) {
        val cx = rect.left + rect.width / 2f - measuredWidthPx / 2f
        val cy = rect.bottom + spacingPx
        candidates += "bottom" to (cx to cy)
    }
    if (spaceLeft >= measuredWidthPx) {
        val cx = rect.left - spacingPx - measuredWidthPx
        val cy = rect.top + rect.height / 2f - measuredHeightPx / 2f
        candidates += "left" to (cx to cy)
    }
    if (spaceRight >= measuredWidthPx) {
        val cx = rect.right + spacingPx
        val cy = rect.top + rect.height / 2f - measuredHeightPx / 2f
        candidates += "right" to (cx to cy)
    }

    val best = candidates.maxByOrNull { (dir, _) ->
        when (dir) {
            "top" -> spaceTop * rect.width
            "bottom" -> spaceBottom * rect.width
            "left" -> spaceLeft * rect.height
            "right" -> spaceRight * rect.height
            else -> 0f
        }
    }

    if (best != null) {
        finalX = best.second.first
        finalY = best.second.second
    } else {
        // Fallback: center near the rect
        finalX = rect.left + rect.width / 2f - measuredWidthPx / 2f
        finalY = rect.top + rect.height / 2f - measuredHeightPx / 2f
    }

    // Clamp to screen bounds with some padding
    val padding = spacingPx
    finalX = finalX.coerceIn(padding, screenWidthPx - measuredWidthPx - padding)
    finalY = finalY.coerceIn(padding, screenHeightPx - measuredHeightPx - padding)

    val offset = IntOffset(finalX.roundToInt(), finalY.roundToInt())

    // Standard positioning for all steps
    val baseModifier = Modifier
        .offset { offset }
        .widthIn(min = 200.dp, max = 260.dp)
        .wrapContentHeight()
        .onGloballyPositioned { noteSize = it.size }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = baseModifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(message, style = MaterialTheme.typography.titleMedium)
            if (secondaryMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(secondaryMessage, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
