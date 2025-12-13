package com.example.mypdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class DrawingPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

data class PageAnnotations(
    val pageIndex: Int,
    val paths: MutableList<DrawingPath> = mutableListOf()
)
