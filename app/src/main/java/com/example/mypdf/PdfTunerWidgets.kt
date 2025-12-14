package com.example.mypdf

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Widgets visuales del afinador usados en el visor PDF

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TunnerSmall(
    tunner: AudioTuner,
    isDaltonic: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val state by tunner.tuningState.collectAsState(initial = null)

    // Premium Logic: Dark semi-transparent background, Color indicates status via Border/Text
    val statusColor = when (val s = state) {
        null -> Color.Gray
        else -> when {
            s.isInTune -> if (isDaltonic) Color(0xFF00BCD4) else Color(0xFF4CAF50) // Green/Cyan
            s.centsOff > 10.0 -> if (isDaltonic) Color(0xFFFF9800) else Color(0xFFE53935) // Red/Orange
            s.centsOff < -10.0 -> if (isDaltonic) Color(0xFFFF00FF) else Color(0xFF1E88E5) // Blue/Magenta
            else -> Color(0xFFFFA000) // Yellow (Close)
        }
    }

    val backgroundColor = Color.Black.copy(alpha = 0.6f) // Dark semi-transparent
    val contentColor = Color.White

    val cents = (state?.centsOff ?: 0.0).coerceIn(-50.0, 50.0)
    val normalized = (cents / 50.0).toFloat().coerceIn(-1f, 1f)

    val animNorm by animateFloatAsState(
        targetValue = normalized,
        animationSpec = tween(durationMillis = 80),
        label = "tuner_triangles"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp)) // Pill shape
            .background(backgroundColor)
            .border(3.dp, statusColor, RoundedCornerShape(24.dp)) // Thicker border (3.dp) and solid color (no alpha)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp), // Check padding
        contentAlignment = Alignment.Center
    ) {
        
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
             Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val centerX = w / 2f
                val range = w * 0.42f
                // Scale indicator
                val x = centerX + animNorm * range
                
                // --- Target Zone Visualization ---
                // Draw a rectangle in the center representing the "in-tune" range (+/- 12 cents approx)
                val inTuneThresholdNorm = (12.0 / 50.0).toFloat() // Based on logic
                val zoneWidth = range * inTuneThresholdNorm * 2 // Total width of zone
                
                drawRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(centerX - zoneWidth / 2f, h * 0.2f),
                    size = androidx.compose.ui.geometry.Size(zoneWidth, h * 0.6f)
                )

                // Optional: Center Line distinct
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(centerX, h * 0.2f),
                    end = Offset(centerX, h * 0.8f),
                    strokeWidth = 2.dp.toPx()
                )

                // Draw Moving Indicator
                drawLine(
                    color = statusColor,
                    start = Offset(x, h * 0.2f), // Top
                    end = Offset(x, h * 0.8f),   // Bottom
                    strokeWidth = 4.dp.toPx(), // Slightly thicker indicator
                    cap = StrokeCap.Round
                )
            }

            // Note Name Centered
            val note = state?.targetNote?.replace(Regex("\\d+"), "") ?: "--"
            Text(
                text = note,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
