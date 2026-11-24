package com.sfj3861.gbastudio

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AudioTrack reports a cumulative, unsigned 32-bit frame count for MODE_STATIC.
 * Comparing this position with the mono sample count lets preview playback finish
 * even when the track remains in PLAYSTATE_PLAYING after its static buffer ends.
 */
internal fun hasStaticPlaybackReachedEnd(playbackHeadPosition: Int, totalFrames: Int): Boolean {
    require(totalFrames >= 0) { "El número total de frames no puede ser negativo." }
    val playedFrames = playbackHeadPosition.toLong() and 0xFFFF_FFFFL
    return playedFrames >= totalFrames.toLong()
}

/** A finishing playback job may update UI state only while it still owns the active track. */
internal fun ownsStaticPlaybackState(activeTrack: Any?, finishingTrack: Any): Boolean =
    activeTrack === finishingTrack

@Composable
fun SpriteAudioToolsScreen() {
    val context= LocalContext.current
    val scope= rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sprites by remember { mutableStateOf<GbaSpriteSheetResult?>(null) }
    var selectedFrame by remember { mutableStateOf(0) }
    var pcm by remember { mutableStateOf<GbaPcmSample?>(null) }
    var frameWidth by remember { mutableStateOf("16") }
    var frameHeight by remember { mutableStateOf("16") }
    var sampleRate by remember { mutableStateOf("16384") }
    var status by remember { mutableStateOf("Importa un spritesheet o un WAV PCM.") }
    var busy by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var audioTrack by remember { mutableStateOf<AudioTrack?>(null) }
    var playbackJob by remember { mutableStateOf<Job?>(null) }

    fun stopPlayback() {
        audioTrack?.let { track -> runCatching { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() } }
        playbackJob?.cancel()
        playbackJob = null
        audioTrack = null
        playing = false
    }

    fun playPreview() {
        val sample = pcm ?: return
        if (playing) { stopPlayback(); return }
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                    .setSampleRate(sample.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(sample.samplesUnsigned8.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            audioTrack = track
            playing = true
            playbackJob = scope.launch {
                try {
                    val completed = withContext(Dispatchers.IO) {
                        val written = track.write(sample.samplesUnsigned8, 0, sample.samplesUnsigned8.size, AudioTrack.WRITE_BLOCKING)
                        check(written == sample.samplesUnsigned8.size) { "No se pudieron cargar todas las muestras de audio." }
                        track.play()
                        withTimeoutOrNull(sample.durationMs + 5_000L) {
                            while (
                                track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                                !hasStaticPlaybackReachedEnd(
                                    track.playbackHeadPosition,
                                    sample.samplesUnsigned8.size
                                )
                            ) {
                                delay(10)
                            }
                            hasStaticPlaybackReachedEnd(
                                track.playbackHeadPosition,
                                sample.samplesUnsigned8.size
                            )
                        } ?: false
                    }
                    if (!completed && ownsStaticPlaybackState(audioTrack, track)) {
                        status = "La reproducción se interrumpió antes de completar la muestra."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (ownsStaticPlaybackState(audioTrack, track)) {
                        status = "Error de reproducción: ${e.message}"
                    }
                } finally {
                    runCatching {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                    }
                    runCatching { track.release() }
                    if (ownsStaticPlaybackState(audioTrack, track)) {
                        audioTrack = null
                        playing = false
                    }
                }
            }
        } catch (e: Exception) {
            playing = false
            status = "No se pudo iniciar la reproducción: ${e.message}"
        }
    }

    fun write(uri:Uri?,bytes:ByteArray?,label:String) {
        if(uri==null || bytes==null)return
        scope.launch { try {
            withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.use{it.write(bytes)}?:error("No se pudo abrir el destino.")}
            status="$label exportado correctamente."
        } catch(e:Exception){status="Error al exportar: ${e.message}"} }
    }
    val openImage=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){busy=true;scope.launch{try{
            val decoded=withContext(Dispatchers.IO){
                val options=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it,null,options)}?:error("No se pudo leer la imagen.")
                require(options.outWidth>0 && options.outHeight>0 && options.outWidth.toLong()*options.outHeight<=AssetStudio.MAX_PIXELS){"Imagen inválida o superior a 4 MP."}
                context.contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}?:error("No se pudo decodificar.")
            }
            bitmap?.takeUnless{it.isRecycled}?.recycle();bitmap=decoded;sprites=null;selectedFrame=0
            status="Spritesheet cargado: ${decoded.width}×${decoded.height}. Configura el tamaño de cada frame."
        }catch(e:Exception){status="Error: ${e.message}"}finally{busy=false}}}
    }
    val openWav=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){busy=true;scope.launch{try{
            val bytes=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.use{it.readBoundedBytes(GbaWavConverter.MAX_INPUT_BYTES)}?:error("No se pudo leer el WAV.")}
            val converted=withContext(Dispatchers.Default){GbaWavConverter.convert(bytes,sampleRate.toIntOrNull()?:16_384)}
            pcm=converted
            status="Audio convertido: ${converted.samplesUnsigned8.size} muestras · ${converted.sampleRate} Hz · ${converted.durationMs} ms."
        }catch(e:Exception){pcm=null;status="Error WAV: ${e.message}"}finally{busy=false}}}
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

    val trackOnDispose = rememberUpdatedState(audioTrack)
    val jobOnDispose = rememberUpdatedState(playbackJob)
    DisposableEffect(bitmap){onDispose{bitmap?.takeUnless{it.isRecycled}?.recycle()}}
    DisposableEffect(Unit) { onDispose { trackOnDispose.value?.let { runCatching { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop() } }; jobOnDispose.value?.cancel() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text("Sprites, animaciones y audio",style=MaterialTheme.typography.headlineMedium)
        Text("Herramientas de conversión para recursos OBJ y audio PCM de GBA. La reproducción en tiempo real aún no forma parte del emulador.")
        Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){
            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Sprite Studio · OBJ/OAM",style=MaterialTheme.typography.titleMedium)
                Button(enabled=!busy,onClick={openImage.launch(arrayOf("image/*"))}){Text("Importar spritesheet")}
                Text(bitmap?.let{"Imagen: ${it.width}×${it.height}"}?:"Ningún spritesheet cargado")
                bitmap?.let { source ->
                    Image(source.asImageBitmap(), contentDescription="Vista previa del spritesheet", contentScale=ContentScale.Fit, modifier=Modifier.fillMaxWidth().heightIn(max=220.dp))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(frameWidth,{frameWidth=it.filter(Char::isDigit).take(2)},label={Text("Ancho frame")},modifier=Modifier.weight(1f),singleLine=true)
                    OutlinedTextField(frameHeight,{frameHeight=it.filter(Char::isDigit).take(2)},label={Text("Alto frame")},modifier=Modifier.weight(1f),singleLine=true)
                }
                Button(enabled=!busy && bitmap!=null,onClick={
                    val source=bitmap
                    if(source!=null){busy=true;scope.launch{try{
                        sprites=withContext(Dispatchers.Default){GbaSpriteStudio.build(source,frameWidth.toInt(),frameHeight.toInt())}
                        val result=sprites!!
                        selectedFrame=0
                        status="Spritesheet dividido en ${result.frames.size} frames; ${result.frames.firstOrNull()?.tileCount?:0} tiles por frame. Paleta compartida."
                    }catch(e:Exception){sprites=null;status="No se pudo procesar el spritesheet: ${e.message}"}finally{busy=false}}}
                }){Text("Generar frames y OAM")}
                sprites?.let{result->
                    Text("${result.frames.size} frames · ${result.frameWidth}×${result.frameHeight} · ${result.paletteBgr555.size} colores")
                    val frame = result.frames.getOrNull(selectedFrame)
                    val framePreview = remember(result, selectedFrame) {
                        frame?.let {
                            val pixels = GbaTileDecoder.decodeArgbPixels(result.frameWidth, result.frameHeight, it.tileData4bpp, result.paletteBgr555)
                            Bitmap.createBitmap(result.frameWidth, result.frameHeight, Bitmap.Config.ARGB_8888).apply {
                                setPixels(pixels, 0, result.frameWidth, 0, 0, result.frameWidth, result.frameHeight)
                            }
                        }
                    }
                    DisposableEffect(framePreview) { onDispose { framePreview?.takeUnless { it.isRecycled }?.recycle() } }
                    framePreview?.let { preview ->
                        Image(preview.asImageBitmap(), contentDescription="Vista previa del frame ${selectedFrame + 1}", contentScale=ContentScale.Fit, modifier=Modifier.fillMaxWidth().heightIn(max=180.dp))
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled=selectedFrame>0,onClick={selectedFrame--}){Text("Anterior")}
                        Text("Frame ${selectedFrame + 1} / ${result.frames.size}", modifier=Modifier.padding(top=12.dp))
                        OutlinedButton(enabled=selectedFrame<result.frames.lastIndex,onClick={selectedFrame++}){Text("Siguiente")}
                    }
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
                pcm?.let{ sample ->
                    Text("${sample.durationMs} ms · ${sample.samplesUnsigned8.size} muestras · ${sample.sourceChannels} canal(es) de origen")
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(enabled=!busy,onClick={playPreview()}) { Text(if(playing) "Detener audio" else "Reproducir vista previa") }
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(enabled=pcm!=null&&!busy,onClick={saveAudioC.launch("audio_samples.c")}){Text("Exportar C")}
                    OutlinedButton(enabled=pcm!=null&&!busy,onClick={saveAudioWav.launch("audio_gba.wav")}){Text("Exportar WAV")}
                }
                Text("Admite PCM sin compresión, mono/estéreo, 8/16 bits. La conversión a 8-bit mono usa interpolación lineal; no incluye compresión ADPCM.")
            }
        }
        Text(if(busy)"Procesando en segundo plano…" else status,color=MaterialTheme.colorScheme.secondary)
    }
}
