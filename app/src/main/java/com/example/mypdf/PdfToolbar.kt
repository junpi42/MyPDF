package com.example.mypdf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Barra de herramientas lateral extraída de PdfViewerScreen

@Composable
fun StyledLeftToolBar(
    selectedTool: String,
    penColor: Color,
    strokeWidth: Float,
    smoothingEnabled: Boolean,
    onSelectTool: (String) -> Unit,
    onColorClick: () -> Unit,
    onStrokeChange: (Float) -> Unit,
    onToggleSmoothing: () -> Unit,
    onUndo: () -> Unit,
    darkMode: Boolean,
    language: Language,
    modifier: Modifier = Modifier
) {
    val toolbarBg = if (darkMode) Color(0xFF1B1E23) else Color(0xFFF0F1F3)
    val s = strings()

    Surface(
        modifier = modifier.width(88.dp),
        color = toolbarBg,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StyledToolButton(
                icon = Icons.Default.TouchApp,
                label = s.toolMove,
                selected = selectedTool == "none",
                onClick = { onSelectTool("none") },
                darkMode = darkMode
            )

            StyledToolButton(
                icon = Icons.Default.Edit,
                label = s.toolPen,
                selected = selectedTool == "pen",
                onClick = { onSelectTool("pen") },
                darkMode = darkMode
            )

            StyledToolButton(
                icon = Icons.Default.Delete,
                label = s.toolErase,
                selected = selectedTool == "eraser",
                onClick = { onSelectTool("eraser") },
                darkMode = darkMode
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.width(60.dp),
                thickness = 1.dp,
                color = (if (darkMode) Color.White else Color.Black).copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = penColor,
                modifier = Modifier
                    .size(54.dp)
                    .clickable { onColorClick() }
            ) {}

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                StyledToolButton(
                    icon = Icons.Default.Remove,
                    label = s.toolThin,
                    selected = strokeWidth < 0.005f,
                    onClick = { onStrokeChange(0.003f) },
                    compact = true,
                    darkMode = darkMode
                )
                StyledToolButton(
                    icon = Icons.Default.HorizontalRule,
                    label = s.toolMedium,
                    selected = strokeWidth in 0.005f..0.008f,
                    onClick = { onStrokeChange(0.006f) },
                    compact = true,
                    darkMode = darkMode
                )
                StyledToolButton(
                    icon = Icons.Default.DragHandle,
                    label = s.toolThick,
                    selected = strokeWidth > 0.008f,
                    onClick = { onStrokeChange(0.01f) },
                    compact = true,
                    darkMode = darkMode
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.width(60.dp),
                thickness = 1.dp,
                color = (if (darkMode) Color.White else Color.Black).copy(alpha = 0.1f)
            )
            Spacer(Modifier.height(12.dp))

            StyledToolButton(
                icon = Icons.Default.Tune,
                label = s.toolSmooth,
                selected = smoothingEnabled,
                onClick = onToggleSmoothing,
                darkMode = darkMode
            )

            Spacer(modifier = Modifier.weight(1f))

            StyledToolButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = s.toolUndo,
                selected = false,
                onClick = onUndo,
                darkMode = darkMode
            )
        }
    }
}

@Composable
fun StyledToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
    darkMode: Boolean
) {
    val bg = when {
        selected && darkMode -> Color(0xFF2E466E)
        selected && !darkMode -> Color(0xFFDCE7FF)
        !selected && darkMode -> Color(0xFF272B33)
        else -> Color(0xFFE6E8EC)
    }

    val tint = when {
        selected && darkMode -> Color(0xFF90CAF9)
        selected && !darkMode -> Color(0xFF1D4ED8)
        !selected && darkMode -> Color(0xFFD0D3D8)
        else -> Color(0xFF2B2E34)
    }

    val size = if (compact) 42.dp else 56.dp

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = Modifier
            .size(size)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(if (compact) 20.dp else 24.dp)
            )
        }
    }
}
