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
import androidx.compose.ui.graphics.drawscope.Stroke
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

    val bg = when (val s = state) {
        null -> Color.DarkGray
        else -> when {
            s.isInTune -> if (isDaltonic) Color(0xFF00BCD4) else Color(0xFF4CAF50)
            s.centsOff > 10.0 -> if (isDaltonic) Color(0xFFFF9800) else Color(0xFFE53935)
            s.centsOff < -10.0 -> if (isDaltonic) Color(0xFFFF00FF) else Color(0xFF1E88E5)
            else -> Color(0xFFFFA000)
        }
    }

    val markerColor = if (bg.luminance() > 0.5f) Color.Black else Color.White

    val cents = (state?.centsOff ?: 0.0).coerceIn(-50.0, 50.0)
    val normalized = (cents / 50.0).toFloat().coerceIn(-1f, 1f)

    val animNorm by animateFloatAsState(
        targetValue = normalized,
        animationSpec = tween(durationMillis = 80),
        label = "tuner_triangles"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            val centerX = w / 2f
            val range = w * 0.42f
            val x = centerX + animNorm * range

            val triW = h * 0.32f
            val triH = h * 0.30f

            val inTuneCents = 12.0
            val maxCents = 50.0
            val normLimit = (inTuneCents / maxCents).toFloat()

            val tickLen = h * 0.18f
            val tickStroke = h * 0.04f
            val halfStroke = tickStroke / 2f

            val topTri = Path().apply {
                moveTo(x - triW / 2f, -halfStroke)
                lineTo(x + triW / 2f, -halfStroke)
                lineTo(x, triH)
                close()
            }

            val bottomTri = Path().apply {
                moveTo(x - triW / 2f, h + halfStroke)
                lineTo(x + triW / 2f, h + halfStroke)
                lineTo(x, h - triH)
                close()
            }

            drawPath(topTri, color = markerColor)
            drawPath(bottomTri, color = markerColor)

            val xLeft = centerX - normLimit * range
            val xRight = centerX + normLimit * range

            drawLine(
                color = markerColor,
                start = Offset(xLeft, -halfStroke),
                end = Offset(xLeft, tickLen),
                strokeWidth = tickStroke
            )
            drawLine(
                color = markerColor,
                start = Offset(xRight, -halfStroke),
                end = Offset(xRight, tickLen),
                strokeWidth = tickStroke
            )

            drawLine(
                color = markerColor,
                start = Offset(xLeft, h + halfStroke),
                end = Offset(xLeft, h - tickLen),
                strokeWidth = tickStroke
            )
            drawLine(
                color = markerColor,
                start = Offset(xRight, h + halfStroke),
                end = Offset(xRight, h - tickLen),
                strokeWidth = tickStroke
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            val note = state?.targetNote
                ?.replace(Regex("\\d+"), "")
                ?: "--"

            Text(
                text = note,
                color = Color.Black,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
