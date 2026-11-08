package com.sfj3861.gbastudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChangeGateTest {
    @Test fun contextRejectsCredentialFilesAndSecretDirectories() {
        assertTrue(AiProjectContextBuilder.isCredentialPath(".env"))
        assertTrue(AiProjectContextBuilder.isCredentialPath(".envrc"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("config/.env.production"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("config/prod.env"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("secrets/service-account.json"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("config/secrets.yaml"))
        assertTrue(AiProjectContextBuilder.isCredentialPath(".aws/credentials"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("keys/release.jks"))
        assertTrue(AiProjectContextBuilder.isCredentialPath(".git-credentials"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("config/.git-credentials"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("git-credentials"))
        assertFalse(AiProjectContextBuilder.isCredentialPath("src/main.c"))
    }
    @Test fun redactsApiKeysTokensAndQuotedCredentialAssignments() {
        val safe=AiProjectContextBuilder.sanitizeForSharing(
            """{"OPENAI_API_KEY":"sk-proj-abcdefghijklmnopqrstuvwxyz012345","GITHUB_TOKEN":"ghp_abcdefghijklmnopqrstuvwxyz012345","AWS_SECRET_ACCESS_KEY":"AKIA1234567890ABCDEF","password":"hunter2 with spaces","authorization":"Bearer abc.def.ghi"}"""
        )
        assertFalse(safe.contains("sk-proj-"))
        assertFalse(safe.contains("ghp_"))
        assertFalse(safe.contains("AKIA"))
        assertFalse(safe.contains("hunter2"))
        assertFalse(safe.contains("with spaces"))
        assertFalse(safe.contains("abc.def.ghi"))
        assertTrue(safe.contains("[REDACTED]"))
    }
    @Test fun proposalsDefaultToUnapproved() {
        val proposal=AiChangeProposal("src/main.c","int main(){return 0;}","Refactor")
        assertFalse(proposal.approved)
    }
}
