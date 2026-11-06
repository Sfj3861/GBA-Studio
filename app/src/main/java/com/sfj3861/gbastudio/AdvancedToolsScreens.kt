package com.sfj3861.gbastudio

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream

@Composable
fun DisassemblerScreen() {
    val context = LocalContext.current
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var label by remember { mutableStateOf("Ninguna ROM seleccionada") }
    var thumb by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf("080000C0") }
    var skipHeader by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf(emptyList<DisassembledInstruction>()) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(GbaLinker.MAX_ROM_SIZE) }
                    ?: error("No se pudo abrir el archivo.")
            }.onSuccess { data ->
                bytes = data
                label = uri.lastPathSegment ?: "ROM importada"
                rows = emptyList()
            }.onFailure { bytes = null; label = "Error al importar: ${it.message}"; rows = emptyList() }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Desensamblador ARM / Thumb", style = MaterialTheme.typography.headlineMedium)
        Text("Inspecciona instrucciones de una ROM GBA. El decodificador cubre un subconjunto de ARM7TDMI; las instrucciones desconocidas se muestran como datos.")
        Button(onClick = { open.launch(arrayOf("*/*")) }) { Text("Abrir ROM o binario") }
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Modo Thumb")
            Switch(checked = thumb, onCheckedChange = { thumb = it; rows = emptyList() })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Saltar cabecera GBA (0xC0)")
            Switch(checked = skipHeader, onCheckedChange = { skipHeader = it; rows = emptyList() })
        }
        OutlinedTextField(value = base, onValueChange = { base = it; rows = emptyList() }, label = { Text("Dirección base (hex)") }, singleLine = true)
        Button(enabled = bytes != null, onClick = {
            val start = base.toLongOrNull(16)?.toInt() ?: 0x08000000
            val source = bytes!!
            val offset = if (skipHeader && source.size >= 0xC0) 0xC0 else 0
            rows = ArmDisassembler.decode(source.copyOfRange(offset, minOf(source.size, offset + 4096)), start, thumb).take(512)
        }) { Text("Desensamblar") }
        if (rows.isNotEmpty()) {
            Text("${rows.size} instrucciones decodificadas")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    rows.forEach { Text(it.formatted(), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen() {
    var source by remember { mutableStateOf("#include <stdio.h>\n\nint main(void) {\n    printf(\"Hello GBA!\\n\");\n    return 0;\n}\n") }
    var results by remember { mutableStateOf(emptyList<CodeDiagnostic>()) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnósticos C / C++", style = MaterialTheme.typography.headlineMedium)
        Text("Analizador rápido local. Detecta algunos problemas frecuentes, pero no sustituye al compilador ni a Clang.")
        OutlinedTextField(value = source, onValueChange = { source = it }, modifier = Modifier.weight(1f).fillMaxWidth(), textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace), label = { Text("Código fuente") })
        Button(onClick = { results = CppDiagnostics.analyze(source) }) { Text("Analizar código") }
        if (results.isNotEmpty()) {
            Text("Resultados: ${results.size}")
            LazyColumn(Modifier.weight(0.8f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${item.severity} · Línea ${item.line}, columna ${item.column}", color = when(item.severity) {
                                CodeDiagnostic.Severity.ERROR -> MaterialTheme.colorScheme.error
                                CodeDiagnostic.Severity.WARNING -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.secondary
                            })
                            Text(item.message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LinkerScreen() {
    val context = LocalContext.current
    var input by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf("Ningún binario seleccionado") }
    var title by remember { mutableStateOf("MY GBA GAME") }
    var gameCode by remember { mutableStateOf("GBA0") }
    var maker by remember { mutableStateOf("00") }
    var logo by remember { mutableStateOf<ByteArray?>(null) }
    var logoName by remember { mutableStateOf("Sin bloque de logo (la BIOS puede rechazar la ROM)") }
    var output by remember { mutableStateOf<ByteArray?>(null) }
    var report by remember { mutableStateOf("") }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                it.readBoundedBytes(GbaLinker.MAX_ROM_SIZE - GbaLinker.HEADER_SIZE)
            } ?: error("No se pudo abrir el binario.")
        }.onSuccess {
            input = it
            fileName = uri.lastPathSegment ?: "binary.bin"
            output = null
            report = ""
        }.onFailure {
            input = null
            output = null
            fileName = "Error al importar: ${it.message}"
        }
    }
    val openLogo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(GbaRomHeader.LOGO_SIZE) } ?: error("No se pudo leer el bloque de logo.")
        }.onSuccess { data ->
            if (data.size == GbaRomHeader.LOGO_SIZE) {
                logo = data
                logoName = uri.lastPathSegment ?: "logo.bin"
                output = null
            } else {
                logo = null
                logoName = "Archivo inválido: se requieren exactamente 156 bytes"
            }
        }.onFailure { logo = null; logoName = "Error: ${it.message}" }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && output != null) runCatching {
            val stream = context.contentResolver.openOutputStream(uri) ?: error("No se pudo abrir el destino de exportación.")
            stream.use { it.write(output!!) }
        }.onSuccess { report += "\nROM exportada." }.onFailure { report += "\nError al exportar: ${it.message}" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Linker / ROM image builder", style = MaterialTheme.typography.headlineMedium)
        Text("Empaqueta un binario raw, genera el salto de entrada ARM y construye los campos de cabecera GBA. El motor interno también admite símbolos y relocaciones, aunque esta pantalla ofrece el flujo simplificado de un único binario.")
        Button(onClick = { open.launch(arrayOf("*/*")) }) { Text("Seleccionar binario de código") }
        Text(fileName)
        OutlinedButton(onClick = { openLogo.launch(arrayOf("*/*")) }) { Text("Importar bloque de logo GBA (156 bytes, opcional)") }
        Text(logoName, color = MaterialTheme.colorScheme.secondary)
        OutlinedTextField(value = title, onValueChange = { title = it.take(12); output = null }, label = { Text("Título (máx. 12)") }, singleLine = true)
        OutlinedTextField(value = gameCode, onValueChange = { gameCode = it.take(4).uppercase(); output = null }, label = { Text("Game Code (4)") }, singleLine = true)
        OutlinedTextField(value = maker, onValueChange = { maker = it.take(2).uppercase(); output = null }, label = { Text("Maker Code (2)") }, singleLine = true)
        Button(enabled = input != null, onClick = {
            runCatching {
                val result = GbaLinker.linkRawSections(listOf(GbaSection("code.bin", input!!)), title, gameCode, maker, nintendoLogo = logo)
                output = result.image
                val entry = (result.image[0].toLong() and 255) or ((result.image[1].toLong() and 255) shl 8) or ((result.image[2].toLong() and 255) shl 16) or ((result.image[3].toLong() and 255) shl 24)
                report = "Imagen: ${result.image.size} bytes\nPunto de entrada: 0x%08X".format(0x08000008L + ((entry and 0x00FFFFFFL) shl 2)) + "\nChecksum de cabecera: ${if (GbaRomHeader.hasValidComplement(result.image)) "correcto" else "incorrecto"}\n" + result.sections.joinToString("\n") { "${it.name}: 0x${it.address.toString(16).uppercase()} · ${it.size} bytes · archivo +0x${it.fileOffset.toString(16).uppercase()}" } + "\n" + result.warnings.joinToString("\n")
            }.onFailure { report = "Error: ${it.message}" }
        }) { Text("Construir imagen ROM") }
        if (output != null) OutlinedButton(onClick = { save.launch("game-experimental.gba") }) { Text("Exportar .gba experimental") }
        if (report.isNotBlank()) Text(report, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun AssetStudioScreen() {
    val context = LocalContext.current
    var palette by remember { mutableStateOf<ByteArray?>(null) }
    var tiles by remember { mutableStateOf<ByteArray?>(null) }
    var summary by remember { mutableStateOf("Importa una imagen PNG, JPG o WebP.") }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = context.contentResolver.openInputStream(uri)
                ?: error("No se pudo leer la imagen.")
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "El archivo no contiene una imagen válida." }
            require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= AssetStudio.MAX_PIXELS) {
                "La imagen supera el límite de ${AssetStudio.MAX_PIXELS / 1_000_000} megapíxeles."
            }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("No se pudo decodificar la imagen.")
        }.onSuccess { bitmap ->
            try {
                runCatching { AssetStudio.convertTo4Bpp(bitmap) }
                    .onSuccess { result ->
                        palette = AssetStudio.paletteBytes(result.colors)
                        tiles = result.tiles4bpp
                        summary = "Imagen: ${bitmap.width}×${bitmap.height}\nTiles 8×8: ${result.tileCount}\nColores únicos de origen: ${result.sourceColorCount} · Entradas de paleta: ${result.colors.size}\nPaleta: ${result.colors.joinToString { "0x%04X".format(it) }}\n" + result.warnings.joinToString("\n")
                    }.onFailure { summary = "Error: ${it.message}"; palette = null; tiles = null }
            } finally {
                bitmap.recycle()
            }
        }.onFailure { summary = "Error al importar: ${it.message}"; palette = null; tiles = null }
    }
    val saveTiles = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && tiles != null) runCatching {
            val stream = context.contentResolver.openOutputStream(uri) ?: error("No se pudo abrir el destino de tiles.")
            stream.use { it.write(tiles!!) }
        }.onFailure { summary = "Error al exportar tiles: ${it.message}" }
    }
    val savePalette = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && palette != null) runCatching {
            val stream = context.contentResolver.openOutputStream(uri) ?: error("No se pudo abrir el destino de la paleta.")
            stream.use { it.write(palette!!) }
        }.onFailure { summary = "Error al exportar paleta: ${it.message}" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Asset Studio", style = MaterialTheme.typography.headlineMedium)
        Text("Convierte imágenes en tiles 4bpp de 8×8 y paletas BGR555 para gráficos GBA.")
        Button(onClick = { imagePicker.launch("image/*") }) { Text("Importar imagen") }
        Text(summary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = tiles != null, onClick = { saveTiles.launch("tiles.4bpp") }) { Text("Exportar tiles") }
            OutlinedButton(enabled = palette != null, onClick = { savePalette.launch("palette.pal") }) { Text("Exportar paleta") }
        }
    }
}

