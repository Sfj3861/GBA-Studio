package com.sfj3861.gbastudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpriteAudioToolsScreen() {
    val context= LocalContext.current
    val scope= rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sprites by remember { mutableStateOf<GbaSpriteSheetResult?>(null) }
    var pcm by remember { mutableStateOf<GbaPcmSample?>(null) }
    var frameWidth by remember { mutableStateOf("16") }
    var frameHeight by remember { mutableStateOf("16") }
    var sampleRate by remember { mutableStateOf("16384") }
    var status by remember { mutableStateOf("Importa un spritesheet o un WAV PCM.") }
    var busy by remember { mutableStateOf(false) }

    fun write(uri:Uri?,bytes:ByteArray?,label:String) {
        if(uri==null || bytes==null)return
        scope.launch { try {
            withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(bytes)}?:error("No se pudo abrir el destino.")}
            status="$label exportado correctamente."
        } catch(e:Exception){status="Error al exportar: \${e.message}"} }
    }
    val openImage=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){busy=true;scope.launch{try{
            val decoded=withContext(Dispatchers.IO){
                val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}?:error("No se pudo leer la imagen.")
                require(options.outWidth>0 && options.outHeight>0 && options.outWidth.toLong()*options.outHeight<=AssetStudio.MAX_PIXELS){"Imagen inválida o superior a 4 MP."}
                context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}?:error("No se pudo decodificar.")
            }
            bitmap?.takeUnless{it.isRecycled}?.recycle();bitmap=decoded;sprites=null
            status="Spritesheet cargado: \${decoded.width}×\${decoded.height}. Configura el tamaño de cada frame."
        }catch(e:Exception){status="Error: \${e.message}"}finally{busy=false}}}
    }
    val openWav=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){busy=true;scope.launch{try{
            val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{it.readBoundedBytes(GbaWavConverter.MAX_INPUT_BYTES)}?:error("No se pudo leer el WAV.")}
            val converted=withContext(Dispatchers.Default){GbaWavConverter.convert(bytes,sampleRate.toIntOrNull()?:16_384)}
            pcm=converted
            status="Audio convertido: \${converted.samplesUnsigned8.size} muestras · \${converted.sampleRate} Hz · \${converted.durationMs} ms."
        }catch(e:Exception){pcm=null;status="Error WAV: \${e.message}"}finally{busy=false}}}
    }
    val saveSprite=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-c")){uri->
        write(uri,sprites?.let{GbaSpriteStudio.animationCSource(it).toByteArray(Charsets.UTF_8)},"Código C de sprites")
    }
    val saveAudioC=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-c")){uri->
        write(uri,pcm?.let{GbaWavConverter.cSource(it).toByteArray(Charsets.UTF_8)},"Código C de audio")
    }
    val saveAudioWav=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")){uri->
        write(uri,pcm?.let{GbaWavConverter.wavBytes(it)},"WAV mono 8-bit")
    }

    DisposableEffect(bitmap){onDispose{bitmap?.takeUnless{it.isRecycled}?.recycle()}}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Sprites, animaciones y audio",style=MaterialTheme.typography.headlineMedium)
        Text("Herramientas de conversión para recursos OBJ y audio PCM de GBA. La reproducción en tiempo real aún no forma parte del emulador.")
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Sprite Studio · OBJ/OAM",style=MaterialTheme.typography.titleMedium)
                Button(enabled=!busy,onClick={openImage.launch(arrayOf("image/*"))}){Text("Importar spritesheet")}
                Text(bitmap?.let{"Imagen: \${it.width}×\${it.height}"}?:"Ningún spritesheet cargado")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(frameWidth,{frameWidth=it.filter(Char::isDigit).take(2)},label={Text("Ancho frame")},modifier=Modifier.weight(1f),singleLine=true)
                    OutlinedTextField(frameHeight,{frameHeight=it.filter(Char::isDigit).take(2)},label={Text("Alto frame")},modifier=Modifier.weight(1f),singleLine=true)
                }
                Button(enabled=!busy && bitmap!=null,onClick={
                    val source=bitmap
                    if(source!=null){busy=true;scope.launch{try{
                        sprites=withContext(Dispatchers.Default){GbaSpriteStudio.build(source,frameWidth.toInt(),frameHeight.toInt())}
                        val result=sprites!!
                        status="Spritesheet dividido en \${result.frames.size} frames; \${result.frames.firstOrNull()?.tileCount?:0} tiles por frame. Paleta compartida."
                    }catch(e:Exception){sprites=null;status="No se pudo procesar el spritesheet: \${e.message}"}finally{busy=false}}}
                }){Text("Generar frames y OAM")}
                sprites?.let{result->
                    Text("\${result.frames.size} frames · \${result.frameWidth}×\${result.frameHeight} · \${result.paletteBgr555.size} colores")
                    result.warnings.forEach{Text("Aviso: $it",color=MaterialTheme.colorScheme.tertiary)}
                    OutlinedButton(enabled=!busy,onClick={saveSprite.launch("sprite_animation.c")}){Text("Exportar animación C + OAM")}
                }
            }
        }
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Audio Studio · WAV PCM",style=MaterialTheme.typography.titleMedium)
                OutlinedTextField(sampleRate,{sampleRate=it.filter(Char::isDigit).take(5)},label={Text("Frecuencia GBA (Hz)")},singleLine=true)
                Button(enabled=!busy,onClick={openWav.launch(arrayOf("audio/wav","audio/x-wav","application/octet-stream"))}){Text("Importar WAV PCM")}
                pcm?.let{Text("\${it.durationMs} ms · \${it.samplesUnsigned8.size} muestras · \${it.sourceChannels} canal(es) de origen")}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(enabled=pcm!=null&&!busy,onClick={saveAudioC.launch("audio_samples.c")}){Text("Exportar C")}
                    OutlinedButton(enabled=pcm!=null&&!busy,onClick={saveAudioWav.launch("audio_gba.wav")}){Text("Exportar WAV")}
                }
                Text("Admite PCM sin compresión, mono/estéreo, 8/16 bits. La conversión a 8-bit mono usa remuestreo por vecino más próximo; no incluye compresión ADPCM.")
            }
        }
        Text(if(busy)"Procesando en segundo plano…" else status,color=MaterialTheme.colorScheme.secondary)
    }
}
