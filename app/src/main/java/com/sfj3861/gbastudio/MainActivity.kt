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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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

private enum class Destination(val label: String, val marker: String) {
    HOME("Inicio", "⌂"),
    CODE("Code Studio", "</>"),
    PORTING("Portabilidad", "⇄"),
    ROM("ROM Analyzer", "▤"),
    ASSETS("Recursos", "▦"),
    TUTORIALS("Tutoriales", "?"),
    AI("ChatGPT", "✦")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GbaStudioTheme { GbaStudioApp() } }
    }
}

@Composable
private fun GbaStudioTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF8AB4F8),
        secondary = Color(0xFF80CBC4),
        background = Color(0xFF101318),
        surface = Color(0xFF191E25),
        surfaceVariant = Color(0xFF252C36)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun GbaStudioApp() {
    var selected by rememberSaveable { mutableStateOf(Destination.HOME.name) }
    val destination = Destination.valueOf(selected)

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        val isTablet = maxWidth >= 840.dp

        if (isTablet) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    header = {
                        Text(
                            "GBA",
                            modifier = Modifier.padding(vertical = 20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp
                        )
                    }
                ) {
                    Destination.values().forEach { item ->
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
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            DestinationContent(destination) { selected = it.name }
                        }
                        if (destination != Destination.AI) {
                            AiCompanionPanel(Modifier.width(290.dp).fillMaxSize())
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                AppHeader()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    DestinationContent(destination) { selected = it.name }
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(
                        Destination.HOME, Destination.CODE, Destination.ROM,
                        Destination.TUTORIALS, Destination.AI
                    ).forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { selected = item.name },
                            icon = { Text(item.marker) },
                            label = { Text(item.label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("GBA STUDIO", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(
                    "Game Boy Advance Development",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "LOCAL MODE",
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
        Destination.TUTORIALS -> TutorialScreen()
        Destination.AI -> AiScreen()
        Destination.ROM -> RomAnalyzerScreen()
        else -> ModulePlaceholder(destination)
    }
}

@Composable
private fun HomeScreen(navigate: (Destination) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            "Tu próximo juego empieza aquí",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Un entorno de desarrollo de GBA pensado para tablets. Esta primera versión prepara la base Android y las herramientas nativas.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "PRIMEROS PASOS",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Aprende a orientarte por GBA Studio",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text("El tutorial explica cómo se organizarán los proyectos, el compilador y el análisis de ROMs.")
                Button(onClick = { navigate(Destination.TUTORIALS) }) {
                    Text("Abrir tutorial")
                }
            }
        }
        Text("MÓDULOS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val modules = listOf(
            Triple(Destination.CODE, "Code Studio", "Editor C/C++ y futura compilación ARM."),
            Triple(Destination.PORTING, "Portabilidad", "Preparación de proyectos para GBA."),
            Triple(Destination.ROM, "ROM Analyzer", "Inspección de cabeceras y estructura ROM."),
            Triple(Destination.ASSETS, "Recursos", "Sprites, tiles, paletas y audio.")
        )
        modules.chunked(2).forEach { moduleRow ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                moduleRow.forEach { (target, title, description) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, fontWeight = FontWeight.Bold)
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = { navigate(target) }) { Text("Explorar") }
                        }
                    }
                }
                if (moduleRow.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Estado del entorno", fontWeight = FontWeight.Bold)
                StatusLine("Interfaz Android", "Inicial")
                StatusLine("Puente nativo C++", "Inicial")
                StatusLine("Compilador GBA", "Pendiente")
                StatusLine("Emulador integrado", "Pendiente")
                StatusLine("Asistente ChatGPT", "Conexión pendiente")
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

@Composable
private fun TutorialScreen() {
    var completed by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Tutorial de inicio", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Una guía breve para conocer el proyecto. Los tutoriales prácticos se ampliarán a medida que cada herramienta esté implementada.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TutorialStep("1", "Conoce los módulos", "Code Studio será el espacio para escribir C/C++. ROM Analyzer servirá para inspeccionar ROMs y Asset Studio gestionará recursos.")
        TutorialStep("2", "Crea un proyecto", "Los proyectos contendrán código fuente, recursos, configuración de compilación y documentación. El gestor de proyectos llegará en la siguiente etapa.")
        TutorialStep("3", "Compila para GBA", "El objetivo es integrar un toolchain ARM local que genere archivos .gba sin servicios externos.")
        TutorialStep("4", "Prueba y analiza", "Más adelante podrás ejecutar ROMs con el emulador integrado e inspeccionar resultados con herramientas de depuración.")
        Button(onClick = { completed = !completed }) {
            Text(if (completed) "Marcar como no leído" else "Marcar introducción como leída")
        }
        Text(
            if (completed) "Introducción completada en esta sesión." else "Puedes volver a esta guía cuando quieras.",
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun TutorialStep(number: String, title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(number, color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ModulePlaceholder(destination: Destination) {
    val explanation = when (destination) {
        Destination.CODE -> "Aquí se integrarán el editor de código, la gestión de proyectos y la compilación ARM. Todavía no se ejecuta una compilación."
        Destination.PORTING -> "Aquí se prepararán los análisis de compatibilidad y las herramientas para adaptar proyectos a las limitaciones de GBA."
        Destination.ROM -> "Aquí se incorporarán la importación de ROMs, el visor hexadecimal, el análisis de cabecera y el desensamblado."
        Destination.ASSETS -> "Aquí se incorporarán editores de sprites, tilesets, mapas, paletas y conversión de audio."
        else -> "Este módulo se implementará en una fase posterior."
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(destination.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Módulo planificado", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        Text(explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Text(
                "Esta pantalla es una base de navegación; no representa todavía una herramienta funcional.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun AiCompanionPanel(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("✦ GBA Studio AI", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text("Asistente opcional", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
            Text(
                "La conexión con ChatGPT se añadirá mediante una integración segura. La aplicación no incluirá claves API dentro de la APK.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AiScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("GBA Studio AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Asistente de desarrollo con ChatGPT", color = MaterialTheme.colorScheme.secondary)
        Text("La integración permitirá consultar código, explicar errores y preparar cambios revisables en el proyecto.")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conexión aún no configurada", fontWeight = FontWeight.Bold)
                Text("Primero construiremos el entorno local. Después añadiremos la conexión API, el control de permisos y la aprobación de acciones.")
            }
        }
    }
}
