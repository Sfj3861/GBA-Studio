package com.sfj3861.gbastudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AiSettingsScreen(){
    val context=LocalContext.current
    val store= remember{AiSettingsStore(context)}
    var key by remember{mutableStateOf("")}
    var configured by remember{mutableStateOf(store.hasApiKey())}
    var status by remember{mutableStateOf("La clave se guarda cifrada mediante Android Keystore en este dispositivo.")}
    Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("ChatGPT · Configuración segura",style=MaterialTheme.typography.headlineMedium)
        Text("Base local de integración. No se realiza ninguna petición de red todavía y la APK no contiene una clave predeterminada.")
        OutlinedTextField(value=key,onValueChange={key=it},label={Text("OpenAI API key")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
        Button(enabled=key.isNotBlank(),onClick={status=runCatching{store.saveApiKey(key);key="";configured=true;"Clave guardada cifrada en Android Keystore."}.getOrElse{"No se pudo guardar: \${it.message}"}}){Text("Guardar clave cifrada")}
        OutlinedButton(enabled=configured,onClick={store.clearApiKey();configured=false;key="";status="Clave local eliminada."}){Text("Eliminar clave")}
        Text(if(configured)"Hay una clave cifrada guardada (no se muestra)." else "No hay clave configurada.",color=MaterialTheme.colorScheme.secondary)
        Text(status)
        Text("Privacidad: ningún archivo se comparte automáticamente. La futura integración deberá pedir permiso por proyecto y archivo, limitar el contexto y mostrar cada diff antes de aplicar cambios.")
        Text("Seguridad: no compartas tu clave con otras personas. La conexión directa a un proveedor todavía requiere una capa de red y controles de gasto/rate limit.")
    }
}
