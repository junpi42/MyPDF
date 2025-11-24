package com.example.mypdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.mypdf.Language

/**
 * Modelo de textos de la app. Añade aquí las claves que necesites.
 */
interface AppStrings {
    // General / comunes
    val close: String
    val cancel: String
    val create: String
    val delete: String
    val rename: String
    val settingsTitle: String
    val languageLabel: String
    val gridSize: String

    // Biblioteca
    val themes: String
    val categories: String
    val newCategory: String
    val newFolder: String
    val folderName: String
    val searchInLibrary: String
    val importPdf: String
    val createFolder: String
    val emptyList: String
    val searching: String
    val cannotCreateCategory: String
    val cannotCreateFolder: String
    val cannotRename: String
    val deleteError: String
    val confirmDeleteTitle: String
    fun confirmDeleteMessage(isFolder: Boolean): String

    // Ordenación
    val sortByName: String
    val sortByDate: String
    val sortBySize: String

    // Rename dialog
    val newNameLabel: String
    val save: String

    // Tuner dialogs
    val tunerSettingsTitle: String
    val tunerNoisyEnv: String
    val tunerDaltonismSoon: String

    // Top bar
    val backDescription: String
    val tunerDescription: String
    val concertDescription: String

    // Viewer toolbar
    val toolMove: String
    val toolPen: String
    val toolErase: String
    val toolThin: String
    val toolMedium: String
    val toolThick: String
    val toolSmooth: String
    val toolUndo: String
    
    // Onboarding
    val welcomeTitle: String
    val chooseLanguage: String
    val chooseTheme: String
    val chooseDaltonism: String


    val daltonismOption: String
    val next: String
    val onboardingDisclaimer: String
    val finish: String

    // Registration / Login
    val registrationTitle: String
    val loginTitle: String
    val username: String
    val email: String
    val password: String
    val register: String
    val login: String
    val googleSignIn: String
    val continueGuest: String
    val skip: String
    val welcomeUser: String
    val configuring: String
    val welcomeSubtitle: String
    val orSeparator: String
}

object StringsEs : AppStrings {
    override val close = "Cerrar"
    override val cancel = "Cancelar"
    override val create = "Crear"
    override val delete = "Eliminar"
    override val rename = "Renombrar"
    override val settingsTitle = "Ajustes"
    override val languageLabel = "Idioma"
    override val gridSize = "Tamaño de cuadrícula"

    override val themes = "Temas"
    override val categories = "Categorías"
    override val newCategory = "Nueva categoría"
    override val newFolder = "Nueva carpeta"
    override val folderName = "Nombre carpeta"
    override val searchInLibrary = "Buscar en biblioteca"
    override val importPdf = "Importar PDF"
    override val createFolder = "Crear carpeta"
    override val emptyList = "Sin elementos"
    override val searching = "Buscando…"
    override val cannotCreateCategory = "No se pudo crear la categoría"
    override val cannotCreateFolder = "No se pudo crear carpeta"
    override val cannotRename = "No se pudo renombrar"
    override val deleteError = "No se pudo eliminar"
    override val confirmDeleteTitle = "Eliminar"
    override fun confirmDeleteMessage(isFolder: Boolean): String =
        if (isFolder) "¿Seguro que quieres eliminar la carpeta? Esta acción no se puede deshacer."
        else "¿Seguro que quieres eliminar el PDF? Esta acción no se puede deshacer."

    override val sortByName = "Nombre"
    override val sortByDate = "Fecha"
    override val sortBySize = "Tamaño"

    override val newNameLabel = "Nuevo nombre"
    override val save = "Guardar"

    override val tunerSettingsTitle = "Ajustes del afinador"
    override val tunerNoisyEnv = "Ambiente ruidoso"
    override val tunerDaltonismSoon = "Daltonismo"

    override val backDescription = "Volver"
    override val tunerDescription = "Afinador"
    override val concertDescription = "Concert"

    override val toolMove = "Mover"
    override val toolPen = "Lápiz"
    override val toolErase = "Borrar"
    override val toolThin = "Fino"
    override val toolMedium = "Medio"
    override val toolThick = "Grueso"
    override val toolSmooth = "Suave"
    override val toolUndo = "Deshacer"
    
