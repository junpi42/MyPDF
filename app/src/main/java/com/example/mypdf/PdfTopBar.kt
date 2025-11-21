package com.example.mypdf


import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledTopBar(
    onBack: () -> Unit,
    tunerOn: Boolean,
    concertModeOn: Boolean,
    onTunerClick: () -> Unit,
    onConcertClick: () -> Unit,
    darkMode: Boolean
) {
    val s = strings()
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val isTablet = config.screenWidthDp > 600
    val iconScale = if (isTablet) 1.5f else 1.0f
    val baseIconSize = 24.dp * iconScale
    val actionIconSize = 28.dp * iconScale
    val playIconSize = 32.dp * iconScale

    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp * (if (isTablet) 1.2f else 1f))) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = s.backDescription,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(baseIconSize)
                )
            }
        },
        actions = {
            // Tuner Button
            IconButton(onClick = onTunerClick, modifier = Modifier.size(48.dp * (if (isTablet) 1.2f else 1f))) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = s.tunerDescription,
                        tint = if (tunerOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(actionIconSize)
                    )
                    if (tunerOn) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(8.dp * iconScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                        )
                    }
                }
            }

            // Concert Mode Button
            IconButton(onClick = onConcertClick, modifier = Modifier.size(48.dp * (if (isTablet) 1.2f else 1f))) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = s.concertDescription,
                    tint = if (concertModeOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(playIconSize)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier.fillMaxWidth().height(if (isTablet) 80.dp else 64.dp)
    )
}
