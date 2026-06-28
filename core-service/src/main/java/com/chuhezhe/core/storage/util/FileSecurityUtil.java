package com.chuhezhe.core.storage.util;

import java.util.Set;

/**
 * 文件安全校验工具：文件名路径穿越检测、扩展名白名单、大小上限。
 */
public class FileSecurityUtil {

    // 允许上传的文件扩展名
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "txt", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "jpg", "jpeg", "png", "gif", "bmp", "svg", "ico",
            "mp3", "mp4", "avi", "mov", "mkv",
            "zip", "rar", "7z", "tar", "gz",
            "csv", "json", "xml", "yaml", "yml",
            "md", "html", "css", "js", "ts", "java", "py",
            "log", "sql"
    );

    /** 单文件最大 5GB */
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024 * 1024;

    /** 单分片大小 5MB */
    public static final long CHUNK_SIZE = 5L * 1024 * 1024;

    /**
     * 校验文件名，拒绝路径穿越字符。
     */
    public static void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("文件名包含非法路径字符");
        }
    }

    /**
     * 校验文件扩展名是否在白名单。
     */
    public static void validateExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return;
        }
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型: " + ext);
        }
    }

    /**
     * 校验文件大小。
     */
    public static void validateFileSize(long fileSize) {
        if (fileSize <= 0) {
            throw new IllegalArgumentException("文件大小必须大于0");
        }
        if (fileSize > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过上限 " + (MAX_FILE_SIZE / 1024 / 1024 / 1024) + "GB");
        }
    }
}
