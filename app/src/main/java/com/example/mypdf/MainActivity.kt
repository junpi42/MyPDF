package com.example.mypdf

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.util.Date
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap


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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpen: (File) -> Unit) {
    val context = LocalContext.current
    var pdfs by remember { mutableStateOf(listOf<File>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Estado para controlar si las miniaturas están cargando
    var thumbnailsLoading by remember { mutableStateOf(true) }
    val thumbnailsMap = remember { mutableStateMapOf<File, Bitmap?>() }

    // Cargar la lista al entrar y generar miniaturas
    LaunchedEffect(Unit) {
        thumbnailsLoading = true
        val files = withContext(Dispatchers.IO) {
            listAppPdfs(context)
        }
        pdfs = files

        // Generar todas las miniaturas en background
        withContext(Dispatchers.IO) {
            files.forEach { file ->
                val thumbnail = generatePdfThumbnail(file)
                thumbnailsMap[file] = thumbnail
            }
        }
        thumbnailsLoading = false
    }

    // Selector del sistema para importar PDFs y clonarlos
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let { pickedUri ->
                // Mantener permiso de lectura (persistente)
                context.contentResolver.takePersistableUriPermission(
                    pickedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                // Clonar en background y refrescar lista
                scope.launch {
                    thumbnailsLoading = true
                    val newFile = withContext(Dispatchers.IO) {
                        clonePdfIntoApp(context, pickedUri)
                    }
                    val files = withContext(Dispatchers.IO) {
                        listAppPdfs(context)
                    }
                    pdfs = files

                    // Generar miniatura del nuevo archivo
                    if (newFile != null) {
                        val thumbnail = withContext(Dispatchers.IO) {
                            generatePdfThumbnail(newFile)
                        }
                        thumbnailsMap[newFile] = thumbnail
                    }
                    thumbnailsLoading = false
                    message = "PDF importado ✓"
                }
            }
        }
    )

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Tus PDFs") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                Text("+")
            }
        }
    ) { padding ->
        // Mostrar pantalla de carga mientras se generan las miniaturas
        if (thumbnailsLoading && pdfs.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Cargando miniaturas...")
                }
            }
        } else if (pdfs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("No hay PDFs. Pulsa + para importar uno.") }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(pdfs) { file ->
                    val thumbnail = thumbnailsMap[file]

                    ListItem(
                        headlineContent = { Text(file.name) },
                        supportingContent = {
                            Text("${file.length() / 1024} KB • ${Date(file.lastModified())}")
                        },
                        leadingContent = {
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail.asImageBitmap(),
                                    contentDescription = "Preview PDF",
                                    modifier = Modifier.size(60.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("📄", style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(file) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    // Feedback rápido
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            message = null
        }
    }
}
