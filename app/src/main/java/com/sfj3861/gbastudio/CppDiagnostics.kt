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
    private val knownTypes = setOf("void", "char", "short", "int", "long", "float", "double", "bool", "unsigned", "signed", "const", "volatile", "static", "struct", "enum", "typedef")
    private val controlWords = setOf("if", "else", "for", "while", "switch", "case", "return", "break", "continue", "sizeof", "do")

    /** Fast editor feedback. This is intentionally a heuristic checker, not a C/C++ compiler. */
    fun analyze(source: String): List<CodeDiagnostic> {
        val out = mutableListOf<CodeDiagnostic>()
        val lines = source.lines()
        var braces = 0
        var parens = 0
        var inBlockComment = false
        lines.forEachIndexed { index, raw ->
            val lineNo = index + 1
            var cleaned = raw
            if (inBlockComment) {
                val end = cleaned.indexOf("*/")
                if (end < 0) cleaned = "" else { cleaned = cleaned.substring(end + 2); inBlockComment = false }
            }
            while (cleaned.contains("/*")) {
                val start = cleaned.indexOf("/*")
                val end = cleaned.indexOf("*/", start + 2)
                if (end < 0) { cleaned = cleaned.substring(0, start); inBlockComment = true; break }
                cleaned = cleaned.removeRange(start, end + 2)
            }
            cleaned = cleaned.substringBefore("//")
            val code = cleaned.replace(Regex("""\"([^\"\\]|\\.)*\"|'([^'\\]|\\.)*'"""), " ")
            braces += code.count { it == '{' } - code.count { it == '}' }
            parens += code.count { it == '(' } - code.count { it == ')' }
            if (braces < 0) {
                out += CodeDiagnostic(lineNo, 1, CodeDiagnostic.Severity.ERROR, "Llave de cierre sin apertura.")
                braces = 0
            }
            if (parens < 0) {
                out += CodeDiagnostic(lineNo, 1, CodeDiagnostic.Severity.ERROR, "Paréntesis de cierre sin apertura.")
                parens = 0
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
        if (braces > 0) out += CodeDiagnostic(lines.size, 1, CodeDiagnostic.Severity.ERROR, "Faltan $braces llave(s) de cierre.")
        if (parens > 0) out += CodeDiagnostic(lines.size, 1, CodeDiagnostic.Severity.ERROR, "Faltan $parens paréntesis de cierre.")
        if (out.isEmpty()) out += CodeDiagnostic(1, 1, CodeDiagnostic.Severity.INFO, "No se han encontrado problemas mediante las comprobaciones rápidas.")
        return out
    }
}
