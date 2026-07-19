package com.chuhezhe.core.storage.service;

import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.storage.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileZipService {

    private final FileMetaMapper fileMetaMapper;
    private final MinioService minioService;

    public byte[] zipFiles(List<Long> fileIds) {
        List<FileMeta> topLevelItems = new ArrayList<>();
        Set<Long> topIds = new LinkedHashSet<>();
        for (Long fileId : fileIds) {
            FileMeta meta = fileMetaMapper.selectById(fileId);
            if (meta != null && topIds.add(meta.getId())) {
                topLevelItems.add(meta);
            }
        }

        if (topLevelItems.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        return doZip(topLevelItems);
    }

    private byte[] doZip(List<FileMeta> topLevelItems) {
        record ZipEntryItem(FileMeta file, String entryName) {}
        List<ZipEntryItem> entries = new ArrayList<>();
        Set<Long> addedIds = new HashSet<>();

        for (FileMeta topItem : topLevelItems) {
            if (Boolean.TRUE.equals(topItem.getIsDir())) {
                String dirPrefix = topItem.getPath();
                List<FileMeta> descendants = fileMetaMapper.listByPathPrefix(dirPrefix);
                for (FileMeta d : descendants) {
                    if (!Boolean.TRUE.equals(d.getIsDir()) && addedIds.add(d.getId())) {
                        String relativePath = d.getPath().substring(dirPrefix.length());
                        if (relativePath.endsWith("/")) {
                            relativePath = relativePath.substring(0, relativePath.length() - 1);
                        }
                        String entryName = topItem.getName() + "/" + relativePath;
                        entries.add(new ZipEntryItem(d, entryName));
                    }
                }
            } else {
                if (addedIds.add(topItem.getId())) {
                    entries.add(new ZipEntryItem(topItem, topItem.getName()));
                }
            }
        }

        if (entries.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);

            for (ZipEntryItem ze : entries) {
                zos.putNextEntry(new ZipEntry(ze.entryName()));

                InputStream is = minioService.getObjectStream(ze.file.getContentRef());
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                is.close();
                zos.closeEntry();
            }

            zos.finish();
            zos.close();
            log.info("zip打包完成: fileCount={}", entries.size());
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("生成zip文件失败", e);
        }
    }
}
