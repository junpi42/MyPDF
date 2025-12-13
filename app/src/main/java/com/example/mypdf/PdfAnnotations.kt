package com.example.mypdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class DrawingPath(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false,
    val pressures: List<Float> = List(points.size) { 1f } // Presión normalizada (0-1) por cada punto
)

data class PageAnnotations(
    val pageIndex: Int,
    val paths: MutableList<DrawingPath> = mutableListOf()
)
