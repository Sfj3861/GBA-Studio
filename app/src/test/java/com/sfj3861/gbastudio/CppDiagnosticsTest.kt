package com.sfj3861.gbastudio

import org.junit.Assert.assertTrue
import org.junit.Test

class CppDiagnosticsTest {
    @Test fun findsUnclosedBrace() {
        val results = CppDiagnostics.analyze("int main(void) {\n return 0;")
        assertTrue(results.any { it.severity == CodeDiagnostic.Severity.ERROR && it.message.contains("llave") })
    }

    @Test fun ignoresBracesInsideString() {
        val results = CppDiagnostics.analyze("const char* s = \"{\";")
        assertTrue(results.none { it.message.contains("llave") })
    }
}
