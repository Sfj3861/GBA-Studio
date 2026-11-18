package com.sfj3861.gbastudio

data class AiProjectContext(val projectId:String,val includedFiles:Map<String,String>,val totalCharacters:Int)

object AiProjectContextBuilder {
    const val MAX_FILE_CHARS=64_000
    const val MAX_TOTAL_CHARS=256_000
    private val forbiddenNames=setOf(
        "secrets","secret","secrets.json","secrets.yaml","secrets.yml","credentials","credentials.json",
        "credentials.yaml","credentials.yml","google-services.json","keystore.properties",
        "service-account.json","service_account.json","token.json",".gitconfig","id_rsa","id_ed25519",
        ".npmrc",".netrc",".pypirc",".envrc",".dockerconfigjson",".git-credentials","git-credentials"
    )
    private val forbiddenDirectories=setOf("secrets","credentials","private-keys","private_keys",".aws",".azure",".ssh",".gnupg",".git")
    private val forbiddenSuffixes=listOf(".keystore",".jks",".pem",".p12",".pfx",".key",".p8",".der",".crt",".cer",".asc",".gpg",".secret",".credentials")
    // Keep the future AI context limited to user-selected text/source formats, never binary assets or ROMs.
    private val allowedContextExtensions=setOf(
        "c","h","cc","cpp","cxx","hh","hpp","hxx","s","asm","inc",
        "txt","md","markdown","json","xml","yaml","yml","toml","ini","cfg","conf",
        "properties","gradle","kts","ld","cmake","mk","make","sh","gitignore","gitattributes"
    )
    private val allowedContextFileNames=setOf("readme","license","makefile","cmakelists.txt")
    private val extraHeaderAssignment = Regex("""(?im)([A-Za-z0-9_.-]*\bextra[_-]?header\b(?:[ \t]*["']?[ \t]*[:=][ \t]*|[ \t]+))(?:(?!\\\r?\n)[^\r\n])*(?:\\\r?\n[ \t]*(?:(?!\\\r?\n)[^\r\n])*)*""")
    private val assignmentSecret=Regex("""(?i)([A-Za-z0-9_.-]*(?:api[_-]?key|access[_-]?key(?:[_-]?id)?|token|password|secret|private[_-]?key|authorization|extra[_-]?header))(\s*["']?\s*[:=]\s*)(?:(?:Bearer|Basic)\s+)?(?:"[^"]*"|'[^']*'|[^\s,;}\]]+)""")
    private val providerKey=Regex("""\bsk-(?:proj-)?[A-Za-z0-9_-]{20,}\b""")
    // Catch common provider tokens even when copied into source text without a secret-like variable name.
    private val knownCredentialToken=Regex("""\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|xox[baprs]-[A-Za-z0-9-]{10,}|AIza[0-9A-Za-z_-]{35})\b""")

    internal fun isSupportedContextPath(path:String):Boolean {
        val name=path.substringAfterLast('/').lowercase()
        if(name in allowedContextFileNames) return true
        val extension=name.substringAfterLast('.', "")
        return extension in allowedContextExtensions
    }

    internal fun isCredentialPath(path:String):Boolean {
        val segments=path.replace('\\','/').split('/')
        val names=segments.map{it.lowercase()}
        return names.any { name ->
            name in forbiddenNames || name==".env" || name.startsWith(".env.") ||
                name.contains(".env.") || name.endsWith(".env") ||
                name.startsWith("secret.") || name.startsWith("secrets.") ||
                name.startsWith("credential.") || name.startsWith("credentials.") ||
                forbiddenSuffixes.any(name::endsWith)
        } || names.dropLast(1).any { it in forbiddenDirectories }
    }

    /** Resolve aliases before applying credential exclusions so symlinks cannot bypass the filter. */
    internal fun canonicalProjectRelativePath(projectDirectory:File,path:String):String {
        require(path.isNotBlank() && !path.startsWith("/") && !path.contains('\\')) {
            "Ruta de contexto no válida."
        }
        val canonicalRoot=projectDirectory.canonicalFile
        val canonicalFile=File(canonicalRoot,path).canonicalFile
        require(canonicalFile.path.startsWith(canonicalRoot.path+File.separator)) {
            "La ruta de contexto sale de la carpeta del proyecto."
        }
        return canonicalFile.relativeTo(canonicalRoot).invariantSeparatorsPath
    }

    private fun redactSecrets(text:String):String {
        val withoutGitHeaders=extraHeaderAssignment.replace(text) { match ->
            match.groupValues[1]+"\"[REDACTED]\""
        }
        val redactedAssignments=assignmentSecret.replace(withoutGitHeaders) { match ->
            match.groupValues[1]+match.groupValues[2]+"\"[REDACTED]\""
        }
        val redactedKnownTokens=knownCredentialToken.replace(redactedAssignments) { "[REDACTED_TOKEN]" }
        return providerKey.replace(redactedKnownTokens) { "[REDACTED_API_KEY]" }
    }

    internal fun sanitizeForSharing(text:String)=redactSecrets(text)

    /** Only explicitly selected, ordinary source/document files are included. */
    fun build(project:GbaProject,repository:ProjectRepository,selectedPaths:List<String>):AiProjectContext {
        require(selectedPaths.size<=64) { "Selecciona como máximo 64 archivos." }
        val included=linkedMapOf<String,String>()
        var total=0
        selectedPaths.distinct().forEach { path ->
            val canonicalPath=canonicalProjectRelativePath(project.directory,path)
            require(isSupportedContextPath(path) && isSupportedContextPath(canonicalPath)) {
                "Solo se pueden incluir archivos de texto y código compatibles con el contexto."
            }
            require(!isCredentialPath(path) && !isCredentialPath(canonicalPath)) {
                "El archivo $path está excluido por seguridad."
            }
            val raw=repository.readText(project,canonicalPath)
            require('\u0000' !in raw) { "El archivo $path parece contener datos binarios." }
            require(raw.length<=MAX_FILE_CHARS) { "El archivo $path supera el límite de contexto." }
            val content=redactSecrets(raw)
            require(total+content.length<=MAX_TOTAL_CHARS) { "El contexto supera 256000 caracteres." }
            included[canonicalPath]=content;total+=content.length
        }
        return AiProjectContext(project.id,included,total)
    }
}

data class AiChangeProposal(val relativePath:String,val replacement:String,val summary:String,val approved:Boolean=false)
object AiChangeGate {
    /**
     * Resolve aliases before validating a proposed write. An approved proposal must not
     * target credentials through either its displayed path or a symlink destination.
     */
    internal fun validatedTargetPath(projectDirectory:File,relativePath:String):String {
        val canonicalPath=AiProjectContextBuilder.canonicalProjectRelativePath(projectDirectory,relativePath)
        require(!AiProjectContextBuilder.isCredentialPath(relativePath) &&
            !AiProjectContextBuilder.isCredentialPath(canonicalPath)) {
            "Los archivos de credenciales están excluidos de las propuestas de cambio."
        }
        return canonicalPath
    }

    /** A proposed change is never applied unless the user explicitly approves it. */
    fun apply(proposal:AiChangeProposal,project:GbaProject,repository:ProjectRepository) {
        require(proposal.approved) { "El cambio requiere aprobación explícita." }
        require(proposal.summary.isNotBlank() && proposal.replacement.length<=2_000_000) { "Propuesta no válida." }
        val safePath=validatedTargetPath(project.directory,proposal.relativePath)
        repository.writeText(project,safePath,proposal.replacement)
    }
}
