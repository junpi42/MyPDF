package com.example.mypdf

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import com.example.mypdf.ui.theme.MyPDFTheme

// PERF helper está definido al final del archivo (no necesitamos imports extra aquí)
// <<< PERF IMPORTS <<<

// --- Helpers que faltaban ---
private fun formatBytes(b: Long): String {
    val kb = 1024.0; val mb = kb * 1024; val gb = mb * 1024
    return when {
        b >= gb -> String.format(java.util.Locale.getDefault(), "%.1f GB", b / gb)
        b >= mb -> String.format(java.util.Locale.getDefault(), "%.1f MB", b / mb)
        b >= kb -> String.format(java.util.Locale.getDefault(), "%.0f KB", b / kb)
        else -> "$b B"
    }
}

private fun formatRelativeDate(ts: Long): String {
    val now = System.currentTimeMillis()
    val d = (now - ts) / (1000 * 60 * 60 * 24)
    return when {
        d <= 0 -> "Today"
        d == 1L -> "Yesterday"
        d < 7  -> "$d days ago"
        else   -> java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
            .format(java.util.Date(ts))
    }
}


enum class SortOption { BY_NAME, BY_DATE, BY_SIZE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            // Load settings or null if missing
            val initialSettings = remember { SettingsManager.loadSettings(context) }
            
            var showOnboarding by remember { mutableStateOf(initialSettings == null) }
            
            // State initialized from settings or defaults
            var darkMode by remember { mutableStateOf(initialSettings?.isDarkMode ?: false) }
            var language by remember { mutableStateOf(if (initialSettings?.language == "ES") Language.ES else Language.EN) }
            var gridScale by remember { mutableFloatStateOf(initialSettings?.gridScale ?: 1.0f) }
            var isDaltonic by remember { mutableStateOf(initialSettings?.isDaltonic ?: false) }
            var tutorialCompleted by remember { mutableStateOf(initialSettings?.tutorialCompleted ?: false) }

            // Helper to save settings
            fun save() {
                SettingsManager.saveSettings(
                    context,
                    AppSettings(
                        language = if (language == Language.ES) "ES" else "EN",
                        isDarkMode = darkMode,
                        isDaltonic = isDaltonic,
                        gridScale = gridScale,
                        tutorialCompleted = tutorialCompleted
                    )
                )
            }

