package com.sfj3861.gbastudio

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectArchivePathValidatorTest {
    @Test fun acceptsNormalProjectPaths() {
        assertTrue(ProjectArchivePathValidator.isSafe("project.json"))
        assertTrue(ProjectArchivePathValidator.isSafe("src/main.c"))
        assertTrue(ProjectArchivePathValidator.isSafe("assets/backgrounds/forest.png"))
        assertTrue(ProjectArchivePathValidator.isSafe("assets/"))
    }

    @Test fun rejectsTraversalAndAbsolutePaths() {
        assertFalse(ProjectArchivePathValidator.isSafe("../outside.txt"))
        assertFalse(ProjectArchivePathValidator.isSafe("src/../../outside.txt"))
        assertFalse(ProjectArchivePathValidator.isSafe("/absolute/path"))
        assertFalse(ProjectArchivePathValidator.isSafe("C:/outside.txt"))
        assertFalse(ProjectArchivePathValidator.isSafe("C:\\outside.txt"))
    }

    @Test fun exportRejectsSymlinksThatPointOutsideTheProject() {
        val root = Files.createTempDirectory("gba-export-root").toFile()
        val outside = Files.createTempFile("gba-export-secret", ".txt").toFile()
        try {
            val alias = File(root, "src/leak.txt").apply { parentFile?.mkdirs() }
            Files.createSymbolicLink(alias.toPath(), outside.toPath())

            assertFalse(
                runCatching { ProjectArchivePathValidator.canonicalExportFile(root, alias) }.isSuccess
            )
        } finally {
            root.deleteRecursively()
            Files.deleteIfExists(outside.toPath())
        }
    }

    @Test fun exportResolvesInternalSymlinksToOneCanonicalFile() {
        val root = Files.createTempDirectory("gba-export-root").toFile()
        try {
            val target = File(root, "src/main.c").apply {
                parentFile?.mkdirs()
                writeText("int main(void) { return 0; }")
            }
            val alias = File(root, "src/alias.c")
            Files.createSymbolicLink(alias.toPath(), target.toPath())

            assertTrue(
                ProjectArchivePathValidator.canonicalExportFile(root, alias) == target.canonicalFile
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsNonPortableAndAmbiguousPaths() {
        assertFalse(ProjectArchivePathValidator.isSafe("src\\main.c"))
        assertFalse(ProjectArchivePathValidator.isSafe("src//main.c"))
        assertFalse(ProjectArchivePathValidator.isSafe("src/./main.c"))
        assertFalse(ProjectArchivePathValidator.isSafe("bad\u0000name"))
        assertFalse(ProjectArchivePathValidator.isSafe(""))
        assertFalse(ProjectArchivePathValidator.isSafe("a".repeat(241)))
    }
}
