package com.example.mypdf

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppSettings(
    val language: String = "EN",
    val isDarkMode: Boolean = false,
    val isDaltonic: Boolean = false,
    val gridScale: Float = 1.0f,
    val tutorialCompleted: Boolean = false
)

object SettingsManager {
    private const val FILE_NAME = "app_settings.json"

    fun loadSettings(context: Context): AppSettings? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val jsonString = file.readText()
            Json.decodeFromString<AppSettings>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveSettings(context: Context, settings: AppSettings) {
        val file = File(context.filesDir, FILE_NAME)
        try {
            val jsonString = Json.encodeToString(AppSettings.serializer(), settings)
            file.writeText(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
