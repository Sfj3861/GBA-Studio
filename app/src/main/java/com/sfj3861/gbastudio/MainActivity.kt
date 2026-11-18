package com.sfj3861.gbastudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

private enum class Destination(val label: String, val marker: String) {
    HOME("Inicio", "⌂"),
    PROJECTS("Workspace", "▣"),
    CODE("Code Studio", "</>"),
    ASSETS("Asset Studio", "▦"),
    SPRITE_AUDIO("Sprite / Audio", "♫"),
    ROM("ROM Analyzer", "▤"),
    AI("IA · Conexión", "✦"),
    TOOLS("Otras herramientas", "⋯"),
    PORTING("Portabilidad", "⇄"),
    DISASSEMBLER("Desensamblador", "⌘"),
    LINKER("Linker experimental", "⇧"),
    DIAGNOSTICS("Diagnósticos", "✓"),
    EMULATOR("CPU experimental", "▶")
}

private val primaryDestinations = listOf(
    Destination.HOME, Destination.PROJECTS, Destination.CODE, Destination.ASSETS,
    Destination.SPRITE_AUDIO, Destination.ROM, Destination.AI, Destination.TOOLS
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GbaStudioTheme { GbaStudioApp() } }
    }
}

@Composable
private fun GbaStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8AB4F8),
            secondary = Color(0xFF80CBC4),
            background = Color(0xFF101318),
            surface = Color(0xFF191E25),
            surfaceVariant = Color(0xFF252C36)
        ),
        content = content
    )
}

@Composable
private fun GbaStudioApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.HOME.name) }
    val destination = Destination.valueOf(selected)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val isTablet = maxWidth >= 840.dp
        if (isTablet) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Text(
                            "GBA",
                            Modifier.padding(vertical = 20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                    }
                ) {
                    primaryDestinations.forEach { item ->
                        NavigationRailItem(
                            selected = destination == item,
                            onClick = { selected = item.name },
                            icon = { Text(item.marker, fontWeight = FontWeight.Bold) },
                            label = { Text(item.label) }
                        )
                    }
                }
                Column(Modifier.weight(1f).fillMaxSize()) {
                    AppHeader()
                    DestinationContent(destination) { selected = it.name }
                }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(
                            "GBA STUDIO",
                            Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        primaryDestinations.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                icon = { Text(item.marker) },
                                selected = destination == item,
                                onClick = {
                                    selected = item.name
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                        Text(
                            "Herramientas avanzadas",
                            Modifier.padding(start = 22.dp, top = 20.dp, bottom = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium
                        )
                        listOf(
                            Destination.PORTING, Destination.DISASSEMBLER, Destination.LINKER,
                            Destination.DIAGNOSTICS, Destination.EMULATOR
                        ).forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                selected = destination == item,
                                onClick = {
                                    selected = item.name
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                        }
                    }
                }
            ) {
                Column(Modifier.fillMaxSize()) {
                    AppHeader(onMenu = { scope.launch { drawerState.open() } })
                    DestinationContent(destination) { selected = it.name }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(onMenu: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onMenu != null) {
                TextButton(onClick = onMenu) { Text("☰  Menú") }
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text("GBA STUDIO", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(
                    "Desarrollo para Game Boy Advance",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "LOCAL",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DestinationContent(destination: Destination, navigate: (Destination) -> Unit) {
    when (destination) {
        Destination.HOME -> HomeScreen(navigate)
        Destination.PROJECTS -> ProjectManagerScreen(ProjectRepository(LocalContext.current))
        Destination.CODE -> ProjectManagerScreen(
            ProjectRepository(LocalContext.current),
            "Code Studio · Selecciona un proyecto"
        )
        Destination.ROM -> RomAnalyzerScreen()
        Destination.ASSETS -> AssetStudioScreen()
        Destination.SPRITE_AUDIO -> SpriteAudioToolsScreen()
        Destination.AI -> AiSettingsScreen()
        Destination.TOOLS -> ToolsHubScreen(navigate)
        Destination.PORTING -> PortingScreen()
        Destination.DISASSEMBLER -> DisassemblerScreen()
        Destination.LINKER -> LinkerScreen()
        Destination.DIAGNOSTICS -> DiagnosticsScreen()
        Destination.EMULATOR -> EmulatorScreen()
    }
}

@Composable
private fun HomeScreen(navigate: (Destination) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Tu próximo juego empieza aquí",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Un espacio de trabajo local para crear juegos de Game Boy Advance. Abre un proyecto o entra directamente en una herramienta.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("EMPEZAR", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Proyectos y código", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Crea un proyecto local, organiza sus archivos y edita el código fuente.")
                Button(onClick = { navigate(Destination.PROJECTS) }) { Text("Abrir Workspace") }
            }
        }
        Text("HERRAMIENTAS PRINCIPALES", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val modules = listOf(
            Triple(Destination.CODE, "Code Studio", "Edita archivos C/C++ del proyecto."),
            Triple(Destination.ASSETS, "Asset Studio", "Convierte imágenes en tiles y paletas GBA."),
            Triple(Destination.SPRITE_AUDIO, "Sprite / Audio Studio", "Prepara sprites OBJ y audio PCM."),
            Triple(Destination.ROM, "ROM Analyzer", "Inspecciona cabeceras y datos de ROM.")
        )
        modules.forEach { (target, title, description) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, fontWeight = FontWeight.Bold)
                        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { navigate(target) }) { Text("Abrir") }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Estado real del proyecto", fontWeight = FontWeight.Bold)
                StatusLine("Editor y proyectos", "Disponible")
                StatusLine("Conversión de gráficos", "Disponible")
                StatusLine("Conversión de audio", "Disponible")
                StatusLine("Compilador GBA completo", "Pendiente")
                StatusLine("ChatGPT API", "Pendiente de backend")
                StatusLine("Emulador completo", "No implementado")
            }
        }
    }
}

@Composable
private fun ToolsHubScreen(navigate: (Destination) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Herramientas avanzadas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Estas utilidades complementan el flujo principal. Las funciones experimentales se identifican claramente.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val tools = listOf(
            Triple(Destination.DIAGNOSTICS, "Diagnósticos C/C++", "Comprobaciones rápidas basadas en heurísticas."),
            Triple(Destination.PORTING, "Análisis de portabilidad", "Detecta APIs y patrones habituales no portables."),
            Triple(Destination.DISASSEMBLER, "Desensamblador ARM/Thumb", "Decodificación parcial de instrucciones."),
            Triple(Destination.LINKER, "Linker / ROM builder", "Generador experimental de imágenes ROM desde secciones raw."),
            Triple(Destination.EMULATOR, "CPU y depuración experimental", "Stepper parcial ARM/Thumb y memoria limitada.")
        )
        tools.forEach { (target, title, description) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { navigate(target) }) { Text("Abrir herramienta") }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, status: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(status, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
    }
}
