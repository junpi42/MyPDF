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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets

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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    var selectedFile by remember { mutableStateOf<File?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedFile == null) {
                LibraryScreen(onOpen = { selectedFile = it })
            } else {
                PdfEditScreen(file = selectedFile!!, onBack = { selectedFile = null })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpen: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var fabMenuExpanded by remember { mutableStateOf(false) }

    // nuevo estado para acciones de long-press
    var fileToEdit by remember { mutableStateOf<File?>(null) }
    var isDirTarget by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isTablet = screenWidthDp >= 900
    val isLargePhone = screenWidthDp in 600..899

    val sidebarWidth = when {
        isTablet -> 280.dp
        isLargePhone -> 200.dp
        else -> 96.dp
    }

    val gridMinCell = when {
        isTablet -> 260.dp
        isLargePhone -> 200.dp
        else -> 150.dp
    }

    val folderIconTextStyle = when {
        isTablet -> MaterialTheme.typography.displaySmall
        isLargePhone -> MaterialTheme.typography.headlineLarge
        else -> MaterialTheme.typography.headlineMedium
    }

    val thumbHeight = when {
        isTablet -> 180.dp
        isLargePhone -> 130.dp
        else -> 90.dp
    }

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

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.width(sidebarWidth).fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Categorías", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(categories) { cat ->
                            val selected = cat == selectedCategory
                            Surface(
                                shape = MaterialTheme.shapes.large,
                                tonalElevation = if (selected) 6.dp else 0.dp,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .clickable { selectedCategory = cat }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(if (selected) "📚" else "📁", style = folderIconTextStyle)
                                    Spacer(Modifier.width(8.dp))
                                    Text(cat.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                }
                            }
                        }
                    }
                    TextButton(onClick = { showNewCategoryDialog = true }, modifier = Modifier.padding(8.dp)) {
                        Text("+ categoría")
                    }
                }
            }

            VerticalDivider()

            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Buscar en biblioteca") }
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            sortOption = when (sortOption) {
                                SortOption.BY_NAME -> SortOption.BY_DATE
                                SortOption.BY_DATE -> SortOption.BY_SIZE
                                SortOption.BY_SIZE -> SortOption.BY_NAME
                            }
                        }) {
                            Text(
                                when (sortOption) {
                                    SortOption.BY_NAME -> "Nombre"
                                    SortOption.BY_DATE -> "Fecha"
                                    SortOption.BY_SIZE -> "Tamaño"
                                }
                            )
                        }
                        IconButton(onClick = { sortAsc = !sortAsc }) {
                            Text(if (sortAsc) "↑" else "↓")
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
                            Text(if (searching) "Buscando…" else "Sin elementos")
                        }

                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = gridMinCell),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp)
                        ) {
                            items(showFolders.size) { i ->
                                val dir = showFolders[i]
                                Surface(
                                    tonalElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(if (isTablet) 0.9f else 1.0f)
                                        .combinedClickable(
                                            onClick = { selectedCategory = dir },
                                            onLongClick = {
                                                fileToEdit = dir
                                                isDirTarget = true
                                                renameText = dir.name
                                                showRenameDialog = true
                                            }
                                        )
                                ) {
                                    Column(
                                        Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("📁", style = folderIconTextStyle)
                                        Spacer(Modifier.height(8.dp))
                                        Text(dir.name, maxLines = 2, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                            items(showFiles.size) { i ->
                                val f = showFiles[i]
                                val bmp = thumbs[f]
                                Surface(
                                    tonalElevation = 2.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(if (isTablet) 0.9f else 1.0f)
                                        .combinedClickable(
                                            onClick = { onOpen(f) },
                                            onLongClick = {
                                                fileToEdit = f
                                                isDirTarget = false
                                                renameText = f.nameWithoutExtension
                                                showRenameDialog = true
                                            }
                                        )
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        if (bmp != null) {
                                            Image(
                                                bmp.asImageBitmap(),
                                                null,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(thumbHeight)
                                            )
                                        } else {
                                            Text("📄", style = folderIconTextStyle)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = f.nameWithoutExtension,
                                            maxLines = 2,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${f.length() / 1024} KB",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Box {
                    FloatingActionButton(onClick = { fabMenuExpanded = true }) {
                        Text("+")
                    }
                    DropdownMenu(
                        expanded = fabMenuExpanded,
                        onDismissRequest = { fabMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Importar PDF") },
                            onClick = {
                                fabMenuExpanded = false
                                picker.launch(arrayOf("application/pdf"))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Crear carpeta") },
                            onClick = {
                                fabMenuExpanded = false
                                showNewFolderDialog = true
                            }
                        )
                    }
                }
            }
        }

        if (showNewCategoryDialog) {
            AlertDialog(
                onDismissRequest = { showNewCategoryDialog = false },
                title = { Text("Nueva categoría") },
                text = {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        singleLine = true,
                        label = { Text("Nombre") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newCategoryName.isNotBlank()) {
                            scope.launch {
                                val created = createLibraryFolder(context, "", newCategoryName)
                                if (created != null) {
                                    refreshCategories()
                                    selectedCategory = created
                                } else {
                                    Toast.makeText(context, "No se pudo crear la categoría", Toast.LENGTH_SHORT).show()
                                }
                                showNewCategoryDialog = false
                                newCategoryName = ""
                            }
                        }
                    }) {
                        Text("Crear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNewCategoryDialog = false
                        newCategoryName = ""
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text("Nueva carpeta") },
                text = {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        singleLine = true,
                        label = { Text("Nombre carpeta") }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newFolderName.isNotBlank()) {
                            scope.launch {
                                val base = selectedCategory ?: ensureDefaultCategory()
                                val candidate = File(base, newFolderName)
                                val ok = withContext(Dispatchers.IO) { candidate.mkdir() }
                                if (ok) {
                                    loadCategory(selectedCategory)
                                    newFolderName = ""
                                } else {
                                    Toast.makeText(context, "No se pudo crear carpeta", Toast.LENGTH_SHORT).show()
                                }
                                showNewFolderDialog = false
                            }
                        }
                    }) {
                        Text("Crear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showNewFolderDialog = false
                        newFolderName = ""
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showRenameDialog && fileToEdit != null) {
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Renombrar") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = renameText,
                            onValueChange = { renameText = it },
                            singleLine = true,
                            label = { Text("Nuevo nombre") }
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text("Eliminar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = fileToEdit ?: return@TextButton
                        val newNameClean = renameText.trim()
                        if (newNameClean.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                val parent = target.parentFile
                                if (parent != null) {
                                    val ext = if (!isDirTarget && target.extension.isNotEmpty()) ".${target.extension}" else ""
                                    val newFile = File(parent, newNameClean + ext)
                                    val ok = target.renameTo(newFile)
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            if (!isDirTarget) {
                                                thumbs.remove(target)
                                            }
                                            showRenameDialog = false
                                            fileToEdit = null
                                            loadCategory(selectedCategory)
                                        } else {
                                            Toast.makeText(context, "No se pudo renombrar", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showRenameDialog = false
                        fileToEdit = null
                    }) {
                        Text("Cancelar")
                    }
                }
            )
        }

        if (showDeleteDialog && fileToEdit != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Eliminar") },
                text = {
                    Text("¿Seguro que quieres eliminar ${(if (isDirTarget) "la carpeta" else "el PDF")}? Esta acción no se puede deshacer.")
                },
                confirmButton = {
                    TextButton(onClick = {
                        val target = fileToEdit ?: return@TextButton
                        scope.launch(Dispatchers.IO) {
                            fun deleteRecursively(f: File) {
                                if (f.isDirectory) {
                                    f.listFiles()?.forEach { deleteRecursively(it) }
                                }
                                f.delete()
                            }
                            deleteRecursively(target)
                            withContext(Dispatchers.Main) {
                                if (!isDirTarget) {
                                    thumbs.remove(target)
                                }
                                showDeleteDialog = false
                                showRenameDialog = false
                                fileToEdit = null
                                loadCategory(selectedCategory)
                            }
                        }
                    }) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
// ====== UI helpers que podrían haberse perdido ======
@Composable
private fun CategoryItem(name: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        tonalElevation = if (selected) 6.dp else 0.dp,
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (selected) "📚" else "📁")
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text("$count files", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PdfCard(
    name: String,
    sizeText: String,
    dateText: String,
    thumbnail: Bitmap?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().height(260.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (thumbnail != null) {
                    Image(thumbnail.asImageBitmap(), null, modifier = Modifier.fillMaxSize().padding(16.dp))
                } else {
                    Text("📄", style = MaterialTheme.typography.headlineLarge)
                }
            }
            Surface(tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Text(sizeText, style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(12.dp))
                            Text(dateText, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
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
