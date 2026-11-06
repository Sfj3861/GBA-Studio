package com.sfj3861.gbastudio

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class GbaProject(
    val id: String,
    val name: String,
    val directory: File,
    val updatedAt: Long
)

class ProjectRepository(context: Context) {
    private val root = File(context.filesDir, "gba-projects").apply { mkdirs() }
    private val index = File(root, "projects.json")

    fun list(): List<GbaProject> = readIndex().mapNotNull { item ->
        val id = item.optString("id")
        val name = item.optString("name")
        val safeId = runCatching { UUID.fromString(id).toString() }.getOrNull()
        val dir = safeId?.let { File(root, it) }
        if (safeId == null || name.isBlank() || dir == null || !dir.isDirectory) null
        else GbaProject(safeId, name, dir, item.optLong("updatedAt"))
    }.sortedByDescending { it.updatedAt }

    fun create(name: String): GbaProject {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "El nombre no puede estar vacío." }
        require(clean.length <= 40) { "El nombre no puede superar 40 caracteres." }
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        check(dir.mkdirs()) { "No se pudo crear la carpeta del proyecto." }
        File(dir, "src").mkdirs()
        File(dir, "assets").mkdirs()
        File(dir, "README.md").writeText("# " + clean + "\n\nProyecto GBA creado con GBA Studio.\n")
        File(dir, "src/main.c").writeText(
            "#include <stdint.h>\n\nint main(void) {\n    // Punto de entrada del juego.\n    while (1) {\n        // Bucle principal.\n    }\n    return 0;\n}\n"
        )
        File(dir, "project.json").writeText(
            JSONObject().put("name", clean).put("platform", "Game Boy Advance")
                .put("language", "C").put("createdAt", System.currentTimeMillis()).toString(2)
        )
        val project = GbaProject(id, clean, dir, System.currentTimeMillis())
        writeIndex(list() + project)
        return project
    }

    fun rename(project: GbaProject, newName: String) {
        requireOwned(project)
        val clean = newName.trim()
        require(clean.isNotEmpty()) { "El nombre no puede estar vacío." }
        require(clean.length <= 40) { "El nombre no puede superar 40 caracteres." }
        val all = list().map { if (it.id == project.id) it.copy(name = clean, updatedAt = System.currentTimeMillis()) else it }
        val config = File(project.directory, "project.json")
        val json = if (config.exists()) JSONObject(config.readText()) else JSONObject()
        config.writeText(json.put("name", clean).toString(2))
        writeIndex(all)
    }

    fun delete(project: GbaProject) {
        requireOwned(project)
        check(project.directory.deleteRecursively()) { "No se pudo eliminar la carpeta del proyecto." }
        writeIndex(list().filterNot { it.id == project.id })
    }

    fun exportProject(project: GbaProject, output: OutputStream) {
        requireOwned(project)
        ZipOutputStream(output).use { zip ->
            project.directory.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(project.directory).invariantSeparatorsPath
                require(!relative.startsWith("/") && !relative.split("/").contains(".."))
                zip.putNextEntry(ZipEntry(relative))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun importProject(input: InputStream): GbaProject {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id)
        check(dir.mkdirs()) { "No se pudo crear el proyecto importado." }
        try {
            var entries = 0
            var totalBytes = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    require(entries <= 2000) { "El archivo contiene demasiados elementos." }
                    val name = entry.name
                    require(name.isNotBlank() && !name.startsWith("/") && !name.contains("\\") &&
                        !name.split("/").contains("..")) { "El archivo ZIP contiene una ruta no válida." }
                    val target = File(dir, name).canonicalFile
                    require(target.path.startsWith(dir.canonicalPath + File.separator)) {
                        "El archivo ZIP intenta salir de la carpeta del proyecto."
                    }
                    if (entry.isDirectory) {
                        check(target.mkdirs() || target.isDirectory) { "No se pudo crear una carpeta importada." }
                    } else {
                        check(target.parentFile?.let { it.isDirectory || it.mkdirs() } ?: false) {
                            "No se pudo crear una carpeta importada."
                        }
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                require(totalBytes <= 32L * 1024L * 1024L) {
                                    "El proyecto supera el límite de importación de 32 MB."
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifest = File(dir, "project.json")
            require(manifest.isFile) { "El ZIP no contiene project.json; no parece un proyecto GBA Studio." }
            val metadata = JSONObject(manifest.readText(Charsets.UTF_8))
            val name = metadata.optString("name").trim().ifBlank { "Proyecto importado" }.take(40)
            metadata.put("name", name)
            manifest.writeText(metadata.toString(2), Charsets.UTF_8)
            val project = GbaProject(id, name, dir, System.currentTimeMillis())
            writeIndex(list() + project)
            return project
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    fun createTextFile(project: GbaProject, relativePath: String) {
        val file = resolveFile(project, relativePath)
        require(file.extension.lowercase() in setOf("c", "h", "cpp", "hpp", "txt", "md", "json")) {
            "Extensión no compatible."
        }
        check(!file.exists()) { "Ya existe un archivo con ese nombre." }
        check(file.parentFile?.let { it.isDirectory || it.mkdirs() } ?: false) { "No se pudo crear la carpeta." }
        check(file.createNewFile()) { "No se pudo crear el archivo." }
    }

    fun readText(project: GbaProject, relativePath: String): String {
        val file = resolveFile(project, relativePath)
        require(file.isFile) { "El archivo no existe." }
        require(file.length() <= 2_000_000L) { "El archivo supera el límite de edición de 2 MB." }
        return file.readText(Charsets.UTF_8)
    }

    fun writeText(project: GbaProject, relativePath: String, content: String) {
        val file = resolveFile(project, relativePath)
        require(content.toByteArray(Charsets.UTF_8).size <= 2_000_000) { "El archivo supera el límite de edición de 2 MB." }
        require(file.isFile) { "Solo se pueden editar archivos existentes." }
        require(file.extension.lowercase() in setOf("c", "h", "cpp", "hpp", "txt", "md", "json")) {
            "Este tipo de archivo todavía no se puede editar."
        }
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(content, Charsets.UTF_8)
        check(temp.renameTo(file)) { "No se pudo guardar el archivo." }
        val now = System.currentTimeMillis()
        writeIndex(list().map { if (it.id == project.id) it.copy(updatedAt = now) else it })
    }

    private fun requireOwned(project: GbaProject) {
        val canonicalRoot = root.canonicalFile
        val canonicalDir = project.directory.canonicalFile
        require(canonicalDir.parentFile == canonicalRoot && canonicalDir.name == project.id) {
            "La carpeta no pertenece a este espacio de proyectos."
        }
    }

    private fun resolveFile(project: GbaProject, relativePath: String): File {
        requireOwned(project)
        require(relativePath.isNotBlank() && !relativePath.startsWith("/") && !relativePath.contains("\\")) {
            "Ruta de archivo no válida."
        }
        val base = project.directory.canonicalFile
        val file = File(base, relativePath).canonicalFile
        require(file.path.startsWith(base.path + File.separator)) { "La ruta sale de la carpeta del proyecto." }
        return file
    }

    private fun readIndex(): List<JSONObject> = try {
        if (!index.exists()) emptyList()
        else {
            val array = JSONArray(index.readText())
            (0 until array.length()).map { array.getJSONObject(it) }
        }
    } catch (_: Exception) { emptyList() }

    private fun writeIndex(projects: List<GbaProject>) {
        val array = JSONArray()
        projects.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("updatedAt", it.updatedAt))
        }
        val temp = File(root, "projects.json.tmp")
        temp.writeText(array.toString(2))
        if (index.exists()) index.delete()
        check(temp.renameTo(index)) { "No se pudo guardar el índice de proyectos." }
    }
}
