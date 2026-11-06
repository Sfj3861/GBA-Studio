package com.sfj3861.gbastudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sfj3861.gbastudio.rom.GbaRomHeader
import com.sfj3861.gbastudio.rom.GbaRomHeaderParser

@Composable
fun RomAnalyzerScreen() {
    val context = LocalContext.current
    var selectedName by remember { mutableStateOf<String?>(null) }
    var header by remember { mutableStateOf<GbaRomHeader?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedName = uri.lastPathSegment ?: "ROM seleccionada"
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(GbaRomHeaderParser.HEADER_SIZE)
                    var offset = 0
                    while (offset < buffer.size) {
                        val count = input.read(buffer, offset, buffer.size - offset)
                        if (count < 0) break
                        offset += count
                    }
                    if (offset < buffer.size) {
                        throw IllegalArgumentException("El archivo no contiene una cabecera GBA completa.")
                    }
                    buffer
                } ?: throw IllegalStateException("Android no pudo abrir el archivo seleccionado.")

                header = GbaRomHeaderParser.parse(bytes)
                error = null
            } catch (exception: Exception) {
                header = null
                error = exception.message ?: "No se pudo analizar el archivo."
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ROM Analyzer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Importa una ROM mediante el selector de archivos de Android. Esta primera herramienta lee únicamente los 192 bytes de la cabecera y no ejecuta ni modifica el archivo.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                Text("Seleccionar archivo")
            }
            if (header != null) {
                OutlinedButton(onClick = { header = null; error = null; selectedName = null }) {
                    Text("Limpiar")
                }
            }
        }
        selectedName?.let {
            Text("Archivo: $it", style = MaterialTheme.typography.labelMedium)
        }
        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        header?.let { data ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cabecera GBA", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    HeaderValue("Título", data.title.ifBlank { "(vacío)" })
                    HeaderValue("Game code", data.gameCode)
                    HeaderValue("Maker code", data.makerCode)
                    HeaderValue("Versión", data.softwareVersion.toString())
                    HeaderValue("Checksum almacenado", "0x%02X".format(data.storedChecksum))
                    HeaderValue("Checksum calculado", "0x%02X".format(data.expectedChecksum))
                    Text(
                        if (data.checksumValid) "Checksum de cabecera correcto" else "El checksum de cabecera no coincide",
                        color = if (data.checksumValid) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (header == null && error == null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Text(
                    "Todavía no has seleccionado una ROM. Puedes analizar archivos .gba propios o que tengas autorización para examinar.",
                    Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HeaderValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
