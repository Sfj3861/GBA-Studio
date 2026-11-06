package com.sfj3861.gbastudio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectManagerScreen(repository: ProjectRepository) {
    var projects by remember { mutableStateOf(emptyList<GbaProject>()) }
    var activeProject by remember { mutableStateOf<GbaProject?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<GbaProject?>(null) }
    var deleting by remember { mutableStateOf<GbaProject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() { projects = repository.list() }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (activeProject != null) {
            val opened = activeProject!!
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { activeProject = null }) { Text("Volver") }
                Text(opened.name, style = MaterialTheme.typography.headlineSmall)
            }
            Text("Archivos del proyecto", style = MaterialTheme.typography.titleMedium)
            val entries = opened.directory.walkTopDown().filter { it.isFile }.toList()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { it.absolutePath }) { file ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Text(file.relativeTo(opened.directory).invariantSeparatorsPath, Modifier.fillMaxWidth().padding(12.dp))
                    }
                }
            }
        } else {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Mis proyectos", style = MaterialTheme.typography.headlineMedium)
                Text("Proyectos guardados localmente en este dispositivo.")
            }
            Button(onClick = { showCreate = true }) { Text("Nuevo proyecto") }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        if (projects.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Todavía no hay proyectos", style = MaterialTheme.typography.titleLarge)
                    Text("Crea uno para generar la estructura inicial de código C y carpetas de recursos.")
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
                            Text(project.directory.name, style = MaterialTheme.typography.labelSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { activeProject = project }) { Text("Abrir") }
                                OutlinedButton(onClick = { editing = project }) { Text("Renombrar") }
                                TextButton(onClick = { deleting = project }) { Text("Eliminar") }
                            }
                        }
                    }
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
