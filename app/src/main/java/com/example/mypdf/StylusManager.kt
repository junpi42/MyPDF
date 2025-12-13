package com.example.mypdf

/**
 * Herramientas que pueden ser asignadas al botón del stylus
 */
enum class StylusTool {
    NONE,       // Mover/Ninguna herramienta
    MARKER,     // Rotulador
    HIGHLIGHTER,// Marcador
    ERASER,     // Borrador
    UNDO        // Deshacer (acción especial)
}

/**
 * Convierte StylusTool a string de herramienta
 */
fun StylusTool.toToolString(): String = when(this) {
    StylusTool.NONE -> "none"
    StylusTool.MARKER -> "marker"
    StylusTool.HIGHLIGHTER -> "highlighter"
    StylusTool.ERASER -> "eraser"
    StylusTool.UNDO -> "undo"
}

/**
 * Convierte string de herramienta a StylusTool
 */
fun String.toStylusTool(): StylusTool = when(this) {
    "none" -> StylusTool.NONE
    "marker" -> StylusTool.MARKER
    "highlighter" -> StylusTool.HIGHLIGHTER
    "eraser" -> StylusTool.ERASER
    "undo" -> StylusTool.UNDO
    else -> StylusTool.MARKER
}