    override val welcomeTitle = "Bienvenido"
    override val chooseLanguage = "Elige tu idioma"
    override val chooseTheme = "Elige un tema"
    override val chooseDaltonism = "Modo daltónico"
    override val finish = "Finalizar"

    override val daltonismOption = "Daltonismo"
    override val next = "Siguiente"
    override val onboardingDisclaimer = "No te preocupes, puedes volver a cambiar esto en configuración."

    override val registrationTitle = "Crear cuenta"
    override val loginTitle = "Iniciar sesión"
    override val username = "Nombre de usuario"
    override val email = "Correo electrónico"
    override val password = "Contraseña"
    override val register = "Registrarse"
    override val login = "Entrar"
    override val googleSignIn = "Continuar con Google"
    override val continueGuest = "Continuar como invitado"
    override val skip = "Omitir"
    override val welcomeUser = "Bienvenido"
    override val configuring = "Estamos configurándolo todo..."
    override val welcomeSubtitle = "Tu compañero creativo"
    override val orSeparator = "o"
}

object StringsEn : AppStrings {
    override val close = "Close"
    override val cancel = "Cancel"
    override val create = "Create"
    override val delete = "Delete"
    override val rename = "Rename"
    override val settingsTitle = "Settings"
    override val languageLabel = "Language"
    override val gridSize = "Grid Size"

    override val themes = "Themes"
    override val categories = "Categories"
    override val newCategory = "New category"
    override val newFolder = "New folder"
    override val folderName = "Folder name"
    override val searchInLibrary = "Search in library"
    override val importPdf = "Import PDF"
    override val createFolder = "Create folder"
    override val emptyList = "No items"
    override val searching = "Searching…"
    override val cannotCreateCategory = "Could not create category"
    override val cannotCreateFolder = "Could not create folder"
    override val cannotRename = "Could not rename"
    override val deleteError = "Could not delete"
    override val confirmDeleteTitle = "Delete"
    override fun confirmDeleteMessage(isFolder: Boolean): String =
        if (isFolder) "Are you sure you want to delete the folder? This action cannot be undone."
        else "Are you sure you want to delete the PDF? This action cannot be undone."

    override val sortByName = "Name"
    override val sortByDate = "Date"
    override val sortBySize = "Size"

    override val newNameLabel = "New name"
    override val save = "Save"

    override val tunerSettingsTitle = "Tuner settings"
    override val tunerNoisyEnv = "Noisy environment"
    override val tunerDaltonismSoon = "Color blindness"

    override val backDescription = "Back"
    override val tunerDescription = "Tuner"
    override val concertDescription = "Concert"

    override val toolMove = "Move"
    override val toolPen = "Pen"
    override val toolErase = "Erase"
    override val toolThin = "Thin"
    override val toolMedium = "Medium"
    override val toolThick = "Thick"
    override val toolSmooth = "Smooth"
    override val toolUndo = "Undo"

    override val welcomeTitle = "Welcome"
    override val chooseLanguage = "Choose your language"
    override val chooseTheme = "Choose a theme"
    override val chooseDaltonism = "Color blindness mode"
    override val finish = "Finish"

    override val daltonismOption = "Color blindness"
    override val next = "Next"
    override val onboardingDisclaimer = "Don't worry, you can change this later in settings."

    override val registrationTitle = "Create account"
    override val loginTitle = "Log in"
    override val username = "Username"
    override val email = "Email"
    override val password = "Password"
    override val register = "Sign up"
    override val login = "Log in"
    override val googleSignIn = "Continue with Google"
    override val continueGuest = "Continue as Guest"
    override val skip = "Skip"
    override val welcomeUser = "Welcome"
    override val configuring = "Configuring everything..."
    override val welcomeSubtitle = "Your creative companion"
    override val orSeparator = "or"
}

fun stringsFor(language: Language): AppStrings {
    return when (language) {
        Language.EN -> StringsEn
        Language.ES -> StringsEs
    }
}

val LocalStrings = staticCompositionLocalOf<AppStrings> {
    error("AppStrings not provided")
}

@Composable
fun ProvideStrings(language: Language, content: @Composable () -> Unit) {
    val strings = stringsFor(language)
    androidx.compose.runtime.CompositionLocalProvider(LocalStrings provides strings) {
        content()
    }
}

@Composable
fun strings(): AppStrings = LocalStrings.current
