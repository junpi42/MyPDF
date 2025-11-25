package com.example.mypdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
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
    onDismiss: () -> Unit
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
            TutorialStep.CONCERT_MODE,
            TutorialStep.EXIT_CONCERT -> true
            else -> false
        }

        // En modo concierto no queremos que la X tape los botones de la esquina
        val showCloseButton = state.step != TutorialStep.CONCERT_MODE && state.step != TutorialStep.EXIT_CONCERT

        // Show tap to continue sólo cuando no haya interacción forzada
        val showTapToContinue = !forceInteraction

        TutorialBlocker(
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

@Composable
fun TutorialBlocker(
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

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .then(
                if (showTapToContinue) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNext
                    )
                } else {
                    Modifier
                }
            )
    ) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)

        Canvas(Modifier.fillMaxSize()) {
            val overlay = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                targetRect?.takeIf { !it.isEmpty }?.let { rect ->
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
            targetRect?.takeIf { !it.isEmpty }?.let { rect ->
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

        targetRect?.takeIf { !it.isEmpty }?.let { rect ->
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

    val preferRight = rect.right + spacingPx + measuredWidthPx <= screenWidthPx
    val preferAbove = rect.top - spacingPx - measuredHeightPx >= 0f

    var offsetXPx = if (preferRight) rect.right + spacingPx else rect.left - spacingPx - measuredWidthPx
    var offsetYPx = if (preferAbove) rect.top - spacingPx - measuredHeightPx else rect.bottom + spacingPx

    offsetXPx = offsetXPx.coerceIn(0f, screenWidthPx - measuredWidthPx)
    offsetYPx = offsetYPx.coerceIn(0f, screenHeightPx - measuredHeightPx)

    val offset = IntOffset(offsetXPx.roundToInt(), offsetYPx.roundToInt())

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
