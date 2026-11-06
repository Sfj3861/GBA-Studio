package com.sfj3861.gbastudio

import android.content.Context
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
        val dir = File(root, id)
        if (id.isBlank() || name.isBlank() || !dir.isDirectory) null
        else GbaProject(id, name, dir, item.optLong("updatedAt"))
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
        project.directory.deleteRecursively()
        writeIndex(list().filterNot { it.id == project.id })
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
