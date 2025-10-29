package com.example.mypdf

import android.content.Context
import java.io.File
import androidx.core.content.edit

private const val PREFS_NAME = "reader_state"

private fun keyFor(file: File): String = "last_page::" + file.absolutePath

fun getLastPage(context: Context, file: File): Int {
    return try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getInt(keyFor(file), 0).coerceAtLeast(0)
    } catch (_: Exception) { 0 }
}

fun saveLastPage(context: Context, file: File, pageIndex: Int) {
    try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putInt(keyFor(file), pageIndex.coerceAtLeast(0)) }
    } catch (_: Exception) { }
}

