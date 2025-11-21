package com.example.mypdf

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val barBg = if (darkMode) Color(0xFF111111) else Color(0xFFF0F1F3)
    val iconTint = if (darkMode) Color.White else Color(0xFF111111)
    val s = strings()

    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = s.backDescription,
                    tint = iconTint,
                    modifier = Modifier.size(30.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = onTunerClick) {
                Box(modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = s.tunerDescription,
                        tint = iconTint,
                        modifier = Modifier.matchParentSize()
                    )
                    if (tunerOn) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color(0xFFFF5252), RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            IconButton(onClick = onConcertClick) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = s.concertDescription,
                    tint = if (concertModeOn) Color(0xFFFFC107) else iconTint,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = barBg,
            titleContentColor = iconTint,
            navigationIconContentColor = iconTint,
            actionIconContentColor = iconTint
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(0.5.dp, if (darkMode) Color(0xFF1C1C1C) else Color(0xFFE0E0E0))
    )
}
