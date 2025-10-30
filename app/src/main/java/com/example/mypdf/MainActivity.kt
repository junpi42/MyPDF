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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap

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
        PdfViewerScreen(file = selectedFile!!, onBack = { selectedFile = null })
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
    suspend fun refreshCategories() {
        loading = true
        val (rootDirs, _) = withContext(Dispatchers.IO) { listLibraryFolder(context, "") }
        categories = rootDirs.sortedBy { it.name.lowercase() }
        if (selectedCategory?.exists() != true) selectedCategory = categories.firstOrNull()
        loading = false
    }
    suspend fun loadCategory(cat: File?) {
        loading = true
        if (cat == null) { folders = emptyList(); pdfs = emptyList(); loading = false; return }
        val base = appPdfDir(context)
        val relPath = runCatching { cat.relativeTo(base).path.replace(File.separatorChar, '/') }
            .getOrElse { cat.name }
        val (fList, pList) = withContext(Dispatchers.IO) { listLibraryFolder(context, relPath) }
        folders = fList.sortedBy { it.name.lowercase() }
        pdfs = pList
        withContext(Dispatchers.IO) {
            pList.forEach { f -> if (!thumbs.containsKey(f)) thumbs[f] = generatePdfThumbnail(f) }
        }
        loading = false
    }
    suspend fun searchEverywhere(text: String) {
        if (text.isBlank()) { globalResults = emptyList(); return }
        searching = true
        val results = withContext(Dispatchers.IO) {
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
        withContext(Dispatchers.IO) { results.forEach { if (!thumbs.containsKey(it)) thumbs[it] = generatePdfThumbnail(it) } }
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
        return if (sortAsc) base else base.reversed()
    }
    fun countFilesRecursive(dir: File): Int {
        var count = 0
        dir.listFiles()?.forEach {
            if (it.isDirectory) count += countFilesRecursive(it)
            else if (it.extension.equals("pdf", true)) count++
        }
        return count
    }
    fun formatBytes(b: Long): String {
        val kb = 1024.0; val mb = kb * 1024; val gb = mb * 1024
        return when {
            b >= gb -> String.format("%.1f GB", b / gb)
            b >= mb -> String.format("%.1f MB", b / mb)
            b >= kb -> String.format("%.0f KB", b / kb)
            else -> "$b B"
        }
    }
    fun formatRelativeDate(ts: Long): String {
        val now = System.currentTimeMillis()
        val d = (now - ts) / (1000 * 60 * 60 * 24)
        return when {
            d <= 0 -> "Today"
            d == 1L -> "Yesterday"
            d < 7  -> "$d days ago"
            else   -> java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault()).format(java.util.Date(ts))
        }
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
                } ?: ""
                val newFile = withContext(Dispatchers.IO) { clonePdfIntoApp(context, uri, relPath) }
                withContext(Dispatchers.IO) { thumbs[newFile] = generatePdfThumbnail(newFile) }
                loadCategory(selectedCategory)
                Toast.makeText(context, "PDF importado ✓", Toast.LENGTH_SHORT).show()
                loading = false
            }
        }
    )
    Row(Modifier.fillMaxSize()) {
        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier.width(260.dp).fillMaxHeight()
        ) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    items(categories) { cat ->
                        val selected = cat == selectedCategory
                        CategoryItem(
                            name = cat.name,
                            count = remember(cat) { countFilesRecursive(cat) },
                            selected = selected,
                            onClick = { selectedCategory = cat }
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
                var showCategoryDialog by remember { mutableStateOf(false) }
                var categoryName by remember { mutableStateOf("") }
                Surface(
                    onClick = { showCategoryDialog = true },
                    shape = MaterialTheme.shapes.large,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("+")
                        Spacer(Modifier.width(10.dp))
                        Text("New Category")
                    }
                }
                if (showCategoryDialog) {
                    AlertDialog(
                        onDismissRequest = { showCategoryDialog = false },
                        confirmButton = {
                            TextButton(onClick = {
                                if (categoryName.isNotBlank()) {
                                    scope.launch {
                                        val created = createLibraryFolder(context, "", categoryName)
                                        if (created != null) {
                                            refreshCategories()
                                            selectedCategory = created
                                            categoryName = ""
                                            showCategoryDialog = false
                                        } else {
                                            Toast.makeText(context, "No se pudo crear", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }) { Text("Create") }
                        },
                        dismissButton = { TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") } },
                        title = { Text("New Category") },
                        text = {
                            OutlinedTextField(
                                value = categoryName,
                                onValueChange = { categoryName = it },
                                label = { Text("Name") },
                                singleLine = true
                            )
                        }
                    )
                }
            }
        }
        VerticalDivider()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text(
                "PDF Library Manager",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    Row(
                        Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    showFiles.isEmpty() && showFolders.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (searching) "Searching…" else "No items yet.")
                        }
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 280.dp),
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
                                    .height(220.dp)
                                    .clickable { selectedCategory = dir }
                            ) {
                                Column(Modifier.fillMaxSize()) {
                                    Box(
                                        Modifier.fillMaxWidth().weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) { Text("📁", style = MaterialTheme.typography.headlineLarge) }
                                    Surface(
                                        tonalElevation = 0.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(14.dp)) {
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
                FloatingActionButton(
                    onClick = { picker.launch(arrayOf("application/pdf")) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                ) { Text("⤴") }
            }
        }
    }
}

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
