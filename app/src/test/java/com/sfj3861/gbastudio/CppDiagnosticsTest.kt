package com.sfj3861.gbastudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CppDiagnosticsTest {
    @Test fun findsUnclosedBrace() {
        val results = CppDiagnostics.analyze("int main(void) {\n return 0;")
        assertTrue(results.any { it.severity == CodeDiagnostic.Severity.ERROR && it.message.contains("Falta el cierre") })
    }

    @Test fun ignoresBracesInsideString() {
        val results = CppDiagnostics.analyze("const char* s = \"{\";")
        assertFalse(results.any { it.severity == CodeDiagnostic.Severity.ERROR })
    }

    @Test fun ignoresCommentMarkersInsideStrings() {
        val results = CppDiagnostics.analyze("const char* url = \"https://example.test/{path}\";")
        assertFalse(results.any { it.severity == CodeDiagnostic.Severity.ERROR })
    }

    @Test fun ignoresBracesInsideMultilineBlockComments() {
        val results = CppDiagnostics.analyze("int main() {\n/* } ) ]\n still comment { */\nreturn 0;\n}")
        assertFalse(results.any { it.severity == CodeDiagnostic.Severity.ERROR })
    }

    @Test fun reportsUnclosedBlockComment() {
        val results = CppDiagnostics.analyze("int main() { /* never closed")
        assertTrue(results.any { it.message.contains("Comentario de bloque") })
    }

    @Test fun doesNotSuggestStdioWhenHeaderWasIncludedEarlier() {
        val results = CppDiagnostics.analyze("#include <stdio.h>\nint main(void) {\n printf(\"ok\");\n return 0;\n}")
        assertFalse(results.any { it.message.contains("stdio.h") })
    }

    @Test fun detectsMismatchedDelimiterOrder() {
        val results = CppDiagnostics.analyze("int main(void) { return (1]; }")
        assertTrue(results.any { it.message.contains("orden incorrecto") })
    }


    @Test fun ignoresIncludesInsideBlockComments() {
        val results = CppDiagnostics.analyze("/*\n#include <stdio.h>\n*/\nprintf(\"not included\");")
        assertTrue(results.any { it.message.contains("stdio.h") })
    }

    @Test fun doesNotWarnAboutReturnOnNextLine() {
        val results = CppDiagnostics.analyze("int main(void) {\n return 0;\n}")
        assertFalse(results.any { it.severity == CodeDiagnostic.Severity.WARNING })
        assertFalse(results.any { it.severity == CodeDiagnostic.Severity.ERROR })
    }
}
