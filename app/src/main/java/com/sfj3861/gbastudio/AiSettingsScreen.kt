package com.sfj3861.gbastudio

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val AI_BACKEND_PREFS = "gba_studio_ai_backend"
private const val AI_BACKEND_URL = "base_url"

@Composable
fun AiSettingsScreen() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(AI_BACKEND_PREFS, android.content.Context.MODE_PRIVATE)
    }
    var endpoint by remember { mutableStateOf(preferences.getString(AI_BACKEND_URL, "") ?: "") }
    var status by remember {
        mutableStateOf("Configura la dirección de tu backend. La conexión API todavía no está implementada.")
    }
    var saved by remember { mutableStateOf(endpoint.isNotBlank()) }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("IA · Conexión", style = MaterialTheme.typography.headlineMedium)
        Text(
            "GBA Studio se conectará a OpenAI a través de un backend propio. La clave de OpenAI debe permanecer en el servidor y nunca dentro de la APK.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = endpoint,
            onValueChange = { endpoint = it },
            label = { Text("URL del backend") },
            placeholder = { Text("https://tu-servidor.example") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            val clean = endpoint.trim().trimEnd('/')
            val parsed = runCatching { Uri.parse(clean) }.getOrNull()
            val host = parsed?.host?.lowercase()
            val secureRemote = parsed?.scheme == "https" && !host.isNullOrBlank()
            val localDevelopment = parsed?.scheme == "http" &&
                host in setOf("localhost", "127.0.0.1", "10.0.2.2")
            val safe = (secureRemote || localDevelopment) &&
                parsed?.userInfo == null && parsed?.query == null && parsed?.fragment == null
            if (!safe) {
                status = "Introduce una URL HTTPS válida. HTTP solo se permite para localhost durante desarrollo."
            } else {
                preferences.edit().putString(AI_BACKEND_URL, clean).apply()
                endpoint = clean
                saved = true
                status = "Dirección guardada. La comunicación se activará cuando se integre el cliente del backend."
            }
        }, enabled = endpoint.isNotBlank()) {
            Text("Guardar dirección")
        }
        OutlinedButton(
            enabled = saved,
            onClick = {
                preferences.edit().remove(AI_BACKEND_URL).apply()
                endpoint = ""
                saved = false
                status = "Dirección del backend eliminada."
            }
        ) {
            Text("Eliminar configuración")
        }
        Text(
            if (saved) "Backend configurado: $endpoint" else "Backend sin configurar.",
            color = MaterialTheme.colorScheme.secondary
        )
        Text(status)
        Text(
            "Privacidad y control: los archivos del proyecto no se compartirán automáticamente. Cada contexto y cada operación local deberán seleccionarse y aprobarse manualmente."
        )
        Text(
            "La pantalla todavía no envía peticiones. No introduzcas aquí una clave API: este campo solo admite la dirección del servidor."
        )
    }
}
