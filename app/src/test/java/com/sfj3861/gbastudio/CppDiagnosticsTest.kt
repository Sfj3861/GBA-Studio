package com.sfj3861.gbastudio

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class CppDiagnosticsTest {
    @Test fun findsUnclosedBrace() {
        val results = CppDiagnostics.analyze("int main(void) {\\n return 0;")
        assertTrue(results.any { it.severity == CodeDiagnostic.Severity.ERROR && it.message.contains("llave") })
    }

    @Test fun ignoresBracesInsideString() {
        val results = CppDiagnostics.analyze("const char* s = \"{\";")
        assertFalse(results.any { it.message.contains("llave") })
    }

    @Test fun ignoresCommentMarkersInsideStrings() {
        val results = CppDiagnostics.analyze("const char* url = \"https://example.test/{path}\";")
        assertFalse(results.any { it.message.contains("llave") })
    }

    @Test fun ignoresBracesInsideMultilineBlockComments() {
        val results = CppDiagnostics.analyze("int main() {\\n/* } ) ]\\n still comment { */\\nreturn 0;\\n}")
        assertFalse(results.any { it.message.contains("llave") || it.message.contains("paréntesis") || it.message.contains("corchete") })
    }

    @Test fun reportsUnclosedBlockComment() {
        val results = CppDiagnostics.analyze("int main() { /* never closed")
        assertTrue(results.any { it.message.contains("Comentario de bloque") })
    }
}
