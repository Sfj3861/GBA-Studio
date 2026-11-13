package com.sfj3861.gbastudio

import java.io.File
import java.nio.file.Files
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
        assertTrue(AiProjectContextBuilder.isCredentialPath(".gitconfig"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("config/.gitconfig"))
        assertTrue(AiProjectContextBuilder.isCredentialPath(".git/config"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("src/.git/config"))
        assertTrue(AiProjectContextBuilder.isCredentialPath("""src\.git\config"""))
        assertTrue(AiProjectContextBuilder.isCredentialPath("""config\secrets\service-account.json"""))
        assertFalse(AiProjectContextBuilder.isCredentialPath("src/main.c"))
    }
    @Test fun redactsApiKeysTokensAndQuotedCredentialAssignments() {
        val safe=AiProjectContextBuilder.sanitizeForSharing(
            """{"OPENAI_API_KEY":"sk-proj-abcdefghijklmnopqrstuvwxyz012345","GITHUB_TOKEN":"ghp_abcdefghijklmnopqrstuvwxyz012345","AWS_SECRET_ACCESS_KEY":"AKIA1234567890ABCDEF","password":"hunter2 with spaces","authorization":"Bearer abc.def.ghi","http.extraheader":"Basic dXNlcjpwYXNz"}"""
        )
        assertFalse(safe.contains("sk-proj-"))
        assertFalse(safe.contains("ghp_"))
        assertFalse(safe.contains("AKIA"))
        assertFalse(safe.contains("hunter2"))
        assertFalse(safe.contains("with spaces"))
        assertFalse(safe.contains("abc.def.ghi"))
        assertFalse(safe.contains("dXNlcjpwYXNz"))
        assertTrue(safe.contains("[REDACTED]"))
    }
    @Test fun redactsGitConfigExtraHeaderValuesIncludingLineContinuations() {
        val config = """
            [http]
                extraHeader = AUTHORIZATION: Basic dXNlcjpwYXNz
            [https://example.invalid]
                extraHeader = AUTHORIZATION: Basic \
                    c2Vjb25kLXNlY3JldA==
            [https://multi.example.invalid]
                extraHeader = AUTHORIZATION: Basic \
                    first-continuation \
                    final-continuation-secret
        """.trimIndent()
        val safe=AiProjectContextBuilder.sanitizeForSharing(config)
        assertFalse(safe.contains("dXNlcjpwYXNz"))
        assertFalse(safe.contains("c2Vjb25kLXNlY3JldA=="))
        assertFalse(safe.contains("first-continuation"))
        assertFalse(safe.contains("final-continuation-secret"))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test fun redactsGitConfigExtraHeaderValuesWithoutEqualsIncludingLineContinuations() {
        val config = """
            [http]
                extraHeader AUTHORIZATION: Basic no-equals-secret
            [https://no-equals.example.invalid]
                extraHeader AUTHORIZATION: Basic \\
                    no-equals-continuation-secret
        """.trimIndent()
        val safe=AiProjectContextBuilder.sanitizeForSharing(config)
        assertFalse(safe.contains("no-equals-secret"))
        assertFalse(safe.contains("no-equals-continuation-secret"))
        assertTrue(safe.contains("[REDACTED]"))
    }

    @Test fun contextRejectsCredentialTargetsReachedThroughSymlinks() {
        val root=Files.createTempDirectory("gba-ai-context").toFile()
        try {
            val secret=File(root,"secrets/api-token.txt").apply {
                parentFile?.mkdirs()
                writeText("sensitive")
            }
            val alias=File(root,"src/config.txt").apply {
                parentFile?.mkdirs()
            }
            Files.createSymbolicLink(alias.toPath(),secret.toPath())

            assertFalse(AiProjectContextBuilder.isCredentialPath("src/config.txt"))
            val resolved=AiProjectContextBuilder.canonicalProjectRelativePath(root,"src/config.txt")
            assertTrue(AiProjectContextBuilder.isCredentialPath(resolved))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun contextRejectsSymlinkTargetsOutsideProject() {
        val root=Files.createTempDirectory("gba-ai-context").toFile()
        val outside=Files.createTempFile("gba-ai-secret",".txt")
        try {
            val alias=File(root,"src/config.txt").apply {
                parentFile?.mkdirs()
            }
            Files.createSymbolicLink(alias.toPath(),outside)
            var rejected=false
            try {
                AiProjectContextBuilder.canonicalProjectRelativePath(root,"src/config.txt")
            } catch (_: IllegalArgumentException) {
                rejected=true
            }
            assertTrue(rejected)
        } finally {
            root.deleteRecursively()
            Files.deleteIfExists(outside)
        }
    }

    @Test fun approvedChangeTargetsRejectCredentialPaths() {
        val root=Files.createTempDirectory("gba-ai-change-gate").toFile()
        try {
            val blockedPaths=listOf(
                "secrets/api-token.json",
                "config/credentials.json",
                "google-services.json",
                ".git/config",
                "config/.git-credentials"
            )
            blockedPaths.forEach { path ->
                assertTrue(
                    "Expected credential target to be rejected: $path",
                    runCatching { AiChangeGate.validatedTargetPath(root,path) }.isFailure
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun approvedChangeRejectsSymlinkAliasesToCredentialFiles() {
        val root=Files.createTempDirectory("gba-ai-change-gate").toFile()
        try {
            val secret=File(root,"secrets/api-token.json").apply {
                parentFile?.mkdirs()
                writeText("""{"token":"private"}""")
            }
            val alias=File(root,"src/settings.json").apply { parentFile?.mkdirs() }
            Files.createSymbolicLink(alias.toPath(),secret.toPath())

            assertTrue(
                runCatching { AiChangeGate.validatedTargetPath(root,"src/settings.json") }.isFailure
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun approvedChangeKeepsOrdinaryProjectTargets() {
        val root=Files.createTempDirectory("gba-ai-change-gate").toFile()
        try {
            File(root,"src").mkdirs()
            assertTrue(AiChangeGate.validatedTargetPath(root,"src/main.c")=="src/main.c")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun proposalsDefaultToUnapproved() {
        val proposal=AiChangeProposal("src/main.c","int main(){return 0;}","Refactor")
        assertFalse(proposal.approved)
    }
}
