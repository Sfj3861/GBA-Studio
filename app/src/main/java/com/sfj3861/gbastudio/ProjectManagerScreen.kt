package com.sfj3861.gbastudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectManagerScreen(repository: ProjectRepository, screenTitle: String = "Mis proyectos") {
    var projects by remember { mutableStateOf(emptyList<GbaProject>()) }
    var activeProject by remember { mutableStateOf<GbaProject?>(null) }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var dirty by remember { mutableStateOf(false) }
    var saveState by remember { mutableStateOf("Sin cambios") }
    var showCreate by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GbaProject?>(null) }
    var deleting by remember { mutableStateOf<GbaProject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun refresh() { projects = repository.list() }
    var exportTarget by remember { mutableStateOf<GbaProject?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado.")
                    repository.importProject(input)
                }
                refresh()
                error = null
            } catch (e: Exception) {
                error = e.message ?: "No se pudo importar el proyecto."
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val project = exportTarget
        exportTarget = null
        if (uri != null && project != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("No se pudo crear el archivo de destino.")
                    repository.exportProject(project, output)
                }
                saveState = "Proyecto exportado"
                error = null
            } catch (e: Exception) {
                error = e.message ?: "No se pudo exportar el proyecto."
            }
        }
    }

    fun loadFile(project: GbaProject, relativePath: String) {
        scope.launch {
            try {
                val previousFile = selectedFile
                val previousDraft = draft
                val previousDirty = dirty
                if (previousDirty && activeProject != null && previousFile != null) {
                    withContext(Dispatchers.IO) { repository.writeText(activeProject!!, previousFile, previousDraft) }
                }
                val text = withContext(Dispatchers.IO) { repository.readText(project, relativePath) }
                activeProject = project
                selectedFile = relativePath
                draft = text
                dirty = false
                saveState = "Guardado"
                error = null
            } catch (e: Exception) {
                error = e.message ?: "No se pudo abrir el archivo."
            }
        }
    }

    fun saveFile(project: GbaProject, relativePath: String, text: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.writeText(project, relativePath, text) }
                dirty = false
                saveState = "Guardado"
                refresh()
            } catch (e: Exception) {
                saveState = "Error al guardar"
                error = e.message ?: "No se pudo guardar el archivo."
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(activeProject?.id, selectedFile, draft, dirty) {
        if (dirty && activeProject != null && selectedFile != null) {
            saveState = "Guardando…"
            delay(900)
            val project = activeProject
            val file = selectedFile
            if (project != null && file != null && dirty) {
                try {
                    withContext(Dispatchers.IO) { repository.writeText(project, file, draft) }
                    dirty = false
                    saveState = "Autoguardado"
                    refresh()
                } catch (e: Exception) {
                    saveState = "Error al guardar"
                    error = e.message ?: "No se pudo autoguardar."
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (activeProject == null) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(screenTitle, style = MaterialTheme.typography.headlineMedium)
                    Text("Proyectos guardados localmente en este dispositivo.")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                        Text("Importar")
                    }
                    Button(onClick = { showCreate = true }) { Text("Nuevo proyecto") }
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            if (projects.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Todavía no hay proyectos", style = MaterialTheme.typography.titleLarge)
                        Text("Crea uno para generar una plantilla C para Game Boy Advance.")
                        OutlinedButton(onClick = { showCreate = true }) { Text("Crear mi primer proyecto") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(projects, key = { it.id }) { project ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(project.name, style = MaterialTheme.typography.titleLarge)
                                Text("C · Game Boy Advance · " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(project.updatedAt)),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { activeProject = project; selectedFile = null; error = null }) { Text("Abrir") }
                                    OutlinedButton(onClick = { exportTarget = project; exportLauncher.launch(project.name + ".gbastudio.zip") }) { Text("Exportar") }
                                    OutlinedButton(onClick = { editing = project }) { Text("Renombrar") }
                                    TextButton(onClick = { deleting = project }) { Text("Eliminar") }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val project = activeProject!!
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        try {
                            val file = selectedFile
                            val opened = activeProject
                            if (dirty && file != null && opened != null) {
                                withContext(Dispatchers.IO) { repository.writeText(opened, file, draft) }
                            }
                            activeProject = null
                            selectedFile = null
                            dirty = false
                            refresh()
                        } catch (e: Exception) {
                            error = e.message ?: "No se pudieron guardar los cambios."
                        }
                    }
                }) { Text("Volver") }
                Column(Modifier.weight(1f)) {
                    Text(project.name, style = MaterialTheme.typography.headlineSmall)
                    Text("Explorador y editor de archivos")
                }
                Button(onClick = { showNewFile = true }) { Text("Nuevo archivo") }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(190.dp).heightIn(min = 100.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ARCHIVOS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    val entries = project.directory.walkTopDown().filter {
                        it.isFile && it.extension.lowercase() in setOf("c", "h", "cpp", "hpp", "txt", "md", "json")
                    }.toList()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(entries, key = { it.absolutePath }) { file ->
                            val relative = file.relativeTo(project.directory).invariantSeparatorsPath
                            OutlinedButton(
                                onClick = { if (relative != selectedFile) loadFile(project, relative) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(relative, maxLines = 2) }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedFile == null) {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Text("Selecciona un archivo de la izquierda para editarlo.", Modifier.padding(18.dp))
                        }
                    } else {
                        Row(Modifier.fillMaxWidth()) {
                            Text(selectedFile!!, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(saveState, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it; dirty = true; saveState = "Cambios pendientes" },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            visualTransformation = if (selectedFile?.substringAfterLast('.', "").lowercase() in setOf("c", "h", "cpp", "hpp"))
                                CCodeVisualTransformation else VisualTransformation.None,
                            label = { Text("Código / contenido") },
                            placeholder = { Text("Escribe aquí…") }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val file = selectedFile
                                if (file != null) saveFile(project, file, draft)
                            }, enabled = dirty) { Text("Guardar ahora") }
                            OutlinedButton(onClick = {
                                val file = selectedFile
                                if (file != null) loadFile(project, file)
                            }) { Text("Recargar") }
                        }
                    }
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }
    }

    if (showNewFile && activeProject != null) {
        ProjectNameDialog("Nuevo archivo", "Ruta (ej. src/player.c)", "Crear", { showNewFile = false }) { relativePath ->
            val project = activeProject
            if (project != null) {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repository.createTextFile(project, relativePath.trim()) }
                        showNewFile = false
                        loadFile(project, relativePath.trim())
                        error = null
                    } catch (e: Exception) {
                        error = e.message ?: "No se pudo crear el archivo."
                    }
                }
            }
        }
    }
    if (showCreate) ProjectNameDialog("Nuevo proyecto", "Nombre del proyecto", "Crear", { showCreate = false }) { name ->
        scope.launch {
            try { withContext(Dispatchers.IO) { repository.create(name) }; refresh(); error = null }
            catch (e: Exception) { error = e.message ?: "No se pudo crear el proyecto." }
        }
    }
    editing?.let { project ->
        ProjectNameDialog("Renombrar proyecto", "Nuevo nombre", "Guardar", { editing = null }, project.name) { name ->
            scope.launch {
                try { withContext(Dispatchers.IO) { repository.rename(project, name) }; refresh(); error = null }
                catch (e: Exception) { error = e.message ?: "No se pudo renombrar." }
            }
        }
    }
    deleting?.let { project ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar proyecto") },
            text = { Text("Se eliminarán el proyecto «" + project.name + "» y sus archivos locales. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try { withContext(Dispatchers.IO) { repository.delete(project) }; refresh(); error = null }
                        catch (e: Exception) { error = e.message ?: "No se pudo eliminar." }
                    }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ProjectNameDialog(
    title: String,
    label: String,
    action: String,
    onDismiss: () -> Unit,
    initial: String = "",
    onConfirm: (String) -> Unit
) {
    var name by remember(title, initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name); onDismiss() }) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
