package com.sfj3861.gbastudio

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DisassemblerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var label by remember { mutableStateOf("Ninguna ROM seleccionada") }
    var thumb by remember { mutableStateOf(false) }
    var base by remember { mutableStateOf("080000C0") }
    var skipHeader by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf(emptyList<DisassembledInstruction>()) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            loading = true
            scope.launch {
                try {
                    val data = readDocumentBytes(context, uri, GbaLinker.MAX_ROM_SIZE)
                    bytes = data
                    label = uri.lastPathSegment ?: "ROM importada"
                    rows = emptyList()
                } catch (error: Exception) {
                    bytes = null
                    label = "Error al importar: ${error.message}"
                    rows = emptyList()
                } finally {
                    loading = false
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Desensamblador ARM / Thumb", style = MaterialTheme.typography.headlineMedium)
        Text("Inspecciona instrucciones de una ROM GBA. El decodificador cubre un subconjunto de ARM7TDMI; las instrucciones desconocidas se muestran como datos.")
        Button(enabled = !loading, onClick = { open.launch(arrayOf("*/*")) }) {
            Text(if (loading) "Leyendo ROM…" else "Abrir ROM o binario")
        }
        Text(if (loading) "Importando archivo en segundo plano…" else label, color = MaterialTheme.colorScheme.secondary)
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
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
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
        if (uri != null) {
            loading = true
            scope.launch {
                try {
                    input = readDocumentBytes(context, uri, GbaLinker.MAX_ROM_SIZE - GbaLinker.HEADER_SIZE)
                    fileName = uri.lastPathSegment ?: "binary.bin"
                    output = null
                    report = ""
                } catch (error: Exception) {
                    input = null
                    output = null
                    fileName = "Error al importar: ${error.message}"
                } finally {
                    loading = false
                }
            }
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
        val data = output
        if (uri != null && data != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: error("No se pudo abrir el destino de exportación.")
                        stream.use { it.write(data) }
                    }
                    report += "\nROM exportada."
                } catch (error: Exception) {
                    report += "\nError al exportar: ${error.message}"
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Linker / ROM image builder", style = MaterialTheme.typography.headlineMedium)
        Text("Empaqueta un binario raw, genera el salto de entrada ARM y construye los campos de cabecera GBA. El motor interno también admite símbolos y relocaciones, aunque esta pantalla ofrece el flujo simplificado de un único binario.")
        Button(enabled = !loading, onClick = { open.launch(arrayOf("*/*")) }) {
            Text(if (loading) "Leyendo binario…" else "Seleccionar binario de código")
        }
        Text(fileName)
        OutlinedButton(onClick = { openLogo.launch(arrayOf("*/*")) }) { Text("Importar bloque de logo GBA (156 bytes, opcional)") }
        Text(logoName, color = MaterialTheme.colorScheme.secondary)
        OutlinedTextField(value = title, onValueChange = { title = it.take(12); output = null }, label = { Text("Título (máx. 12)") }, singleLine = true)
        OutlinedTextField(value = gameCode, onValueChange = { gameCode = it.take(4).uppercase(); output = null }, label = { Text("Game Code (4)") }, singleLine = true)
        OutlinedTextField(value = maker, onValueChange = { maker = it.take(2).uppercase(); output = null }, label = { Text("Maker Code (2)") }, singleLine = true)
        Button(enabled = input != null && !loading, onClick = {
            val binary = input
            if (binary != null) {
                val gameTitle = title
                val code = gameCode
                val makerCode = maker
                val logoData = logo
                loading = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.Default) {
                            GbaLinker.linkRawSections(
                                listOf(GbaSection("code.bin", binary)),
                                gameTitle,
                                code,
                                makerCode,
                                nintendoLogo = logoData
                            )
                        }
                        output = result.image
                        val entry = (result.image[0].toLong() and 255) or ((result.image[1].toLong() and 255) shl 8) or ((result.image[2].toLong() and 255) shl 16) or ((result.image[3].toLong() and 255) shl 24)
                        report = "Imagen: ${result.image.size} bytes\nPunto de entrada: 0x%08X".format(0x08000008L + ((entry and 0x00FFFFFFL) shl 2)) + "\nChecksum de cabecera: ${if (GbaRomHeader.hasValidComplement(result.image)) "correcto" else "incorrecto"}\n" + result.sections.joinToString("\n") { "${it.name}: 0x${it.address.toString(16).uppercase()} · ${it.size} bytes · archivo +0x${it.fileOffset.toString(16).uppercase()}" } + "\n" + result.warnings.joinToString("\n")
                    } catch (error: Exception) {
                        output = null
                        report = "Error: ${error.message}"
                    } finally {
                        loading = false
                    }
                }
            }
        }) { Text(if (loading) "Procesando…" else "Construir imagen ROM") }
        if (output != null) OutlinedButton(enabled = !loading, onClick = { save.launch("game-experimental.gba") }) { Text("Exportar .gba experimental") }
        if (report.isNotBlank()) Text(report, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
fun AssetStudioScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf<ByteArray?>(null) }
    var tiles by remember { mutableStateOf<ByteArray?>(null) }
    var mapTiles by remember { mutableStateOf<ByteArray?>(null) }
    var tilemap by remember { mutableStateOf<ByteArray?>(null) }
    var assetBundleZip by remember { mutableStateOf<ByteArray?>(null) }
    var convertedAssets by remember { mutableStateOf<GbaPaletteResult?>(null) }
    var paletteBank by remember { mutableStateOf(0) }
    var mapSummary by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var summary by remember { mutableStateOf("Importa una imagen PNG, JPG o WebP.") }

    DisposableEffect(preview) {
        onDispose { preview?.takeUnless { it.isRecycled }?.recycle() }
    }

    fun clearAssets() {
        palette = null
        tiles = null
        convertedAssets = null
        mapTiles = null
        tilemap = null
        assetBundleZip = null
        mapSummary = ""
        preview = null
    }

    fun rebuildMap(result: GbaPaletteResult, bank: Int) {
        loading = true
        scope.launch {
            try {
                val built = withContext(Dispatchers.Default) {
                    val map = GbaTileMapBuilder.build(result, paletteBank = bank)
                    val zip = GbaAssetBundleExporter.createZip(result, map)
                    val pixels = GbaTileMapPreviewRenderer.renderArgbPixels(map, result.colors)
                    val bitmap = Bitmap.createBitmap(
                        map.widthTiles * 8,
                        map.heightTiles * 8,
                        Bitmap.Config.ARGB_8888
                    ).apply {
                        setPixels(pixels, 0, map.widthTiles * 8, 0, 0, map.widthTiles * 8, map.heightTiles * 8)
                    }
                    Triple(map, zip, bitmap)
                }
                mapTiles = built.first.uniqueTiles4bpp
                tilemap = built.first.bytes
                assetBundleZip = built.second
                preview = built.third
                mapSummary = "Vista previa del fondo: ${built.first.widthTiles * 8}×${built.first.heightTiles * 8} px · modo ${built.first.sizeCode} · banco ${built.first.paletteBank} · ${built.first.uniqueTileCount} tiles únicos"
            } catch (error: Exception) {
                mapTiles = null
                tilemap = null
                assetBundleZip = null
                val fallback = withContext(Dispatchers.Default) { AssetStudio.renderPreview(result) }
                preview = fallback
                mapSummary = "Tilemap no generado: ${error.message}. Se muestra la imagen convertida sin mapa."
            } finally {
                loading = false
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !loading) {
            loading = true
            scope.launch {
                var decoded: Bitmap? = null
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, bounds)
                        } ?: error("No se pudo leer la imagen.")
                        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
                            "El archivo no contiene una imagen válida."
                        }
                        require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= AssetStudio.MAX_PIXELS) {
                            "La imagen supera el límite de ${AssetStudio.MAX_PIXELS / 1_000_000} megapíxeles."
                        }
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)
                        } ?: error("No se pudo decodificar la imagen.")
                    }
                    decoded = bitmap
                    val result = withContext(Dispatchers.Default) { AssetStudio.convertTo4Bpp(bitmap) }
                    val mapArtifacts = withContext(Dispatchers.Default) {
                        runCatching {
                            val map = GbaTileMapBuilder.build(result, paletteBank = paletteBank)
                            val zip = GbaAssetBundleExporter.createZip(result, map)
                            val pixels = GbaTileMapPreviewRenderer.renderArgbPixels(map, result.colors)
                            val previewBitmap = Bitmap.createBitmap(
                                map.widthTiles * 8, map.heightTiles * 8, Bitmap.Config.ARGB_8888
                            ).apply {
                                setPixels(pixels, 0, map.widthTiles * 8, 0, 0, map.widthTiles * 8, map.heightTiles * 8)
                            }
                            Triple(map, zip, previewBitmap)
                        }
                    }
                    convertedAssets = result
                    palette = AssetStudio.paletteBytes(result.colors)
                    tiles = result.tiles4bpp
                    if (mapArtifacts.isSuccess) {
                        val (map, zip, bitmapPreview) = mapArtifacts.getOrThrow()
                        mapTiles = map.uniqueTiles4bpp
                        tilemap = map.bytes
                        assetBundleZip = zip
                        preview = bitmapPreview
                        mapSummary = "Vista previa del fondo: ${map.widthTiles * 8}×${map.heightTiles * 8} px · modo ${map.sizeCode} · banco ${map.paletteBank} · ${map.uniqueTileCount} tiles únicos"
                    } else {
                        mapTiles = null
                        tilemap = null
                        assetBundleZip = null
                        preview = withContext(Dispatchers.Default) { AssetStudio.renderPreview(result) }
                        mapSummary = "Tilemap no generado: ${mapArtifacts.exceptionOrNull()?.message}. Se muestra la imagen convertida sin mapa."
                    }
                    summary = "Imagen: ${bitmap.width}×${bitmap.height}\nTiles de origen: ${result.tileCount}\nColores únicos de origen: ${result.sourceColorCount} · Entradas de paleta: ${result.colors.size}\nPaleta: ${result.colors.joinToString { "0x%04X".format(it) }}\n" + result.warnings.joinToString("\n")
                } catch (error: Exception) {
                    clearAssets()
                    summary = "Error al importar: ${error.message}"
                } finally {
                    decoded?.takeUnless { it.isRecycled }?.recycle()
                    loading = false
                }
            }
        }
    }

    fun saveBytes(data: ByteArray?, uri: Uri?, description: String) {
        if (uri == null || data == null) return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                        ?: error("No se pudo abrir el destino de exportación.")
                }
            } catch (error: Exception) {
                summary = "Error al exportar $description: ${error.message}"
            }
        }
    }

    val saveTiles = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        saveBytes(tiles, uri, "tiles")
    }
    val savePalette = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        saveBytes(palette, uri, "paleta")
    }
    val saveMapTiles = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        saveBytes(mapTiles, uri, "tiles deduplicados")
    }
    val saveTilemap = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        saveBytes(tilemap, uri, "tilemap")
    }
    val saveBundle = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        saveBytes(assetBundleZip, uri, "paquete ZIP")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Asset Studio", style = MaterialTheme.typography.headlineMedium)
        Text("Convierte imágenes en tiles 4bpp de 8×8 y paletas BGR555 para gráficos GBA.")
        Button(enabled = !loading, onClick = { imagePicker.launch("image/*") }) {
            Text(if (loading) "Procesando imagen…" else "Importar imagen")
        }
        Text(if (loading) "Decodificación, cuantización y generación de mapas en segundo plano…" else summary)
        Text("Banco de paleta de fondo: $paletteBank")
        Slider(
            value = paletteBank.toFloat(),
            onValueChange = { paletteBank = it.toInt() },
            onValueChangeFinished = { convertedAssets?.let { assets -> rebuildMap(assets, paletteBank) } },
            valueRange = 0f..15f,
            steps = 14,
            enabled = !loading && convertedAssets != null
        )
        if (mapSummary.isNotBlank()) Text(mapSummary, color = MaterialTheme.colorScheme.secondary)
        preview?.let { bitmap ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = "Vista previa del fondo GBA reconstruido a partir del tilemap",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !loading && tiles != null, onClick = { saveTiles.launch("tiles-raw.4bpp") }) { Text("Tiles originales") }
            OutlinedButton(enabled = !loading && palette != null, onClick = { savePalette.launch("palette.pal") }) { Text("Exportar paleta") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !loading && mapTiles != null, onClick = { saveMapTiles.launch("tiles-unique.4bpp") }) { Text("Tiles para mapa") }
            OutlinedButton(enabled = !loading && tilemap != null, onClick = { saveTilemap.launch("background.map") }) { Text("Exportar tilemap") }
        }
        OutlinedButton(
            enabled = !loading && assetBundleZip != null,
            onClick = { saveBundle.launch("gba-assets.zip") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Exportar paquete completo ZIP") }
    }
}