@Composable
fun EmulatorScreen() {
    val context = LocalContext.current
    var core by remember { mutableStateOf<GbaEmulatorCore?>(null) }
    var snapshot by remember { mutableStateOf<EmulatorSnapshot?>(null) }
    var status by remember { mutableStateOf("Carga una ROM GBA para iniciar la prueba de CPU.") }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(GbaLinker.MAX_ROM_SIZE) } ?: error("No se pudo leer la ROM.")
        }.onSuccess { data ->
            runCatching { GbaEmulatorCore(data) }.onSuccess {
                core = it; snapshot = it.snapshot(); status = "ROM cargada: ${data.size} bytes"
            }.onFailure { status = "No se pudo iniciar: ${it.message}" }
        }.onFailure { status = "Error: ${it.message}" }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Emulador experimental", style = MaterialTheme.typography.headlineMedium)
        Text("Esta primera etapa es un intérprete de instrucciones ARM/Thumb para depuración paso a paso. Todavía no reproduce vídeo, audio, temporizadores, DMA ni periféricos GBA; no es aún un emulador completo.")
        Button(onClick = { open.launch(arrayOf("*/*")) }) { Text("Abrir ROM GBA") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = core != null, onClick = { snapshot = core?.step(); status = snapshot?.message ?: status }) { Text("Un paso") }
            OutlinedButton(enabled = core != null, onClick = { repeat(100) { snapshot = core?.step() }; status = snapshot?.message ?: status }) { Text("100 pasos") }
        }
        Text(status, color = MaterialTheme.colorScheme.secondary)
        snapshot?.let { state ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("PC: 0x%08X".format(state.pc), fontFamily = FontFamily.Monospace)
                    Text("Modo: ${if (state.thumb) "Thumb" else "ARM"} · Pasos: ${state.steps}")
                    Text("CPSR: 0x%08X".format(state.cpsr), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text("Estado: ${if (state.halted) "Detenido" else "Activo"}")
                    state.registers.forEachIndexed { index, value ->
                        Text("R${index.toString().padStart(2, '0')}: 0x%08X".format(value), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
