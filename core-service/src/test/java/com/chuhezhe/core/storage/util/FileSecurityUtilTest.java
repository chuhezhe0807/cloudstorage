package com.chuhezhe.core.storage.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileSecurityUtilTest {

    @Test
    void validateFileNameAcceptsNormal() {
        assertDoesNotThrow(() -> FileSecurityUtil.validateFileName("test.txt"));
    }

    @Test
    void validateFileNameRejectsPathTraversal1() {
        assertThrows(IllegalArgumentException.class,
                () -> FileSecurityUtil.validateFileName("../etc/passwd"));
    }

    @Test
    void validateFileNameRejectsPathTraversal2() {
        assertThrows(IllegalArgumentException.class,
                () -> FileSecurityUtil.validateFileName("..\\etc\\passwd"));
    }

    @Test
    void validateFileNameRejectsSlash() {
        assertThrows(IllegalArgumentException.class,
                () -> FileSecurityUtil.validateFileName("a/b"));
    }

    @Test
    void validateFileNameRejectsBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> FileSecurityUtil.validateFileName(""));
    }

    @Test
    void validateExtensionAcceptsWhitelist() {
        assertDoesNotThrow(() -> FileSecurityUtil.validateExtension("doc.txt"));
        assertDoesNotThrow(() -> FileSecurityUtil.validateExtension("doc.pdf"));
        assertDoesNotThrow(() -> FileSecurityUtil.validateExtension("doc.docx"));
        assertDoesNotThrow(() -> FileSecurityUtil.validateExtension("doc.jpg"));
        assertDoesNotThrow(() -> FileSecurityUtil.validateExtension("doc.zip"));
        assertDoesNotThrow(() -> FileSecurityUtil.validateExtension("noext"));
    }

    @Test
    void validateExtensionRejectsExe() {
        assertThrows(IllegalArgumentException.class,
                () -> FileSecurityUtil.validateExtension("virus.exe"));
    }

    @Test
    void validateExtensionRejectsBat() {
        assertThrows(IllegalArgumentException.class,
                () -> FileSecurityUtil.validateExtension("script.bat"));
    }

    @Test
    void validateFileSizeAcceptsValid() {
        assertDoesNotThrow(() -> FileSecurityUtil.validateFileSize(1024));
    }

    @Test
    void validateFileSizeRejectsZero() {
        assertThrows(IllegalArgumentException.class,
                () -> FileSecurityUtil.validateFileSize(0));
    }

    @Test
    void validateFileSizeRejectsOverLimit() {
        long overLimit = FileSecurityUtil.MAX_FILE_SIZE + 1;
        assertThrows(IllegalArgumentException.class,
                () -> FileSecurityUtil.validateFileSize(overLimit));
    }
}
