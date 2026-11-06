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
    private data class OpeningDelimiter(val char: Char, val line: Int, val column: Int)

    /** Fast editor feedback. This is intentionally a heuristic checker, not a C/C++ compiler. */
    fun analyze(source: String): List<CodeDiagnostic> {
        val out = mutableListOf<CodeDiagnostic>()
        val lines = source.lines()
        val hasStdIoInclude = lines.any {
            Regex("""^\s*#\s*include\s*[<"]stdio\.h[>"]""").containsMatchIn(it)
        }
        val delimiters = mutableListOf<OpeningDelimiter>()
        var inBlockComment = false

        lines.forEachIndexed { index, raw ->
            val lineNo = index + 1
            val sanitized = stripCommentsAndLiterals(raw, inBlockComment)
            val code = sanitized.first
            inBlockComment = sanitized.second

            code.forEachIndexed { columnIndex, char ->
                when (char) {
                    '{', '(', '[' -> delimiters += OpeningDelimiter(char, lineNo, columnIndex + 1)
                    '}', ')', ']' -> {
                        val expected = when (char) {
                            '}' -> '{'
                            ')' -> '('
                            else -> '['
                        }
                        val opening = delimiters.lastOrNull()
                        if (opening == null || opening.char != expected) {
                            out += CodeDiagnostic(
                                lineNo,
                                columnIndex + 1,
                                CodeDiagnostic.Severity.ERROR,
                                "Cierre '$char' sin apertura coincidente o en orden incorrecto."
                            )
                        } else {
                            delimiters.removeAt(delimiters.lastIndex)
                        }
                    }
                }
            }

            if (Regex("""\bprintf\s*\(""").containsMatchIn(code) && !hasStdIoInclude) {
                out += CodeDiagnostic(
                    lineNo,
                    1,
                    CodeDiagnostic.Severity.INFO,
                    "Comprueba que has incluido <stdio.h> si utilizas printf."
                )
            }
            if (code.trimEnd().endsWith("=") || code.trimEnd().endsWith("+")) {
                out += CodeDiagnostic(
                    lineNo,
                    code.length,
                    CodeDiagnostic.Severity.WARNING,
                    "La expresión parece estar incompleta."
                )
            }
            if (Regex("""\b(?:int|char|short|long|float|double|void)\s*;""").containsMatchIn(code)) {
                out += CodeDiagnostic(
                    lineNo,
                    1,
                    CodeDiagnostic.Severity.ERROR,
                    "Declaración de tipo sin nombre de variable."
                )
            }
        }

        if (inBlockComment) {
            out += CodeDiagnostic(
                lines.size,
                1,
                CodeDiagnostic.Severity.ERROR,
                "Comentario de bloque sin cerrar."
            )
        }
        delimiters.forEach { opening ->
            val closing = when (opening.char) {
                '{' -> '}'
                '(' -> ')'
                else -> ']'
            }
            out += CodeDiagnostic(
                opening.line,
                opening.column,
                CodeDiagnostic.Severity.ERROR,
                "Falta el cierre '$closing' para '" + opening.char + "'."
            )
        }
        if (out.isEmpty()) {
            out += CodeDiagnostic(
                1, 1, CodeDiagnostic.Severity.INFO,
                "No se han encontrado problemas mediante las comprobaciones rápidas."
            )
        }
        return out
    }

    /**
     * Removes comments and quoted literals without interpreting their contents as code.
     * Block-comment state is returned so comments can span multiple source lines.
     */
    private fun stripCommentsAndLiterals(
        line: String,
        initiallyInBlockComment: Boolean
    ): Pair<String, Boolean> {
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
