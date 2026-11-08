package com.sfj3861.gbastudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChangeGateTest {
    @Test fun contextRejectsCredentialFilesAndSecretDirectories() {
        assertTrue(AiProjectContextBuilder.isCredentialPath(".env"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("config/.env.production"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("config/prod.env"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("secrets/service-account.json"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("keys/release.jks"))
        assertFalse(AiProjectContextBuilder.isCredentialPath("src/main.c"))
    }
    @Test fun redactsApiKeysAndQuotedCredentialAssignments() {
        val safe=AiProjectContextBuilder.sanitizeForSharing(
            """{"OPENAI_API_KEY":"sk-proj-abcdefghijklmnopqrstuvwxyz012345","password":"hunter2 with spaces"}"""
        )
        assertFalse(safe.contains("sk-proj-"))
        assertFalse(safe.contains("hunter2"))
        assertFalse(safe.contains("with spaces"))
        assertTrue(safe.contains("[REDACTED]"))
    }
    @Test fun proposalsDefaultToUnapproved() {
        val proposal=AiChangeProposal("src/main.c","int main(){return 0;}","Refactor")
        assertFalse(proposal.approved)
    }
}
