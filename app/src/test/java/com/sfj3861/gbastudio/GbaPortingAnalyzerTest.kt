package com.sfj3861.gbastudio

import org.junit.Assert.assertTrue
import org.junit.Test

class GbaPortingAnalyzerTest {
    @Test fun flagsDesktopIncludesFilesAndConsoleOutput(){
        val findings=GbaPortingAnalyzer.analyzeSource("src/main.c","#include <windows.h>\nint main(){printf(\"hi\");}")
        assertTrue(findings.any{it.message.contains("escritorio")})
        assertTrue(findings.any{it.message.contains("consola")})
    }
    @Test fun cleanBasicSourceHasNoWarnings(){
        assertTrue(GbaPortingAnalyzer.analyzeSource("src/main.c","int main(void){for(;;){}}").isEmpty())
    }
}
