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
        val page = renderer.openPage(0)

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

fun appPdfDir(context: Context): File {
    val dir = File(context.filesDir, "pdf_library")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

fun libraryDirFor(context: Context, relativePath: String): File {
    val clean = relativePath.trim().trimStart('/').trimEnd('/')
    val safe = clean.split('/').filter { it.isNotBlank() && it != ".." }.joinToString(File.separator)
    val base = appPdfDir(context)
    val target = if (safe.isEmpty()) base else File(base, safe)
    if (!target.exists()) target.mkdirs()
    return target
}

fun listAppPdfs(context: Context): List<File> {
    val dir = appPdfDir(context)
    return dir.listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()
}

fun listLibraryFolder(context: Context, relativePath: String): Pair<List<File>, List<File>> {
    val dir = libraryDirFor(context, relativePath)
    val children = dir.listFiles() ?: emptyArray()
    val folders = children.filter { it.isDirectory }.sortedBy { it.name.lowercase() }
    val pdfs = children.filter { it.isFile && it.extension.equals("pdf", true) }
    return folders to pdfs
}

fun createLibraryFolder(context: Context, relativePath: String, name: String): File? {
    val safeName = name.trim().replace(Regex("[^a-zA-Z0-9._ -]"), "_").trim().ifEmpty { return null }
    val dir = libraryDirFor(context, relativePath)
    val target = File(dir, safeName)
    if (target.exists()) return target.takeIf { it.isDirectory }
    return if (target.mkdirs()) target else null
}

fun clonePdfIntoApp(context: Context, src: Uri, relativePath: String = ""): File {
    val name = queryDisplayName(context, src) ?: "document.pdf"
    val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    val destDir = libraryDirFor(context, relativePath)
    val destinationFile = uniqueName(File(destDir, safeName))

    context.contentResolver.openInputStream(src).use { inputStream ->
        destinationFile.outputStream().use { outputStream ->
            inputStream?.copyTo(outputStream)
        }
    }
    return destinationFile
}

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