            MyPDFTheme(darkTheme = darkMode) {
                // Pasamos el código de idioma (EN, ES, FR, IT) a ProvideStrings
                ProvideStrings(languageCode = language.name) {
                     Surface(modifier = Modifier.fillMaxSize()) {
                        val deviceType = rememberDeviceType()
                        if (showOnboarding) {
                            // Determine system language for initial onboarding
                            val systemLang = java.util.Locale.getDefault().language
                            val initialLang = if (systemLang == "es") Language.ES else Language.EN
                            
                            OnboardingDialog(
                                initialLanguage = initialLang,
                                onFinish = { lang, dark, daltonic ->
                                    language = lang
                                    darkMode = dark
                                    isDaltonic = daltonic
                                    save()
                                    showOnboarding = false
                                }
                            )
                        } else {
                            AppRootAdaptive(
                                deviceType = deviceType,
                                isDarkMode = darkMode,
                                onToggleDarkMode = { darkMode = !darkMode; save() },
                                isDaltonic = isDaltonic,
                                onToggleDaltonic = { isDaltonic = !isDaltonic; save() },
                                language = language,
                                onLanguageChange = { 
                                    language = it
                                    save() 
                                },
                                gridScale = gridScale,
                                onGridScaleChange = { gridScale = it; save() },
                                tutorialCompleted = tutorialCompleted,
                                onTutorialComplete = { tutorialCompleted = true; save() },
                                onResetTutorial = { tutorialCompleted = false; save() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRootAdaptive(
    deviceType: DeviceType,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    isDaltonic: Boolean,
    onToggleDaltonic: () -> Unit,
    language: Language,
    onLanguageChange: (Language) -> Unit,
    gridScale: Float,
    onGridScaleChange: (Float) -> Unit,
    tutorialCompleted: Boolean,
    onTutorialComplete: () -> Unit,
    onResetTutorial: () -> Unit
) {
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedFile = selectedPath?.let(::File)

    // Hoisted Tutorial State: guardamos solo el paso en savedState para sobrevivir rotaciones,
    // y el targetRect en memoria normal (no es serializable)
    var tutorialStep by rememberSaveable { mutableStateOf(if (!tutorialCompleted) TutorialStep.INTRO_DIALOG else TutorialStep.NONE) }
    var tutorialTargetRect by remember { mutableStateOf<Rect?>(null) }

    // Derivamos el TutorialState que se pasa a pantallas hijas
    val tutorialState = TutorialState(step = tutorialStep, targetRect = tutorialTargetRect)

    LaunchedEffect(tutorialCompleted) {
        if (tutorialCompleted) {
            tutorialStep = TutorialStep.NONE
            tutorialTargetRect = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedFile == null) {
                LibraryScreen(
                    deviceType = deviceType,
                    onOpen = { selectedPath = it.absolutePath },
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    isDaltonic = isDaltonic,
                    onToggleDaltonic = onToggleDaltonic,
                    language = language,
                    onLanguageChange = onLanguageChange,
                    gridScale = gridScale,
                    onGridScaleChange = onGridScaleChange,
                    tutorialCompleted = tutorialCompleted,
                    onTutorialComplete = {
                        onTutorialComplete()
                        tutorialStep = TutorialStep.NONE
                        tutorialTargetRect = null
                    },
                    onResetTutorial = onResetTutorial,
                    tutorialState = tutorialState,
                    onTutorialStateChange = { tutorialStep = it.step; tutorialTargetRect = it.targetRect }
                )
            } else {
                PdfEditScreen(
                    deviceType = deviceType,
                    file = selectedFile,
                    onBack = { selectedPath = null },
                    isDarkMode = isDarkMode,
                    isDaltonic = isDaltonic,
                    onToggleDaltonic = onToggleDaltonic,
                    language = language,
                    tutorialState = tutorialState,
                    onTutorialStateChange = { tutorialStep = it.step; tutorialTargetRect = it.targetRect },
                    onTutorialComplete = {
                        onTutorialComplete()
                        tutorialStep = TutorialStep.NONE
                        tutorialTargetRect = null
                    }
                )
            }
        }
    }
}

private data class LibraryTutorialTargets(
    val newCategory: Rect? = null,
    val fab: Rect? = null,
    val importedCard: Rect? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    deviceType: DeviceType,
    onOpen: (File) -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    isDaltonic: Boolean,
    onToggleDaltonic: () -> Unit,
    language: Language,
    onLanguageChange: (Language) -> Unit,
    gridScale: Float,
    onGridScaleChange: (Float) -> Unit,
    tutorialCompleted: Boolean,
    onTutorialComplete: () -> Unit,
    onResetTutorial: () -> Unit,
    tutorialState: TutorialState,
    onTutorialStateChange: (TutorialState) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = strings()
    val snackbarHostState = remember { SnackbarHostState() }

    // Tutorial targets local state

    var tutorialTargets by remember { mutableStateOf(LibraryTutorialTargets()) }

    fun resolveTarget(step: TutorialStep): Rect? = when (step) {
        TutorialStep.NEW_CATEGORY -> tutorialTargets.newCategory
        TutorialStep.FAB, TutorialStep.IMPORT_PDF_MENU -> tutorialTargets.fab
        TutorialStep.LONG_PRESS_FILE,
        TutorialStep.OPTIONS_MENU,
        TutorialStep.RENAME_DIALOG,
        TutorialStep.OPEN_FILE -> tutorialTargets.importedCard
        else -> null
    }

    fun advanceTutorial(next: TutorialStep) {
        onTutorialStateChange(tutorialState.copy(step = next, targetRect = resolveTarget(next)))
    }

    LaunchedEffect(tutorialTargets, tutorialState.step) {
        val resolved = resolveTarget(tutorialState.step)
        if (resolved != tutorialState.targetRect) {
            onTutorialStateChange(tutorialState.copy(targetRect = resolved))
        }
    }

    LaunchedEffect(tutorialCompleted) {
        if (tutorialCompleted) {
            onTutorialStateChange(tutorialState.copy(step = TutorialStep.NONE, targetRect = null))
        }
    }

    val tutorialsEnabled = tutorialState.step != TutorialStep.NONE

    // State variables
    var categories by remember { mutableStateOf(listOf<File>()) }
    var selectedCategory by rememberSaveable { mutableStateOf<File?>(null) }
    var folders by remember { mutableStateOf(listOf<File>()) }
    var pdfs by remember { mutableStateOf(listOf<File>()) }
    val thumbs = remember { mutableStateMapOf<File, Bitmap?>() }
    var loading by remember { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    var globalResults by remember { mutableStateOf(listOf<File>()) }
    var searching by remember { mutableStateOf(false) }
    var sortOption by rememberSaveable { mutableStateOf(SortOption.BY_DATE) }
    var sortAsc by rememberSaveable { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    // var gridScale by rememberSaveable { mutableStateOf(1.0f) } // Now passed from parent

    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var fabMenuExpanded by remember { mutableStateOf(false) }

    // Long-press actions
    var fileToEdit by remember { mutableStateOf<File?>(null) }
    var isDirTarget by remember { mutableStateOf(false) }
    var showFileOptionsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    fun notifyTutorialHint(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Ensure targetRect is updated when step changes (if rect is already captured)
    LaunchedEffect(tutorialState.step) {
        val target = when (tutorialState.step) {
            TutorialStep.NEW_CATEGORY -> tutorialTargets.newCategory
            TutorialStep.FAB -> tutorialTargets.fab
            TutorialStep.LONG_PRESS_FILE -> tutorialTargets.importedCard
            TutorialStep.OPTIONS_MENU -> tutorialTargets.importedCard
            TutorialStep.RENAME_DIALOG -> tutorialTargets.importedCard
            TutorialStep.OPEN_FILE -> tutorialTargets.importedCard
            else -> null
        }
        if (target != null) {
            onTutorialStateChange(tutorialState.copy(targetRect = target))
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTablet = screenWidthDp >= 900
    val isLargePhone = screenWidthDp in 600..899

    val sidebarWidth = when {
        isTablet -> 300.dp
        isLargePhone -> 240.dp
        else -> 0.dp // Hidden on small screens (could use a drawer, but keeping logic simple for now as per request)
    }
    // For this redesign, we'll assume if sidebarWidth is 0, we might need a different layout or just hide it.
    // However, the original code showed it always (96.dp on small). Let's keep a minimum width or use a drawer.
    // To make it "Professional", on mobile it should probably be a bottom nav or a drawer.
    // But to avoid "logic changes" (navigation structure), I will keep the sidebar but make it look better.
    // If it's very small, I'll use a compact rail style.
    val effectiveSidebarWidth = if (sidebarWidth == 0.dp) 80.dp else sidebarWidth
    val isCompactSidebar = effectiveSidebarWidth < 200.dp

    val baseMin = when (deviceType) {
        DeviceType.TABLET_LARGE -> 220
        DeviceType.TABLET -> 180
        DeviceType.PHONE -> 140
    }
    val adjustedMin = (baseMin * gridScale).toInt().coerceAtLeast(100)
    val columns = (screenWidthDp / adjustedMin).coerceAtLeast(1)

    suspend fun ensureDefaultCategory(): File {
        val (rootDirs, _) = listLibraryFolder(context, "")
        val default = rootDirs.firstOrNull { it.name == "default" }
        if (default != null) return default
        return createLibraryFolder(context, "", "default") ?: appPdfDir(context)
    }

    suspend fun refreshCategories() {
        loading = true
        val (rootDirs, _) = withContext(Dispatchers.IO) { listLibraryFolder(context, "") }
        val cats = rootDirs.sortedBy { it.name.lowercase() }
        categories = cats
        val effectiveSelected = if (selectedCategory?.exists() == true && cats.contains(selectedCategory)) {
            selectedCategory
        } else {
            cats.firstOrNull() ?: ensureDefaultCategory().also { created ->
                categories = (cats + created).sortedBy { it.name.lowercase() }
            }
        }
        selectedCategory = effectiveSelected
        loading = false
    }

    suspend fun loadCategory(cat: File?) {
        loading = true
        if (cat == null) {
            folders = emptyList(); pdfs = emptyList(); loading = false; return
        }
        val base = appPdfDir(context)
        val relPath = runCatching { cat.relativeTo(base).path.replace(File.separatorChar, '/') }
            .getOrElse { cat.name }
        val (fList, pList) = withContext(Dispatchers.IO) { listLibraryFolder(context, relPath) }
        folders = fList.sortedBy { it.name.lowercase() }
        pdfs = pList
        withContext(Dispatchers.IO) {
            pList.forEach { f -> if (!thumbs.containsKey(f)) thumbs[f] = generatePdfThumbnailCached(context, f) }
        }
        loading = false
    }

    suspend fun searchEverywhere(text: String) {
        if (text.isBlank()) { globalResults = emptyList(); return }
        searching = true
        val results = withContext(Dispatchers.IO) {
            fun walk(dir: File, acc: MutableList<File>) {
                dir.listFiles()?.forEach { f ->
                    if (f.isDirectory) walk(f, acc) else if (f.extension.equals("pdf", true)) acc += f
                }
            }
            val (rootDirs, rootPdfs) = listLibraryFolder(context, "")
            val all = mutableListOf<File>()
            all += rootPdfs
            rootDirs.forEach { walk(it, all) }
            all.filter { it.name.contains(text, ignoreCase = true) }
        }
        withContext(Dispatchers.IO) {
            results.forEach { f -> if (!thumbs.containsKey(f)) thumbs[f] = generatePdfThumbnailCached(context, f) }
        }
        globalResults = results
        searching = false
    }

    fun applySort(list: List<File>): List<File> {
        val comp = when (sortOption) {
            SortOption.BY_NAME -> compareBy<File> { it.name.lowercase() }
            SortOption.BY_DATE -> compareBy<File> { it.lastModified() }
            SortOption.BY_SIZE -> compareBy<File> { it.length() }
        }
        val base = list.sortedWith(comp)
        return if (sortAsc) base else base.asReversed()
    }

    LaunchedEffect(Unit) { refreshCategories() }
    LaunchedEffect(selectedCategory) { loadCategory(selectedCategory) }
    LaunchedEffect(query) { searchEverywhere(query) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri ?: return@rememberLauncherForActivityResult
            scope.launch {
                loading = true
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val base = appPdfDir(context)
                val relPath = selectedCategory?.let { sel ->
                    runCatching { sel.relativeTo(base).path.replace(File.separatorChar, '/') }.getOrElse { sel.name }
                } ?: "default"
                val effectiveRel = if (relPath.isBlank()) "default" else relPath
                val newFile = withContext(Dispatchers.IO) { clonePdfIntoApp(context, uri, effectiveRel) }
                withContext(Dispatchers.IO) {
                    thumbs[newFile] = generatePdfThumbnailCached(context, newFile)
                }
                refreshCategories()
                selectedCategory = ensureDefaultCategory().let { def ->
                    if (effectiveRel == "default") def else File(def.parentFile, effectiveRel)
                }
                loadCategory(selectedCategory)
                Toast.makeText(context, "PDF importado ✓", Toast.LENGTH_SHORT).show()
                loading = false
            }
        }
    )

    val showFiles = if (query.isNotBlank()) applySort(globalResults) else applySort(pdfs)
    val showFolders = if (query.isNotBlank()) emptyList<File>() else folders

    if (deviceType == DeviceType.PHONE) {
        LibraryScreenPhone(
            gridScale = gridScale,
            categories = categories,
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            onEditFile = { f, isDir ->
                if (tutorialState.step == TutorialStep.LONG_PRESS_FILE) {
                    advanceTutorial(TutorialStep.OPTIONS_MENU)
                }
                fileToEdit = f
                isDirTarget = isDir
                renameText = if (isDir) f.name else f.nameWithoutExtension
                showFileOptionsDialog = true
            },
            s = s,
            tutorialState = tutorialState,
            advanceTutorial = { advanceTutorial(it) },
            onShowNewCategoryDialog = { showNewCategoryDialog = true },
            onUpdateTutorialTarget = { tutorialTargets = it },
            tutorialTargets = tutorialTargets,
            onShowSettingsDialog = { showSettingsDialog = true },
            fabMenuExpanded = fabMenuExpanded,
            onFabMenuExpanded = { fabMenuExpanded = it },
            onImportPdf = { picker.launch(arrayOf("application/pdf")) },
            onShowNewFolderDialog = { showNewFolderDialog = true },
            query = query,
            onQueryChange = { query = it },
            sortOption = sortOption,
            onSortOptionChange = { sortOption = it },
            sortAsc = sortAsc,
            onSortAscChange = { sortAsc = it },
            displayFiles = showFiles,
            displayFolders = showFolders,
            loading = loading,
            searching = searching,
            thumbs = thumbs,
            columns = columns,
            onOpen = onOpen,
            onFolderOpen = { folder -> selectedCategory = folder },
            notifyTutorialHint = { notifyTutorialHint(it) },
            tutorialsEnabled = tutorialsEnabled
        )
    } else {
        LibraryScreenTablet(
            gridScale = gridScale,
            effectiveSidebarWidth = effectiveSidebarWidth,
            isCompactSidebar = isCompactSidebar,
            categories = categories,
            selectedCategory = selectedCategory,
            onSelectCategory = { selectedCategory = it },
            onEditFile = { f, isDir ->
                if (tutorialState.step == TutorialStep.LONG_PRESS_FILE) {
                    advanceTutorial(TutorialStep.OPTIONS_MENU)
                }
                fileToEdit = f
                isDirTarget = isDir
                renameText = if (isDir) f.name else f.nameWithoutExtension
                showFileOptionsDialog = true
            },
            s = s,
            tutorialState = tutorialState,
            advanceTutorial = { advanceTutorial(it) },
            onShowNewCategoryDialog = { showNewCategoryDialog = true },
            onUpdateTutorialTarget = { tutorialTargets = it },
            tutorialTargets = tutorialTargets,
            onShowSettingsDialog = { showSettingsDialog = true },
            fabMenuExpanded = fabMenuExpanded,
            onFabMenuExpanded = { fabMenuExpanded = it },
            onImportPdf = { picker.launch(arrayOf("application/pdf")) },
            onShowNewFolderDialog = { showNewFolderDialog = true },
            query = query,
            onQueryChange = { query = it },
            sortOption = sortOption,
            onSortOptionChange = { sortOption = it },
            sortAsc = sortAsc,
            onSortAscChange = { sortAsc = it },
            globalResults = showFiles,
            pdfs = showFiles,
            folders = showFolders,
            loading = loading,
            searching = searching,
            thumbs = thumbs,
            columns = columns,
            onOpen = onOpen,
            onFolderOpen = { folder -> selectedCategory = folder },
            notifyTutorialHint = { notifyTutorialHint(it) },
            tutorialsEnabled = tutorialsEnabled
        )
    }

    // Dialogs (kept mostly same logic, just updated UI slightly if needed)
    if (showNewCategoryDialog) {
        SimpleInputDialog(
            title = s.newCategory,
            label = s.categories,
            value = newCategoryName,
            onValueChange = { newCategoryName = it },
            onDismiss = { showNewCategoryDialog = false; newCategoryName = "" },
            onConfirm = {
                if (newCategoryName.isNotBlank()) {
                    scope.launch {
                        val created = createLibraryFolder(context, "", newCategoryName)
                        if (created != null) {
                            refreshCategories()
                            selectedCategory = created
                        } else {
                            Toast.makeText(context, s.cannotCreateCategory, Toast.LENGTH_SHORT).show()
                        }
                        showNewCategoryDialog = false
                        newCategoryName = ""
                    }
                }
            },
            confirmText = s.create,
            cancelText = s.cancel
        )
    }
    
    if (showNewFolderDialog) {
        SimpleInputDialog(
            title = s.createFolder,
            label = s.createFolder,
            value = newFolderName,
            onValueChange = { newFolderName = it },
            onDismiss = { showNewFolderDialog = false; newFolderName = "" },
            onConfirm = {
                if (newFolderName.isNotBlank()) {
                    scope.launch {
                        val rel = selectedCategory?.let { cat ->
                            runCatching { cat.relativeTo(appPdfDir(context)).path.replace(File.separatorChar, '/') }.getOrElse { cat.name }
                        } ?: ""
                        val created = createLibraryFolder(context, rel, newFolderName)
                        if (created != null) {
                            loadCategory(selectedCategory)
                        } else {
                            Toast.makeText(context, s.cannotCreateCategory, Toast.LENGTH_SHORT).show()
                        }
                        showNewFolderDialog = false
                        newFolderName = ""
                    }
                }
            },
            confirmText = s.create,
            cancelText = s.cancel
        )
    }

    if (showFileOptionsDialog && fileToEdit != null) {
        AlertDialog(
            onDismissRequest = { 
                if (tutorialState.step == TutorialStep.NONE) showFileOptionsDialog = false 
            },
            title = { Text(if (isDirTarget) fileToEdit?.name ?: "" else fileToEdit?.nameWithoutExtension ?: "") },
            text = {
                Column {
                    // Renombrar (opción resaltada por el tutorial)
                    val isRenameTarget = tutorialState.step == TutorialStep.OPTIONS_MENU
                    TextButton(
                        onClick = {
                            showFileOptionsDialog = false
                            showRenameDialog = true
                            if (tutorialState.step == TutorialStep.OPTIONS_MENU) advanceTutorial(TutorialStep.RENAME_DIALOG)
                        },
                        modifier = Modifier.fillMaxWidth().then(
                            if (isRenameTarget) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier
                        )
                    ) {
                        Text(
                            s.rename,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
                        )
                    }

                    // Eliminar (siempre visible, pero no cambia el paso del tutorial)
                    val deleteEnabled = tutorialState.step != TutorialStep.OPTIONS_MENU
                    TextButton(
                        onClick = {
                            showFileOptionsDialog = false
                            showDeleteDialog = true
                        },
                        enabled = deleteEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            s.delete,
                            color = if (deleteEnabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { 
                    if (tutorialState.step == TutorialStep.NONE) showFileOptionsDialog = false 
                }) { Text(s.cancel) }
            }
        )
    }

    if (showRenameDialog && fileToEdit != null) {
        SimpleInputDialog(
            title = s.rename,
            label = s.rename,
            value = renameText,
            onValueChange = { renameText = it },
            onDismiss = {
                if (tutorialState.step == TutorialStep.NONE) showRenameDialog = false
            },
            onConfirm = {
                if (renameText.isNotBlank()) {
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            val target = fileToEdit!!
                            val desiredName = if (target.isDirectory) renameText else "$renameText.pdf"
                            if (desiredName == target.name) {
                                false
                            } else {
                                val newFile = File(target.parentFile, desiredName)
                                !newFile.exists() && target.renameTo(newFile)
                            }
                        }
                        if (success) {
                            if (isDirTarget) refreshCategories() else loadCategory(selectedCategory)
                            if (tutorialState.step == TutorialStep.RENAME_DIALOG) advanceTutorial(TutorialStep.OPEN_FILE)
                            showRenameDialog = false
                        } else {
                            Toast.makeText(context, s.cannotRename, Toast.LENGTH_SHORT).show()
                            if (tutorialState.step == TutorialStep.RENAME_DIALOG) {
                                notifyTutorialHint(s.tutorialRenameBody)
                            }
                        }
                    }
                } else {
                    notifyTutorialHint(s.tutorialRenameBody)
                }
            },
            confirmText = s.save,
            cancelText = s.cancel
        )
    }

    if (showDeleteDialog && fileToEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(s.delete) },
            text = { Text(s.confirmDeleteMessage(isDirTarget)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val success = withContext(Dispatchers.IO) {
                                fileToEdit?.deleteRecursively() == true
                            }
                            if (success) {
                                refreshCategories()
                                loadCategory(selectedCategory)
                            } else {
                                Toast.makeText(context, s.deleteError, Toast.LENGTH_SHORT).show()
                            }
                            showDeleteDialog = false
                        }
                    }
                ) { Text(s.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(s.cancel) }
            }
        )
    }

    // Settings Dialog (restored)
    if (showSettingsDialog) {
        // Calcula las columnas actuales según el tamaño de pantalla y el gridScale/ baseMin
        val currentColumns = columns.coerceAtLeast(1)

        SettingsDialog(
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            isDaltonic = isDaltonic,
            onToggleDaltonic = onToggleDaltonic,
            language = language,
            onLanguageChange = onLanguageChange,
            gridColumns = currentColumns,
            onColumnsChange = { newCols ->
                // Convierte el número de columnas deseado a un gridScale aproximado
                val cols = newCols.coerceAtLeast(1)
                val newScale = try {
                    ((screenWidthDp.toFloat() / cols.toFloat()) / baseMin.toFloat())
                } catch (e: Exception) { gridScale }
                // Limitar para mantener coherencia visual
                val bounded = newScale.coerceIn(0.5f, 1.5f)
                onGridScaleChange(bounded)
            },
            onResetTutorial = {
                onResetTutorial()
                onTutorialStateChange(TutorialState(step = TutorialStep.INTRO_DIALOG))
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Tutorial Overlay
    TutorialOverlay(
        state = tutorialState,
        onNext = {
            if (tutorialState.step == TutorialStep.INTRO_DIALOG) {
                advanceTutorial(TutorialStep.NEW_CATEGORY)
            }
        },
        onDismiss = onTutorialComplete,
        isTablet = deviceType != DeviceType.PHONE
    )

    SnackbarHost(hostState = snackbarHostState)
}

// ====== UI Components ======

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryItem(name: String, selected: Boolean, compact: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = MaterialTheme.shapes.medium, // Pill shape-ish
        color = containerColor,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start
        ) {
            Icon(
                imageVector = if (selected) androidx.compose.material.icons.Icons.Filled.Folder else androidx.compose.material.icons.Icons.Outlined.Folder,
                contentDescription = null,
                tint = contentColor
            )
            if (!compact) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    maxLines = 1,
                    fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderCard(name: String, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp) // Fixed height for folders
    ) {
        Box(Modifier.fillMaxSize().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
            Row(
                Modifier.padding(16.dp).align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PdfCard(
    name: String,
    sizeText: String,
    dateText: String,
    thumbnail: Bitmap?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            // Thumbnail Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f) // Standard aspect ratio
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Content Area
            Column(Modifier.padding(12.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    minLines = 2, // Keep consistent height
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SimpleInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String,
    cancelText: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelText) } }
    )
}

// =======================
// PERF helper (API 23 safe)
// =======================
private object Perf {
    private const val TAG = "PDFPERF"

    private val count = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()
    private val totalNs = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong>()

    private fun incCount(label: String) {
        synchronized(count) {
            val curr = count[label]
            if (curr == null) {
                val created = java.util.concurrent.atomic.AtomicLong(0)
                val prev = count.putIfAbsent(label, created)
                (prev ?: created).incrementAndGet()
            } else {
                curr.incrementAndGet()
            }
        }
    }

    private fun addTotal(label: String, deltaNs: Long) {
        synchronized(totalNs) {
            val curr = totalNs[label]
            if (curr == null) {
                val created = java.util.concurrent.atomic.AtomicLong(0)
                val prev = totalNs.putIfAbsent(label, created)
                (prev ?: created).addAndGet(deltaNs)
            } else {
                curr.addAndGet(deltaNs)
            }
        }
    }

    fun begin(label: String) {
        android.os.Trace.beginSection(label)
    }

    fun end(label: String, elapsedNs: Long? = null) {
        android.os.Trace.endSection()
        if (elapsedNs != null) {
            incCount(label)
            addTotal(label, elapsedNs)
            android.util.Log.d(TAG, "$label took ${elapsedNs / 1_000_000} ms")
        }
    }

    inline fun <T> time(label: String, block: () -> T): T {
        begin(label)
        val start = System.nanoTime()
        try {
            val result = block()
            val ns = System.nanoTime() - start
            end(label, ns)
            return result
        } catch (e: Throwable) {
            val ns = System.nanoTime() - start
            end(label, ns)
            throw e
        }
    }

    suspend inline fun <T> timeIO(label: String, crossinline block: suspend () -> T): T {
        begin(label)
        val start = System.nanoTime()
        try {
            val res = withContext(kotlinx.coroutines.Dispatchers.IO) { block() }
            val ns = System.nanoTime() - start
            end(label, ns)
            return res
        } catch (e: Throwable) {
            val ns = System.nanoTime() - start
            end(label, ns)
            throw e
        }
    }

    fun dumpSummary() {
        android.util.Log.d(TAG, "===== PERF SUMMARY =====")
        for ((k, total) in totalNs) {
            val c = count[k]?.get() ?: 0L
            val avgMs = if (c > 0) (total.get() / c) / 1_000_000.0 else 0.0
            android.util.Log.d(
                TAG,
                String.format(
                    "%s -> count=%d, total=%.1f ms, avg=%.1f ms",
                    k, c, total.get() / 1_000_000.0, avgMs
                )
            )
        }
        android.util.Log.d(TAG, "========================")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScreenPhone(
    gridScale: Float,
    categories: List<File>,
    selectedCategory: File?,
    onSelectCategory: (File) -> Unit,
    onEditFile: (File, Boolean) -> Unit,
    s: AppStrings,
    tutorialState: TutorialState,
    advanceTutorial: (TutorialStep) -> Unit,
    onShowNewCategoryDialog: () -> Unit,
    onUpdateTutorialTarget: (LibraryTutorialTargets) -> Unit,
    tutorialTargets: LibraryTutorialTargets,
    onShowSettingsDialog: () -> Unit,
    fabMenuExpanded: Boolean,
    onFabMenuExpanded: (Boolean) -> Unit,
    onImportPdf: () -> Unit,
    onShowNewFolderDialog: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    sortAsc: Boolean,
    onSortAscChange: (Boolean) -> Unit,
    displayFiles: List<File>,
    displayFolders: List<File>,
    loading: Boolean,
    searching: Boolean,
    thumbs: Map<File, Bitmap?>,
    columns: Int,
    onOpen: (File) -> Unit,
    onFolderOpen: (File) -> Unit,
    notifyTutorialHint: (String) -> Unit,
    tutorialsEnabled: Boolean
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("PentagramApp", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
                HorizontalDivider()
                
                LazyColumn {
                    items(count = categories.size) { index ->
                        val cat = categories[index]
                        NavigationDrawerItem(
                            label = { Text(cat.name) },
                            selected = cat == selectedCategory,
                            onClick = {
                                onSelectCategory(cat)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    item {
                        NavigationDrawerItem(
                            label = { Text(s.newCategory) },
                            selected = false,
                            icon = { Icon(Icons.Default.Add, null) },
                            onClick = {
                                onShowNewCategoryDialog()
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(
                            label = { Text(s.settingsTitle) },
                            selected = false,
                            icon = { Icon(Icons.Default.Settings, null) },
                            onClick = {
                                onShowSettingsDialog()
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(selectedCategory?.name ?: "PDF") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // Sort button
                        Box {
                            var sortMenuExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text(s.sortByName) }, onClick = { onSortOptionChange(SortOption.BY_NAME); sortMenuExpanded = false })
                                DropdownMenuItem(text = { Text(s.sortByDate) }, onClick = { onSortOptionChange(SortOption.BY_DATE); sortMenuExpanded = false })
                                DropdownMenuItem(text = { Text(s.sortBySize) }, onClick = { onSortOptionChange(SortOption.BY_SIZE); sortMenuExpanded = false })
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (tutorialState.step == TutorialStep.FAB) {
                            advanceTutorial(TutorialStep.IMPORT_PDF_MENU)
                        }
                        onFabMenuExpanded(true)
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    DropdownMenu(
                        expanded = fabMenuExpanded,
                        onDismissRequest = { onFabMenuExpanded(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text(s.importPdf) },
                            leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                            onClick = {
                                onFabMenuExpanded(false)
                                onImportPdf()
                                if (tutorialState.step == TutorialStep.IMPORT_PDF_MENU) {
                                    advanceTutorial(TutorialStep.LONG_PRESS_FILE)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(s.createFolder) },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            onClick = {
                                onFabMenuExpanded(false)
                                onShowNewFolderDialog()
                            }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                // Search bar
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text(s.searchInLibrary) },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                
                // Content
                Box(Modifier.weight(1f)) {
                    if (loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(count = displayFolders.size) { index ->
                                val folder = displayFolders[index]
                                FolderItem(
                                    file = folder,
                                    onClick = { onFolderOpen(folder) },
                                    onLongClick = { onEditFile(folder, true) },
                                    s = s,
                                    sizeScale = gridScale
                                )
                            }
                            items(count = displayFiles.size) { index ->
                                val file = displayFiles[index]
                                val highlightImported = tutorialsEnabled &&
                                    tutorialState.step in setOf(
                                        TutorialStep.LONG_PRESS_FILE,
                                        TutorialStep.OPTIONS_MENU,
                                        TutorialStep.RENAME_DIALOG,
                                        TutorialStep.OPEN_FILE
                                    ) &&
                                    index == 0
                                PdfFileItem(
                                    file = file,
                                    thumb = thumbs[file],
                                    onClick = {
                                        // Block tap during LONG_PRESS_FILE step - user must long-press
                                        if (tutorialState.step == TutorialStep.LONG_PRESS_FILE) return@PdfFileItem
                                        if (tutorialState.step == TutorialStep.OPEN_FILE) {
                                            advanceTutorial(TutorialStep.TOOLBOX)
                                        }
                                        onOpen(file)
                                    },
                                    onLongClick = { onEditFile(file, false) },
                                    s = s,
                                    sizeScale = gridScale,
                                    onPositioned = if (highlightImported) {
                                        { rect -> onUpdateTutorialTarget(tutorialTargets.copy(importedCard = rect)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryScreenTablet(
    gridScale: Float,
    effectiveSidebarWidth: androidx.compose.ui.unit.Dp,
    isCompactSidebar: Boolean,
    categories: List<File>,
    selectedCategory: File?,
    onSelectCategory: (File) -> Unit,
    onEditFile: (File, Boolean) -> Unit,
    s: AppStrings,
    tutorialState: TutorialState,
    advanceTutorial: (TutorialStep) -> Unit,
    onShowNewCategoryDialog: () -> Unit,
    onUpdateTutorialTarget: (LibraryTutorialTargets) -> Unit,
    tutorialTargets: LibraryTutorialTargets,
    onShowSettingsDialog: () -> Unit,
    fabMenuExpanded: Boolean,
    onFabMenuExpanded: (Boolean) -> Unit,
    onImportPdf: () -> Unit,
    onShowNewFolderDialog: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
    sortAsc: Boolean,
    onSortAscChange: (Boolean) -> Unit,
    globalResults: List<File>,
    pdfs: List<File>,
    folders: List<File>,
    loading: Boolean,
    searching: Boolean,
    thumbs: Map<File, Bitmap?>,
    columns: Int,
    onOpen: (File) -> Unit,
    onFolderOpen: (File) -> Unit,
    notifyTutorialHint: (String) -> Unit,
    tutorialsEnabled: Boolean
) {
    Row(Modifier.fillMaxSize()) {
        // --- SIDEBAR ---
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.width(effectiveSidebarWidth).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // App Title / Header
                if (!isCompactSidebar) {
                    Text(
                        text = "PentagramApp",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp).align(Alignment.Start)
                    )
                } else {
                    Spacer(Modifier.height(16.dp))
                    Text("PDF", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                }

                // Categories Header
                if (!isCompactSidebar) {
                    Text(
                        text = s.categories.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp)
                    )
                }

                // Categories List
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(count = categories.size) { index ->
                        val cat = categories[index]
                        val selected = cat == selectedCategory
                        CategoryItem(
                            name = cat.name,
                            selected = selected,
                            compact = isCompactSidebar,
                            onClick = { onSelectCategory(cat) },
                            onLongClick = { onEditFile(cat, true) }
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        if (isCompactSidebar) {
                            IconButton(
                                onClick = {
                                    if (tutorialState.step == TutorialStep.NEW_CATEGORY) {
                                        advanceTutorial(TutorialStep.FAB)
                                    }
                                    onShowNewCategoryDialog()
                                },
                                modifier = Modifier.onGloballyPositioned {
                                    onUpdateTutorialTarget(tutorialTargets.copy(newCategory = it.boundsInRoot()))
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = s.newCategory)
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    if (tutorialState.step == TutorialStep.NEW_CATEGORY) {
                                        advanceTutorial(TutorialStep.FAB)
                                    }
                                    onShowNewCategoryDialog()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp)
                                    .onGloballyPositioned {
                                        onUpdateTutorialTarget(tutorialTargets.copy(newCategory = it.boundsInRoot()))
                                    },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(s.newCategory)
                            }
                        }
                    }
                }

                // Bottom Settings Area
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                if (isCompactSidebar) {
                    IconButton(onClick = onShowSettingsDialog) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = s.themes,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                } else {
                    TextButton(
                        onClick = onShowSettingsDialog,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.settingsTitle, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // --- MAIN CONTENT ---
        Scaffold(
            modifier = Modifier.weight(1f),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        if (tutorialState.step == TutorialStep.FAB) {
                            advanceTutorial(TutorialStep.IMPORT_PDF_MENU)
                        }
                        onFabMenuExpanded(true)
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.onGloballyPositioned {
                        onUpdateTutorialTarget(tutorialTargets.copy(fab = it.boundsInRoot()))
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)

                    DropdownMenu(
                        expanded = fabMenuExpanded,
                        onDismissRequest = {
                            if (tutorialState.step != TutorialStep.IMPORT_PDF_MENU) {
                                onFabMenuExpanded(false)
                            }
                        }
                    ) {
                        DropdownMenuItem(
                            text = { Text(s.importPdf) },
                            leadingIcon = { Icon(Icons.Default.UploadFile, null) },
                            modifier = if (tutorialState.step == TutorialStep.IMPORT_PDF_MENU) {
                                Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                            } else Modifier,
                            onClick = {
                                onFabMenuExpanded(false)
                                onImportPdf()
                                if (tutorialState.step == TutorialStep.IMPORT_PDF_MENU) {
                                    advanceTutorial(TutorialStep.LONG_PRESS_FILE)
                                }
                            }
                        )
                        
                        val createFolderEnabled = tutorialState.step != TutorialStep.IMPORT_PDF_MENU
                        DropdownMenuItem(
                            text = { Text(s.createFolder) },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                            enabled = createFolderEnabled,
                            onClick = {
                                onFabMenuExpanded(false)
                                onShowNewFolderDialog()
                            }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                // Top Bar / Search
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.extraLarge,
                            placeholder = { Text(s.searchInLibrary) },
                            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        // Sort Button
                        Box {
                            var sortMenuExpanded by remember { mutableStateOf(false) }
                            FilledTonalButton(onClick = { sortMenuExpanded = true }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Sort, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when (sortOption) {
                                        SortOption.BY_NAME -> s.sortByName
                                        SortOption.BY_DATE -> s.sortByDate
                                        SortOption.BY_SIZE -> s.sortBySize
                                    }
                                )
                            }
                            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text(s.sortByName) }, onClick = { onSortOptionChange(SortOption.BY_NAME); sortMenuExpanded = false })
                                DropdownMenuItem(text = { Text(s.sortByDate) }, onClick = { onSortOptionChange(SortOption.BY_DATE); sortMenuExpanded = false })
                                DropdownMenuItem(text = { Text(s.sortBySize) }, onClick = { onSortOptionChange(SortOption.BY_SIZE); sortMenuExpanded = false })
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (sortAsc) "Ascending" else "Descending") },
                                    trailingIcon = { Icon(if (sortAsc) androidx.compose.material.icons.Icons.Default.ArrowUpward else androidx.compose.material.icons.Icons.Default.ArrowDownward, null) },
                                    onClick = { onSortAscChange(!sortAsc); sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                }

                val context = LocalContext.current
                val showFiles = if (query.isNotBlank()) globalResults else pdfs
                val showFolders = if (query.isNotBlank()) emptyList<File>() else folders // Sorting is done in parent? No, parent applies sort.
                // Wait, parent applies sort. I need to pass sorted lists or apply sort here.
                // In original code: val showFiles = if (query.isNotBlank()) applySort(globalResults) else applySort(pdfs)
                // I should pass the sorted lists or the raw lists and sort them here.
                
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        showFiles.isEmpty() && showFolders.isEmpty() -> {
                            // Empty state
                            EmptyLibraryView(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                onScanDocuments = {
                                    // Trigger document scan (implementation not shown here)
                                    Toast.makeText(context, "Scanning documents...", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columns),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                            items(count = showFolders.size) { index ->
                                val folder = showFolders[index]
                                FolderItem(
                                    file = folder,
                                    onClick = { onFolderOpen(folder) },
                                    onLongClick = { onEditFile(folder, true) },
                                    s = s,
                                    sizeScale = gridScale
                                )
                            }
                            items(count = showFiles.size) { index ->
                                val file = showFiles[index]
                                val highlightImported = tutorialsEnabled &&
                                    tutorialState.step in setOf(
                                        TutorialStep.LONG_PRESS_FILE,
                                        TutorialStep.OPTIONS_MENU,
                                        TutorialStep.RENAME_DIALOG,
                                        TutorialStep.OPEN_FILE
                                    ) &&
                                    index == 0
                                PdfFileItem(
                                    file = file,
                                    thumb = thumbs[file],
                                    onClick = {
                                        // Block tap during LONG_PRESS_FILE step - user must long-press
                                        if (tutorialState.step == TutorialStep.LONG_PRESS_FILE) return@PdfFileItem
                                        if (tutorialState.step == TutorialStep.OPEN_FILE) {
                                            advanceTutorial(TutorialStep.TOOLBOX)
                                        }
                                        onOpen(file)
                                    },
                                    onLongClick = { onEditFile(file, false) },
                                    s = s,
                                    sizeScale = gridScale,
                                    onPositioned = if (highlightImported) {
                                        { rect -> onUpdateTutorialTarget(tutorialTargets.copy(importedCard = rect)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItem(
    file: File,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    s: AppStrings,
    sizeScale: Float = 1f
) {
    val scale = sizeScale.coerceIn(0.5f, 1.5f)
    val iconSize = 64.dp * scale
    val spacing = 4.dp * scale
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(spacing))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfFileItem(
    file: File,
    thumb: Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    s: AppStrings,
    sizeScale: Float = 1f,
    onPositioned: ((Rect) -> Unit)? = null
) {
    val scale = sizeScale.coerceIn(0.5f, 1.5f)
    val cardWidth = 100.dp * scale
    val cardHeight = 140.dp * scale
    val iconSize = 32.dp * scale
    val spacing = 4.dp * scale
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp)
            .then(
                if (onPositioned != null) {
                    Modifier.onGloballyPositioned { coords -> onPositioned(coords.boundsInRoot()) }
                } else {
                    Modifier
                }
            )
    ) {
        Card(
            elevation = CardDefaults.cardElevation(4.dp),
            modifier = Modifier.size(cardWidth, cardHeight)
        ) {
            if (thumb != null) {
                Image(
                    bitmap = thumb.asImageBitmap(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        null,
                        tint = androidx.compose.ui.graphics.Color.Gray,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
        Spacer(Modifier.height(spacing))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun EmptyLibraryView(
    modifier: Modifier = Modifier,
    onScanDocuments: () -> Unit = {},
    s: AppStrings? = null
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryBooks, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(16.dp))
            if (s != null) {
                Text(s.emptyList, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
