package com.example.mypdf

import androidx.compose.runtime.Composable
import java.io.File

@Composable
fun PdfEditScreen(
    file: File,
    onBack: () -> Unit
) {
    PdfViewerScreen(file = file, onBack = onBack)
}