@Composable
fun EmulatorScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var romData by remember { mutableStateOf<ByteArray?>(null) }
    var core by remember { mutableStateOf<GbaEmulatorCore?>(null) }
    var debugger by remember { mutableStateOf<GbaDebuggerSession?>(null) }
    var breakpointAddress by remember { mutableStateOf("080000C0") }
    var memoryAddress by remember { mutableStateOf("02000000") }
    var memoryDump by remember { mutableStateOf("") }
    var videoPreview by remember { mutableStateOf<Bitmap?>(null) }
    var snapshot by remember { mutableStateOf<EmulatorSnapshot?>(null) }
    DisposableEffect(videoPreview) {
        onDispose { videoPreview?.takeUnless { it.isRecycled }?.recycle() }
    }
    var running by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Carga una ROM GBA para iniciar la prueba de CPU.") }

    LaunchedEffect(running, core) {
        val activeDebugger = debugger
        if (running && activeDebugger != null) {
            while (true) {
                val result = withContext(Dispatchers.Default) { activeDebugger.run(250) }
                snapshot = result.cpu
                if (result.cpu.halted || result.stoppedAtBreakpoint) {
                    running = false
                    status = if (result.stoppedAtBreakpoint) "Breakpoint alcanzado en 0x%08X".format(result.cpu.pc) else result.cpu.message
                    break
                }
                delay(16)
            }
        }
    }

    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            running = false
            loading = true
            scope.launch {
                try {
                    val data = readDocumentBytes(context, uri, GbaLinker.MAX_ROM_SIZE)
                    val newCore = withContext(Dispatchers.Default) { GbaEmulatorCore(data) }
                    romData = data
                    core = newCore
                    debugger = GbaDebuggerSession(newCore)
                    snapshot = newCore.snapshot()
                    status = "ROM cargada: ${data.size} bytes"
                } catch (error: Exception) {
                    romData = null
                    core = null
                    snapshot = null
                    status = "Error al importar o iniciar: ${error.message}"
                } finally {
                    loading = false
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Emulador experimental", style = MaterialTheme.typography.headlineMedium)
        Text("Stepper ARM7TDMI parcial para depuración. Puede ejecutar algunas instrucciones aritméticas, saltos y accesos ROM/EWRAM/IWRAM; no emula vídeo, sonido, BIOS ni periféricos GBA.")
        Button(enabled = !loading, onClick = { running = false; open.launch(arrayOf("*/*")) }) {
            Text(if (loading) "Cargando ROM…" else "Abrir ROM GBA")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = core != null && snapshot?.halted == false && !running && !loading,
                onClick = { snapshot = debugger?.step()?.cpu; status = snapshot?.message ?: status }
            ) { Text("Un paso") }
            OutlinedButton(
                enabled = core != null && snapshot?.halted == false && !running && !loading,
                onClick = {
                    repeat(100) {
                        if (snapshot?.halted != true) snapshot = debugger?.step()?.cpu
                    }
                    status = snapshot?.message ?: status
                }
            ) { Text("100 pasos") }
            OutlinedButton(
                enabled = core != null && snapshot?.halted == false && !loading,
                onClick = { running = !running }
            ) { Text(if (running) "Pausar" else "Ejecutar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = breakpointAddress, onValueChange = { breakpointAddress = it.take(8) }, label = { Text("Breakpoint (hex)") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton(enabled = debugger != null, onClick = {
                val address = breakpointAddress.toLongOrNull(16)?.toInt()
                if (address != null) runCatching { debugger?.addBreakpoint(address) }
                    .onSuccess { status = "Breakpoint añadido: 0x%08X".format(address) }
                    .onFailure { status = "Breakpoint inválido: ${it.message}" }
                else status = "Dirección hexadecimal no válida."
            }) { Text("Añadir") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = debugger != null, onClick = { debugger?.clearBreakpoints(); status = "Breakpoints eliminados." }) { Text("Limpiar breakpoints") }
            OutlinedButton(enabled = debugger != null, onClick = { status = debugger?.snapshot()?.trace?.takeLast(8)?.joinToString("\n") ?: "" }) { Text("Ver traza") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = memoryAddress, onValueChange = { memoryAddress = it.take(8) }, label = { Text("Memoria (hex)") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedButton(enabled = debugger != null, onClick = {
                val address = memoryAddress.toLongOrNull(16)?.toInt()
                memoryDump = if (address != null) runCatching { debugger?.readMemoryHex(address, 32) ?: "" }.getOrElse { "Error: ${it.message}" } else "Dirección inválida"
            }) { Text("Leer 32 B") }
        }
        if (memoryDump.isNotBlank()) Text(memoryDump, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        OutlinedButton(enabled = core != null && !loading, onClick = {
            val activeCore = core
            if (activeCore != null) {
                running = false
                val videoState = activeCore.videoMemorySnapshot()
                scope.launch {
                    try {
                        val frame = withContext(Dispatchers.Default) { GbaPpuRenderer.renderMode0(videoState) }
                        val bitmap = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888).apply {
                            setPixels(frame.argbPixels, 0, frame.width, 0, 0, frame.width, frame.height)
                        }
                        videoPreview?.takeUnless { it.isRecycled }?.recycle()
                        videoPreview = bitmap
                        status = frame.warnings.joinToString(" ") .ifBlank { "Render modo 0 actualizado." }
                    } catch (error: Exception) { status = "Error al renderizar: ${error.message}" }
                }
            }
        }) { Text("Renderizar BG modo 0") }
        videoPreview?.let { frame ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Image(frame.asImageBitmap(), contentDescription = "Vista previa PPU GBA 240 por 160",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(12.dp), contentScale = ContentScale.Fit)
            }
        }
        OutlinedButton(
            enabled = romData != null && !loading,
            onClick = {
                val data = romData
                if (data != null) {
                    running = false
                    loading = true
                    scope.launch {
                        try {
                            val newCore = withContext(Dispatchers.Default) { GbaEmulatorCore(data) }
                            core = newCore
                            debugger = GbaDebuggerSession(newCore)
                            snapshot = newCore.snapshot()
                            status = "CPU reiniciada desde el punto de entrada."
                        } catch (error: Exception) {
                            status = "No se pudo reiniciar: ${error.message}"
                        } finally {
                            loading = false
                        }
                    }
                }
            }
        ) { Text("Reiniciar CPU") }

        Text(
            when {
                loading -> "Cargando ROM o preparando CPU en segundo plano…"
                running -> "Ejecución continua: 250 instrucciones por ciclo"
                else -> status
            },
            color = MaterialTheme.colorScheme.secondary
        )
        snapshot?.let { state ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("PC: 0x%08X".format(state.pc), fontFamily = FontFamily.Monospace)
                    Text("Modo: ${if (state.thumb) "Thumb" else "ARM"} · Pasos: ${state.steps}")
                    Text("CPSR: 0x%08X".format(state.cpsr), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Text("Estado: ${if (state.halted) "Detenido" else if (running) "Ejecutando" else "En pausa"}")
                    state.registers.forEachIndexed { index, value ->
                        Text("R${index.toString().padStart(2, '0')}: 0x%08X".format(value), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}


private suspend fun readDocumentBytes(context: Context, uri: Uri, maxBytes: Int): ByteArray =
    withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBoundedBytes(maxBytes) }
            ?: error("No se pudo abrir el archivo.")
    }
