package com.example.mypdf

import androidx.compose.runtime.Composable
import java.io.File

@Composable
fun PdfEditScreen(
    file: File,
    onBack: () -> Unit,
    isDarkMode: Boolean,
    isDaltonic: Boolean,
    onToggleDaltonic: () -> Unit,
    language: Language
) {
    PdfViewerScreen(
        file = file,
        onBack = onBack,
        isDarkMode = isDarkMode,
        isDaltonic = isDaltonic,
        onToggleDaltonic = onToggleDaltonic,
        language = language
    )
}
