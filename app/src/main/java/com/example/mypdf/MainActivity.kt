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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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


private enum class SortOption { BY_NAME, BY_DATE, BY_SIZE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkMode by rememberSaveable { mutableStateOf(false) }
            var isEnglish by rememberSaveable { mutableStateOf(false) }

            val language = isEnglish.toLanguage()

            MyPDFTheme(darkTheme = darkMode) {
                ProvideStrings(language = language) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppRoot(
                            isDarkMode = darkMode,
                            onToggleDarkMode = { darkMode = !darkMode },
                            language = language,
                            onToggleLanguage = { isEnglish = !isEnglish }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRoot(
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    language: Language,
    onToggleLanguage: () -> Unit
) {
    var selectedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedFile = selectedPath?.let(::File)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedFile == null) {
                LibraryScreen(
                    onOpen = { selectedPath = it.absolutePath },
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    language = language,
                    onToggleLanguage = onToggleLanguage
                )
            } else {
                PdfEditScreen(
                    file = selectedFile,
                    onBack = { selectedPath = null },
                    isDarkMode = isDarkMode,
                    language = language
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onOpen: (File) -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    language: Language,
    onToggleLanguage: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = strings()

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
    var gridScale by rememberSaveable { mutableStateOf(1.0f) }

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

    val gridMinCell = when {
        isTablet -> 220.dp
        isLargePhone -> 180.dp
        else -> 160.dp
    } * gridScale

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
                    items(categories) { cat ->
                        val selected = cat == selectedCategory
                        CategoryItem(
                            name = cat.name,
                            selected = selected,
                            compact = isCompactSidebar,
                            onClick = { selectedCategory = cat }
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        if (isCompactSidebar) {
                            IconButton(onClick = { showNewCategoryDialog = true }) {
                                Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = s.newCategory)
                            }
                        } else {
                            TextButton(
                                onClick = { showNewCategoryDialog = true },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(s.newCategory)
                            }
                        }
                    }
                }

                // Bottom Settings Area
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)

                if (isCompactSidebar) {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = s.themes // Reuse or add new string
                        )
                    }
                } else {
                    TextButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Settings", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // --- MAIN CONTENT ---
        Scaffold(
            modifier = Modifier.weight(1f),
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { fabMenuExpanded = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Add, contentDescription = null)
                    DropdownMenu(
                        expanded = fabMenuExpanded,
                        onDismissRequest = { fabMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(s.importPdf) },
                            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.UploadFile, null) },
                            onClick = {
                                fabMenuExpanded = false
                                picker.launch(arrayOf("application/pdf"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(s.createFolder) },
                            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.CreateNewFolder, null) },
                            onClick = {
                                fabMenuExpanded = false
                                showNewFolderDialog = true
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
                            onValueChange = { query = it },
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
                                DropdownMenuItem(text = { Text(s.sortByName) }, onClick = { sortOption = SortOption.BY_NAME; sortMenuExpanded = false })
                                DropdownMenuItem(text = { Text(s.sortByDate) }, onClick = { sortOption = SortOption.BY_DATE; sortMenuExpanded = false })
                                DropdownMenuItem(text = { Text(s.sortBySize) }, onClick = { sortOption = SortOption.BY_SIZE; sortMenuExpanded = false })
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (sortAsc) "Ascending" else "Descending") }, // Could add string resource
                                    trailingIcon = { Icon(if (sortAsc) androidx.compose.material.icons.Icons.Default.ArrowUpward else androidx.compose.material.icons.Icons.Default.ArrowDownward, null) },
                                    onClick = { sortAsc = !sortAsc; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                }

                val showFiles = if (query.isNotBlank()) applySort(globalResults) else applySort(pdfs)
                val showFolders = if (query.isNotBlank()) emptyList<File>() else folders

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                        showFiles.isEmpty() && showFolders.isEmpty() -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    androidx.compose.material.icons.Icons.Default.SentimentDissatisfied,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    if (searching) s.searching else s.emptyList,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        else -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = gridMinCell),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp)
                            ) {
                                items(showFolders.size) { i ->
                                    val dir = showFolders[i]
                                    FolderCard(
                                        name = dir.name,
                                        onClick = { selectedCategory = dir },
                                        onLongClick = {
                                            fileToEdit = dir
                                            isDirTarget = true
                                            renameText = dir.name
                                            showFileOptionsDialog = true
                                        }
                                    )
                                }
                                items(showFiles.size) { i ->
                                    val f = showFiles[i]
                                    val bmp = thumbs[f]
                                    PdfCard(
                                        name = f.nameWithoutExtension,
                                        sizeText = formatBytes(f.length()),
                                        dateText = formatRelativeDate(f.lastModified()),
                                        thumbnail = bmp,
                                        onClick = { onOpen(f) },
                                        onLongClick = {
                                            fileToEdit = f
                                            isDirTarget = false
                                            renameText = f.nameWithoutExtension
                                            showFileOptionsDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
            onDismissRequest = { showFileOptionsDialog = false },
            title = { Text(if (isDirTarget) fileToEdit?.name ?: "" else fileToEdit?.nameWithoutExtension ?: "") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showFileOptionsDialog = false
                            showRenameDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(s.rename, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                    TextButton(
                        onClick = {
                            showFileOptionsDialog = false
                            showDeleteDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(s.delete, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFileOptionsDialog = false }) { Text(s.cancel) }
            }
        )
    }

    if (showRenameDialog && fileToEdit != null) {
        SimpleInputDialog(
            title = s.rename,
            label = s.rename,
            value = renameText,
            onValueChange = { renameText = it },
            onDismiss = { showRenameDialog = false },
            onConfirm = {
                if (renameText.isNotBlank()) {
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            val target = fileToEdit!!
                            val newFile = File(target.parentFile, if (target.isDirectory) renameText else "$renameText.pdf")
                            if (!newFile.exists()) target.renameTo(newFile) else false
                        }
                        if (success) {
                            refreshCategories()
                            loadCategory(selectedCategory)
                        } else {
                            Toast.makeText(context, s.cannotRename, Toast.LENGTH_SHORT).show()
                        }
                        showRenameDialog = false
                    }
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

    if (showSettingsDialog) {
        SettingsDialog(
            isDarkMode = isDarkMode,
            onToggleDarkMode = onToggleDarkMode,
            language = language,
            onToggleLanguage = onToggleLanguage,
            gridScale = gridScale,
            onGridScaleChange = { gridScale = it },
            onDismiss = { showSettingsDialog = false }
        )
    }
}

// ====== UI Components ======

@Composable
private fun CategoryItem(name: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium, // Pill shape-ish
        color = containerColor,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
