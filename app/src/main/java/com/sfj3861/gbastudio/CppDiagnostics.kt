package com.sfj3861.gbastudio

data class CodeDiagnostic(
    val line: Int,
    val column: Int,
    val severity: Severity,
    val message: String
) {
    enum class Severity { ERROR, WARNING, INFO }
}

object CppDiagnostics {
    /** Fast editor feedback. This is intentionally a heuristic checker, not a C/C++ compiler. */
    fun analyze(source: String): List<CodeDiagnostic> {
        val out = mutableListOf<CodeDiagnostic>()
        val lines = source.lines()
        var braces = 0
        var parens = 0
        var brackets = 0
        var inBlockComment = false

        lines.forEachIndexed { index, raw ->
            val lineNo = index + 1
            val sanitized = stripCommentsAndLiterals(raw, inBlockComment)
            val code = sanitized.first
            inBlockComment = sanitized.second

            braces += code.count { it == '{' } - code.count { it == '}' }
            parens += code.count { it == '(' } - code.count { it == ')' }
            brackets += code.count { it == '[' } - code.count { it == ']' }

            if (braces < 0) {
                out += CodeDiagnostic(lineNo, 1, CodeDiagnostic.Severity.ERROR, "Llave de cierre sin apertura.")
                braces = 0
            }
            if (parens < 0) {
                out += CodeDiagnostic(lineNo, 1, CodeDiagnostic.Severity.ERROR, "Paréntesis de cierre sin apertura.")
                parens = 0
            }
            if (brackets < 0) {
                out += CodeDiagnostic(lineNo, 1, CodeDiagnostic.Severity.ERROR, "Corchete de cierre sin apertura.")
                brackets = 0
            }
            if (Regex("\\bprintf\\s*\\(").containsMatchIn(code) && !code.contains("#include")) {
                out += CodeDiagnostic(lineNo, 1, CodeDiagnostic.Severity.INFO, "Comprueba que has incluido <stdio.h> si utilizas printf.")
            }
            if (Regex("\\b(?:int|char|short|long|float|double|void)\\s+\\w+\\s*\\([^;]*\\)\\s*\\{").containsMatchIn(code) &&
                !code.contains("return") && Regex("\\b(int|char|short|long|float|double)\\s+\\w+\\s*\\(").containsMatchIn(code)) {
                out += CodeDiagnostic(lineNo, 1, CodeDiagnostic.Severity.WARNING, "La función parece no contener return; comprueba su tipo de retorno.")
            }
            if (Regex("\\b(?:TODO|FIXME)\\b").containsMatchIn(code)) {
                out += CodeDiagnostic(lineNo, 1, CodeDiagnostic.Severity.INFO, "Hay una tarea pendiente en este archivo.")
            }
            if (code.trimEnd().endsWith("=") || code.trimEnd().endsWith("+")) {
                out += CodeDiagnostic(lineNo, code.length, CodeDiagnostic.Severity.WARNING, "La expresión parece estar incompleta.")
            }
            if (Regex("\\b(?:int|char|short|long|float|double|void)\\s*;").containsMatchIn(code)) {
                out += CodeDiagnostic(lineNo, 1, CodeDiagnostic.Severity.ERROR, "Declaración de tipo sin nombre de variable.")
            }
        }

        if (inBlockComment) {
            out += CodeDiagnostic(lines.size, 1, CodeDiagnostic.Severity.ERROR, "Comentario de bloque sin cerrar.")
        }
        if (braces > 0) out += CodeDiagnostic(lines.size, 1, CodeDiagnostic.Severity.ERROR, "Faltan $braces llave(s) de cierre.")
        if (parens > 0) out += CodeDiagnostic(lines.size, 1, CodeDiagnostic.Severity.ERROR, "Faltan $parens paréntesis de cierre.")
        if (brackets > 0) out += CodeDiagnostic(lines.size, 1, CodeDiagnostic.Severity.ERROR, "Faltan $brackets corchete(s) de cierre.")
        if (out.isEmpty()) out += CodeDiagnostic(1, 1, CodeDiagnostic.Severity.INFO, "No se han encontrado problemas mediante las comprobaciones rápidas.")
        return out
    }

    /**
     * Removes comments and quoted literals without interpreting their contents as code.
     * Block-comment state is returned so comments can span multiple source lines.
     */
    private fun stripCommentsAndLiterals(line: String, initiallyInBlockComment: Boolean): Pair<String, Boolean> {
        val output = StringBuilder(line.length)
        var inBlockComment = initiallyInBlockComment
        var index = 0
        while (index < line.length) {
            if (inBlockComment) {
                if (index + 1 < line.length && line[index] == '*' && line[index + 1] == '/') {
                    output.append("  ")
                    index += 2
                    inBlockComment = false
                } else {
                    output.append(' ')
                    index++
                }
                continue
            }

            if (index + 1 < line.length && line[index] == '/' && line[index + 1] == '/') {
                output.append(" ".repeat(line.length - index))
                break
            }
            if (index + 1 < line.length && line[index] == '/' && line[index + 1] == '*') {
                output.append("  ")
                index += 2
                inBlockComment = true
                continue
            }

            val current = line[index]
            if (current == '"' || current == '\'') {
                val quote = current
                output.append(' ')
                index++
                while (index < line.length) {
                    val char = line[index]
                    output.append(' ')
                    index++
                    if (char == '\\' && index < line.length) {
                        output.append(' ')
                        index++
                    } else if (char == quote) {
                        break
                    }
                }
            } else {
                output.append(current)
                index++
            }
        }
        return output.toString() to inBlockComment
    }
}
