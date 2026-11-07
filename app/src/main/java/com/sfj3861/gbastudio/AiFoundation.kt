package com.sfj3861.gbastudio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** API credentials are encrypted with an Android Keystore AES-GCM key; never log or bundle them. */
class AiSettingsStore(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("gba_studio_ai_secure",Context.MODE_PRIVATE)
    private val alias="gba_studio_openai_key_v1"

    fun saveApiKey(value:String) {
        val clean=value.trim()
        require(clean.startsWith("sk-") && clean.length in 20..512) { "La clave no parece una API key válida." }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val encrypted=cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        val encoded=Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+"."+Base64.encodeToString(encrypted,Base64.NO_WRAP)
        check(prefs.edit().putString("openai_key",encoded).commit()) { "No se pudo guardar la configuración cifrada." }
    }

    fun loadApiKey():String? {
        val stored=prefs.getString("openai_key",null)?:return null
        val parts=stored.split(".",limit=2)
        require(parts.size==2) { "Almacenamiento cifrado dañado." }
        val iv=Base64.decode(parts[0],Base64.NO_WRAP)
        val ciphertext=Base64.decode(parts[1],Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
        return String(cipher.doFinal(ciphertext),Charsets.UTF_8)
    }

    fun hasApiKey()=prefs.contains("openai_key")
    fun clearApiKey(){prefs.edit().remove("openai_key").commit()}

    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
}

data class AiProjectContext(val projectId:String,val includedFiles:Map<String,String>,val totalCharacters:Int)

object AiProjectContextBuilder {
    const val MAX_FILE_CHARS=64_000
    const val MAX_TOTAL_CHARS=256_000
    private val forbidden=setOf(".env",".env.local","secrets.json","credentials.json","google-services.json","keystore.properties")

    /** Only explicitly selected, ordinary source/document files are included. */
    fun build(project:GbaProject,repository:ProjectRepository,selectedPaths:List<String>):AiProjectContext {
        require(selectedPaths.size<=64) { "Selecciona como máximo 64 archivos." }
        val included=linkedMapOf<String,String>()
        var total=0
        selectedPaths.distinct().forEach { path ->
            val leaf=path.substringAfterLast('/').lowercase()
            require(leaf !in forbidden && !leaf.endsWith(".keystore") && !leaf.endsWith(".jks") && !leaf.endsWith(".pem")) {
                "El archivo $path está excluido por seguridad."
            }
            val content=repository.readText(project,path)
            require(content.length<=MAX_FILE_CHARS) { "El archivo $path supera el límite de contexto." }
            require(total+content.length<=MAX_TOTAL_CHARS) { "El contexto supera 256000 caracteres." }
            included[path]=content;total+=content.length
        }
        return AiProjectContext(project.id,included,total)
    }
}

data class AiChangeProposal(val relativePath:String,val replacement:String,val summary:String,val approved:Boolean=false)
object AiChangeGate {
    /** A proposed change is never applied unless the user explicitly approves it. */
    fun apply(proposal:AiChangeProposal,project:GbaProject,repository:ProjectRepository) {
        require(proposal.approved) { "El cambio requiere aprobación explícita." }
        require(proposal.summary.isNotBlank() && proposal.replacement.length<=2_000_000) { "Propuesta no válida." }
        repository.writeText(project,proposal.relativePath,proposal.replacement)
    }
}
