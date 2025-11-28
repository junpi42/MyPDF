package com.example.mypdf

import android.content.res.Configuration
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.*
import androidx.compose.animation.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledTopBar(
    onBack: () -> Unit,
    tunerOn: Boolean,
    concertModeOn: Boolean,
    onTunerClick: () -> Unit,
    onConcertClick: () -> Unit,
    darkMode: Boolean,
    highlightBack: Boolean = false,
    onTunerPositioned: (Rect) -> Unit = {},
    onConcertPositioned: (Rect) -> Unit = {},
    onBackPositioned: (Rect) -> Unit = {},
    centerContent: @Composable () -> Unit = {},
    showUndo: Boolean = false,
    onUndo: () -> Unit = {}
) {
    val s = strings()
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = config.screenWidthDp > 600
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val height = if (isTablet) 80.dp else 64.dp
    val horizontalPadding = if (isTablet && isLandscape) 0.dp else 8.dp
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().height(height),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Back Button
            val backScale = if (highlightBack) {
                val infiniteTransition = rememberInfiniteTransition(label = "backPulseScale")
                infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                ).value
            } else {
                1f
            }
            
            val backColor = if (highlightBack) {
                val infiniteTransition = rememberInfiniteTransition(label = "backPulseColor")
                infiniteTransition.animateColor(
                    initialValue = MaterialTheme.colorScheme.onSurface,
                    targetValue = MaterialTheme.colorScheme.primary,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "color"
                ).value
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(height)
                    .scale(backScale)
                    .onGloballyPositioned { coordinates ->
                        onBackPositioned(coordinates.boundsInRoot())
                    }
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = s.backDescription,
                    tint = backColor,
                    modifier = Modifier.size(24.dp * (if (isTablet) 1.5f else 1f))
                )
            }

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                centerContent()
            }

            // Tuner Button
            IconButton(
                onClick = onTunerClick,
                modifier = Modifier
                    .size(height)
                    .onGloballyPositioned { coordinates ->
                        onTunerPositioned(coordinates.boundsInRoot())
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = s.tunerDescription,
                        tint = if (tunerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp * (if (isTablet) 1.5f else 1f))
                    )
                    if (tunerOn) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                }
            }

            // Concert Mode Button
            IconButton(
                onClick = onConcertClick,
                modifier = Modifier
                    .size(height)
                    .onGloballyPositioned { coordinates ->
                        onConcertPositioned(coordinates.boundsInRoot())
                    }
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = s.concertDescription,
                    tint = if (concertModeOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp * (if (isTablet) 1.5f else 1f))
                )
            }


         }
     }
 }
