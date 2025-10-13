package com.example.mypdf

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor

fun generatePdfThumbnail(file: File, width: Int = 200, height: Int = 250): Bitmap? {
    return try {
        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fileDescriptor)
        val page = renderer.openPage(0) // primera página

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        fileDescriptor.close()
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// Carpeta propia de la app para guardar los PDFs clonados
fun appPdfDir(context: Context): File {
    val dir = File(context.filesDir, "pdf_library")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

// Lista todos los PDFs guardados en la carpeta de la app
fun listAppPdfs(context: Context): List<File> {
    val dir = appPdfDir(context)
    return dir.listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()
}

// Clona (copia) un PDF elegido con el selector del sistema a la carpeta de la app
fun clonePdfIntoApp(context: Context, src: Uri): File {
    val name = queryDisplayName(context, src) ?: "document.pdf"
    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val destinationFile = uniqueName(File(appPdfDir(context), safeName))

    context.contentResolver.openInputStream(src).use { inputStream ->
        destinationFile.outputStream().use { outputStream ->
            inputStream?.copyTo(outputStream)
        }
    }
    return destinationFile
}

// --- Auxiliares (quedan privados en este archivo) ---

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) return it.getString(nameIndex)
        }
    }
    return null
}

private fun uniqueName(file: File): File {
    if (!file.exists()) return file
    val baseName = file.nameWithoutExtension
    val extension = if (file.extension.isNotEmpty()) ".${file.extension}" else ""
    var counter = 1
    while (true) {
        val candidate = File(file.parentFile, "${baseName}_${counter}$extension")
        if (!candidate.exists()) return candidate
        counter++
    }
}
