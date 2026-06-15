package edu.bluejack25_2.hwixel.ui.project.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileUrlTypeValidatorTest {

    @Test
    fun driveAcceptsGoogleDriveAndDocsHosts() {
        assertTrue(FileUrlTypeValidator.matches("https://drive.google.com/file/d/abc/view", "drive"))
        assertTrue(FileUrlTypeValidator.matches("https://docs.google.com/document/d/abc/edit", "drive"))
        assertTrue(FileUrlTypeValidator.matches("https://drive.usercontent.google.com/download?id=abc", "drive"))
    }

    @Test
    fun driveRejectsNonDriveHosts() {
        assertFalse(FileUrlTypeValidator.matches("https://github.com/user/repo", "drive"))
        assertFalse(FileUrlTypeValidator.matches("https://example.com/spec.pdf", "drive"))
    }

    @Test
    fun githubAcceptsGithubHosts() {
        assertTrue(FileUrlTypeValidator.matches("https://github.com/user/repo", "github"))
        assertTrue(FileUrlTypeValidator.matches("https://gist.github.com/user/abc", "github"))
        assertTrue(FileUrlTypeValidator.matches("https://raw.githubusercontent.com/u/r/main/file", "github"))
        assertTrue(FileUrlTypeValidator.matches("https://user.github.io/page", "github"))
    }

    @Test
    fun githubRejectsNonGithubHosts() {
        assertFalse(FileUrlTypeValidator.matches("https://drive.google.com/file/d/abc", "github"))
        assertFalse(FileUrlTypeValidator.matches("https://example.com", "github"))
    }

    @Test
    fun otherAcceptsAnyValidUrl() {
        assertTrue(FileUrlTypeValidator.matches("https://example.com", "other"))
        assertTrue(FileUrlTypeValidator.matches("https://github.com/user/repo", "other"))
        assertTrue(FileUrlTypeValidator.matches("https://drive.google.com/file/d/abc", "other"))
    }

    @Test
    fun blankOrMalformedHostRejected() {
        assertFalse(FileUrlTypeValidator.matches("not a url", "other"))
        assertFalse(FileUrlTypeValidator.matches("", "drive"))
    }
}
