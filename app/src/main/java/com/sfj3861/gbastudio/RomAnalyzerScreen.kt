package com.sfj3861.gbastudio

import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sfj3861.gbastudio.rom.GbaRomHeader
import com.sfj3861.gbastudio.rom.GbaRomHeaderParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HEX_PAGE_SIZE = 256L

@Composable
fun RomAnalyzerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedName by remember { mutableStateOf<String?>(null) }
    var header by remember { mutableStateOf<GbaRomHeader?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var romUri by remember { mutableStateOf<Uri?>(null) }
    var romSize by remember { mutableStateOf(-1L) }
    var hexOffset by remember { mutableStateOf(0L) }
    var hexPreview by remember { mutableStateOf<String?>(null) }

    fun loadHexPage(uri: Uri, offset: Long) {
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Android no pudo abrir la ROM.")
                    input.use { stream ->
                        var remaining = offset
                        while (remaining > 0) {
                            val skipped = stream.skip(remaining)
                            if (skipped > 0) {
                                remaining -= skipped
                            } else {
                                if (stream.read() < 0) break
                                remaining--
                            }
                        }
                        val buffer = ByteArray(HEX_PAGE_SIZE.toInt())
                        var count = 0
                        while (count < buffer.size) {
                            val read = stream.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        buffer.copyOf(count)
                    }
                }
                hexOffset = offset
                hexPreview = formatHexPreview(bytes, offset)
                error = null
            } catch (exception: Exception) {
                error = exception.message ?: "No se pudo leer esta página hexadecimal."
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedName = uri.lastPathSegment ?: "ROM seleccionada"
            romUri = uri
            romSize = try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
                } ?: -1L
            } catch (_: Exception) { -1L }
            try {
                val headerBytes = context.contentResolver.openInputStream(uri)?.use { input ->
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
                header = GbaRomHeaderParser.parse(headerBytes)
                error = null
                loadHexPage(uri, 0L)
            } catch (exception: Exception) {
                header = null
                hexPreview = null
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
            "Analiza la cabecera GBA y recorre el contenido de la ROM con un visor hexadecimal paginado. El archivo original no se modifica ni se ejecuta.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                Text("Seleccionar archivo")
            }
            if (header != null) {
                OutlinedButton(onClick = {
                    header = null
                    hexPreview = null
                    selectedName = null
                    romUri = null
                    romSize = -1L
                    hexOffset = 0L
                    error = null
                }) { Text("Limpiar") }
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
        hexPreview?.let { dump ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Visor hexadecimal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Offset 0x%08X · Tamaño %s".format(
                            hexOffset,
                            if (romSize >= 0) "%,d bytes".format(romSize) else "desconocido"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(dump.ifBlank { "No hay más datos en esta posición." }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { romUri?.let { loadHexPage(it, maxOf(0L, hexOffset - HEX_PAGE_SIZE)) } },
                            enabled = hexOffset > 0
                        ) { Text("Anterior") }
                        Button(
                            onClick = { romUri?.let { loadHexPage(it, hexOffset + HEX_PAGE_SIZE) } },
                            enabled = romUri != null && (romSize < 0 || hexOffset + HEX_PAGE_SIZE < romSize)
                        ) { Text("Siguiente") }
                    }
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

private fun formatHexPreview(bytes: ByteArray, baseOffset: Long): String {
    return bytes.indices.step(16).joinToString("\n") { rowStart ->
        val end = minOf(rowStart + 16, bytes.size)
        val hex = (rowStart until end).joinToString(" ") { index -> "%02X".format(bytes[index].toInt() and 0xFF) }
        val ascii = (rowStart until end).joinToString("") { index ->
            val value = bytes[index].toInt() and 0xFF
            if (value in 32..126) value.toChar().toString() else "."
        }
        "%08X  %-47s  |%s|".format(baseOffset + rowStart, hex, ascii)
    }
}
