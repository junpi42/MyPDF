package com.example.mypdf


import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp


enum class TutorialStep {
    NONE,
    INTRO_DIALOG,
    NEW_CATEGORY,
    FAB,
    IMPORT_PDF_MENU,
    FILE_LIST, // Wait for file to appear
    LONG_PRESS_FILE,
    OPTIONS_MENU, // Wait for menu
    RENAME_OPTION,
    RENAME_DIALOG, // Wait for rename
    OPEN_FILE
}

data class TutorialState(
    val step: TutorialStep = TutorialStep.NONE,
    val targetRect: Rect? = null
)

@Composable
fun TutorialBlocker(
    targetRect: Rect?,
    onExit: () -> Unit,
    message: String
) {
    var offset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { 
                offset = it.positionInRoot() 
            }
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val density = LocalDensity.current
        val maxHeightPx = constraints.maxHeight
        
        val r = targetRect?.translate(-offset.x, -offset.y) ?: Rect.Zero
        
        val leftDp = with(density) { r.left.toDp() }
        val topDp = with(density) { r.top.toDp() }
        val widthDp = with(density) { r.width.toDp() }
        val heightDp = with(density) { r.height.toDp() }
        
        // We need exact Dp for the 4 boxes.
        // Top Box
        Box(
            Modifier
                .fillMaxWidth()
                .height(topDp)
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures {} } // Block
        )
        
        // Bottom Box
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = topDp + heightDp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures {} } // Block
        )
        
        // Left Box
        Box(
            Modifier
                .width(leftDp)
                .padding(top = topDp)
                .height(heightDp)
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures {} } // Block
        )
        
        // Right Box
        Box(
            Modifier
                .padding(start = leftDp + widthDp, top = topDp)
                .fillMaxWidth()
                .height(heightDp)
                .background(Color.Black.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures {} } // Block
        )
        
        // Message and Exit button
        // Position message near the target if possible, or just at the bottom/top
        Box(Modifier.fillMaxSize()) {
            // Exit button always top right
            IconButton(
                onClick = onExit,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(Icons.Default.Close, contentDescription = "Exit Tutorial", tint = Color.White)
            }
            
            // Message
            // If target is in top half, show message in bottom half, and vice versa
            // If targetRect is Zero (e.g. dialog or menu not yet visible), show message in center
            val isZero = r == Rect.Zero
            val isTop = r.center.y < (maxHeightPx / 2f)
            
            Card(
                modifier = Modifier
                    .align(if (isZero) Alignment.Center else if (isTop) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(32.dp)
                    .padding(top = if (!isTop && !isZero) 64.dp else 0.dp), // Avoid status bar if top
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
