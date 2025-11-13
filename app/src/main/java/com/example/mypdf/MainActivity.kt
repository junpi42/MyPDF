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

    if (selectedFile == null) {
        LibraryScreen(onOpen = { selectedFile = it })
    } else {
        // Abrir siempre la pantalla de edición (que usa visor con edición siempre activa)
        PdfEditScreen(file = selectedFile!!, onBack = { selectedFile = null })
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

    // Estado para crear nueva categoría (diálogo)
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("") }
    // Estados para panel derecho (gestión de carpetas)
    var newFolderName by remember { mutableStateOf("") }
    var folderToDelete by remember { mutableStateOf<File?>(null) }

    // ------- Timings integrados -------
    suspend fun refreshCategories() {
        loading = true
        val pair = Perf.timeIO("listLibrary:root") { listLibraryFolder(context, "") }
        val (rootDirs, _) = pair
        categories = Perf.time("sort:cats") { rootDirs.sortedBy { it.name.lowercase() } }
        if (selectedCategory?.exists() != true) selectedCategory = categories.firstOrNull()
        loading = false
    }

    suspend fun loadCategory(cat: File?) {
        loading = true
        if (cat == null) { folders = emptyList(); pdfs = emptyList(); loading = false; return }
        val base = appPdfDir(context)
        val relPath = runCatching { cat.relativeTo(base).path.replace(File.separatorChar, '/') }
            .getOrElse { cat.name }

        val (fList, pList) = Perf.timeIO("listLibrary:$relPath") { listLibraryFolder(context, relPath) }
        folders = Perf.time("sort:folders") { fList.sortedBy { it.name.lowercase() } }
        pdfs = pList

        Perf.timeIO("thumbs:batch:$relPath") {
            pList.forEach { f ->
                if (!thumbs.containsKey(f)) {
                    thumbs[f] = Perf.timeIO("thumb:gen:${f.name}") { generatePdfThumbnailCached(context, f) }
                }
            }
        }
        loading = false
        // Dump opcional de resumen tras cargar categoría
        Perf.dumpSummary()
    }

    suspend fun searchEverywhere(text: String) {
        if (text.isBlank()) { globalResults = emptyList(); return }
        searching = true

        val results = Perf.timeIO("search:walk") {
            fun walk(dir: File, acc: MutableList<File>) {
                dir.listFiles()?.forEach { f ->
                    if (f.isDirectory) walk(f, acc)
                    else if (f.extension.equals("pdf", true)) acc += f
                }
            }
            val (rootDirs, rootPdfs) = listLibraryFolder(context, "")
            val all = mutableListOf<File>()
            all += rootPdfs
            rootDirs.forEach { walk(it, all) }
            all.filter { it.name.contains(text, ignoreCase = true) }
        }

        Perf.timeIO("thumbs:searchBatch") {
            results.forEach {
                if (!thumbs.containsKey(it)) {
                    thumbs[it] = generatePdfThumbnailCached(context, it)
                }
            }
        }
        globalResults = Perf.time("search:assign") { results }
        searching = false
    }

    fun applySort(list: List<File>): List<File> {
        val comp = when (sortOption) {
            SortOption.BY_NAME -> compareBy<File> { it.name.lowercase() }
            SortOption.BY_DATE -> compareBy<File> { it.lastModified() }
            SortOption.BY_SIZE -> compareBy<File> { it.length() }
        }
        val base = Perf.time("sort:files:$sortOption") { list.sortedWith(comp) }
        return if (sortAsc) base else base.asReversed()
    }

    fun countFilesRecursive(dir: File): Int = Perf.time("count:${dir.name}") {
        var count = 0
        dir.listFiles()?.forEach {
            if (it.isDirectory) count += countFilesRecursive(it)
            else if (it.extension.equals("pdf", true)) count++
        }
        count
    }
    // ------- Fin timings integrados -------

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
                } ?: ""
                val newFile = Perf.timeIO("import:clone:$relPath") { clonePdfIntoApp(context, uri, relPath) }
                withContext(Dispatchers.IO) {
                    thumbs[newFile] = Perf.timeIO("thumb:gen:${newFile.name}") { generatePdfThumbnailCached(context, newFile) }
                }
                loadCategory(selectedCategory)
                Toast.makeText(context, "PDF importado ✓", Toast.LENGTH_SHORT).show()
                loading = false
                // Resumen tras importar
                Perf.dumpSummary()
            }
        }
    )

    // Reemplazamos BoxWithConstraints por LocalConfiguration para detectar ancho
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 800
    val sidebarWidth = if (!isCompact) 260.dp else 72.dp

    // Layout principal: barra izquierda siempre visible, centro amplio, panel derecho opcional
    Row(Modifier.fillMaxSize()) {
        // Barra izquierda (siempre visible): compacta en móviles
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.width(sidebarWidth).fillMaxHeight()
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                )
                LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    items(categories) { cat ->
                        val selected = cat == selectedCategory
                        CategoryItem(
                            name = cat.name,
                            count = remember(cat) { countFilesRecursive(cat) },
                            selected = selected,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }
                Box(Modifier.fillMaxWidth()) {
                    FloatingActionButton(onClick = { showCategoryDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) { Text("➕") }
                }
            }
        }

        VerticalDivider()

        // Contenido central
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                "PDF Library Manager",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search PDFs by name…") },
                    leadingIcon = { Text("🔍") },
                    shape = MaterialTheme.shapes.large
                )
                Spacer(Modifier.width(12.dp))
                Surface(
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sort: " + when (sortOption) {
                            SortOption.BY_NAME -> "Name"
                            SortOption.BY_DATE -> "Date"
                            SortOption.BY_SIZE -> "Size"
                        })
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            sortOption = when (sortOption) {
                                SortOption.BY_NAME -> SortOption.BY_DATE
                                SortOption.BY_DATE -> SortOption.BY_SIZE
                                SortOption.BY_SIZE -> SortOption.BY_NAME
                            }
                        }) { Text("Change") }
                        IconButton(onClick = { sortAsc = !sortAsc }) { Text(if (sortAsc) "↑" else "↓") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val showFiles = if (query.isNotBlank()) applySort(globalResults) else applySort(pdfs)
            val showFolders = if (query.isNotBlank()) emptyList<File>() else folders

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    showFiles.isEmpty() && showFolders.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (searching) "Searching…" else "No items yet.") }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = if (isCompact) 160.dp else 280.dp),
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 96.dp)
                    ) {
                        items(showFolders.size) { i ->
                            val dir = showFolders[i]
                            Surface(
                                tonalElevation = 1.dp,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isCompact) 160.dp else 220.dp)
                                    .clickable { selectedCategory = dir }
                            ) {
                                Column(Modifier.fillMaxSize()) {
                                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("📁", style = MaterialTheme.typography.headlineLarge) }
                                    Surface(tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(dir.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                                            Text("${countFilesRecursive(dir)} files", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        items(showFiles.size) { i ->
                            val f = showFiles[i]
                            val bmp = thumbs[f]
                            PdfCard(
                                name = f.nameWithoutExtension,
                                sizeText = formatBytes(f.length()),
                                dateText = formatRelativeDate(f.lastModified()),
                                thumbnail = bmp,
                                onClick = { onOpen(f) }
                            )
                        }
                    }
                }

                FloatingActionButton(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)) { Text("⤴") }
            }
        }

        // Panel derecho solo en pantallas grandes
        if (!isCompact) {
            VerticalDivider()
            Surface(tonalElevation = 1.dp, modifier = Modifier.width(260.dp).fillMaxHeight()) {
                Column(Modifier.fillMaxSize()) {
                    Text("Folders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
                    if (folders.isEmpty()) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text("No folders") }
                    } else {
                        LazyColumn(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                            items(folders) { dir ->
                                Row(Modifier.fillMaxWidth().clickable { selectedCategory = dir }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(dir.name, style = MaterialTheme.typography.bodyMedium)
                                        Text("${countFilesRecursive(dir)} files", style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { folderToDelete = dir }) { Text("🗑") }
                                }
                            }
                        }
                    }

                    Column(Modifier.padding(12.dp)) {
                        OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("New folder") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = {
                                if (newFolderName.isNotBlank()) {
                                    scope.launch {
                                        val base = selectedCategory ?: appPdfDir(context)
                                        val candidate = File(base, newFolderName)
                                        val ok = withContext(Dispatchers.IO) { candidate.mkdir() }
                                        if (ok) {
                                            loadCategory(selectedCategory)
                                            newFolderName = ""
                                        } else {
                                            Toast.makeText(context, "No se pudo crear carpeta", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }) { Text("Create") }
                        }
                    }
                }
            }
        }
    }
    // fin layout principal
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
