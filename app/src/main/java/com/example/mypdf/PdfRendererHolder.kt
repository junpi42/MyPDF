package com.example.mypdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

private const val TAG = "PDF_TIMING"

// Holder de PdfRenderer extraído de PdfViewerScreen, misma lógica
class PdfRendererHolder(file: File) {
    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(pfd)

    @Volatile
    private var closed = false
    private val renderMutex = Mutex()

    private val maxKb =
        (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceAtLeast(8 * 1024)

    private fun cacheKey(pageIndex: Int, targetWidth: Int): Int {
        val clamped = targetWidth.coerceIn(200, 1920)
        return (pageIndex shl 16) or (clamped and 0xFFFF)
    }

    private val cache = object : LruCache<Int, Bitmap>(maxKb) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    val pageCount: Int
        get() = renderer.pageCount

    suspend fun renderPageQuick(index: Int, quickTargetW: Int): Bitmap? =
        renderInternal(index, quickTargetW)

    suspend fun renderPageToWidth(index: Int, targetW: Int): Bitmap? =
        renderInternal(index, targetW)

    private suspend fun renderInternal(index: Int, targetW: Int): Bitmap? {
        if (closed) return null

        val clampedTargetW = targetW.coerceIn(200, 1920)
        val key = cacheKey(index, clampedTargetW)

        cache.get(key)?.let { return it }

        return try {
            renderMutex.withLock {
                if (closed) return null
                if (index !in 0 until renderer.pageCount) return null

                cache.get(key)?.let { return it }

                val pageLocal = renderer.openPage(index)
                pageLocal.use { page ->
                    val srcW = page.width
                    val srcH = page.height

                    val scale = (clampedTargetW.toFloat() / srcW).coerceIn(0.1f, 8f)
                    val outW = (srcW * scale).toInt().coerceAtLeast(1)
                    val outH = (srcH * scale).toInt().coerceAtLeast(1)

                    val bitmap = createBitmap(outW, outH)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    cache.put(key, bitmap)
                    bitmap
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "render failed idx=$index w=$targetW: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        runCatching { cache.evictAll() }
    }
}

