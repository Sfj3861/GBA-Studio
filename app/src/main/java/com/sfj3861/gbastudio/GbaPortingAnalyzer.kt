package com.sfj3861.gbastudio

data class PortingFinding(val severity:String,val file:String,val message:String)
data class PortingReport(val filesScanned:Int,val sourceBytes:Long,val findings:List<PortingFinding>)

object GbaPortingAnalyzer {
    private val sourceExtensions=setOf("c","h","cc","cpp","hpp")
    fun analyzeSource(path:String,source:String):List<PortingFinding> {
        val out=mutableListOf<PortingFinding>()
        val rules=listOf(
            Regex("""#\s*include\s*[<"](?:windows\.h|SDL(?:2)?/[^">]+|unistd\.h)[">]""",RegexOption.IGNORE_CASE) to "Include de escritorio/host no disponible directamente en GBA.",
            Regex("""\b(?:fopen|fread|fwrite|std::filesystem|CreateFile)\s*\(""") to "Acceso a archivos de host: GBA no ofrece un sistema de archivos estándar.",
            Regex("""\b(?:malloc|calloc|realloc)\s*\(""") to "Asignación dinámica: revisa el tamaño del heap y la estrategia de memoria.",
            Regex("""\b(?:double|long\s+long|uint64_t|int64_t)\b""") to "Tipo de 64 bits o doble precisión: puede aumentar mucho el coste en ARM7TDMI.",
            Regex("""\b(?:printf|fprintf|std::cout)\b""") to "Salida de consola: necesitarás sustituirla por depuración o una interfaz propia."
        )
        rules.forEach{(regex,message)->if(regex.containsMatchIn(source))out+=PortingFinding("WARNING",path,message)}
        if(source.contains("float") || source.contains("double"))out+=PortingFinding("INFO",path,"Punto flotante: considera rendimiento y soporte de runtime.")
        if(source.toByteArray(Charsets.UTF_8).size>256*1024)out+=PortingFinding("WARNING",path,"Archivo fuente grande; divide módulos para facilitar compilación y mantenimiento.")
        return out.distinctBy{it.message}
    }

    fun analyze(project:GbaProject):PortingReport {
        val files=project.directory.walkTopDown().filter{it.isFile && it.extension.lowercase() in sourceExtensions}
            .filter{it.length()<=512*1024}.take(1000).toList()
        val findings=mutableListOf<PortingFinding>();var bytes=0L
        files.forEach{file->
            val size=file.length();bytes+=size
            if(bytes>8L*1024*1024)return@forEach
            val relative=file.relativeTo(project.directory).invariantSeparatorsPath
            val source=runCatching{file.readText(Charsets.UTF_8)}.getOrNull()?:return@forEach
            findings+=analyzeSource(relative,source)
        }
        return PortingReport(files.size,bytes,findings)
    }
}
