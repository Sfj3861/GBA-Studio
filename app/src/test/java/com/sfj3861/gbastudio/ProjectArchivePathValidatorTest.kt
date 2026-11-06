package com.sfj3861.gbastudio

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

    @Test fun rejectsNonPortableAndAmbiguousPaths() {
        assertFalse(ProjectArchivePathValidator.isSafe("src\\main.c"))
        assertFalse(ProjectArchivePathValidator.isSafe("src//main.c"))
        assertFalse(ProjectArchivePathValidator.isSafe("src/./main.c"))
        assertFalse(ProjectArchivePathValidator.isSafe("bad\u0000name"))
        assertFalse(ProjectArchivePathValidator.isSafe(""))
        assertFalse(ProjectArchivePathValidator.isSafe("a".repeat(241)))
    }
}
