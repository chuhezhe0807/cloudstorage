package com.chuhezhe.core.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.mq.RabbitMqConstants;
import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.ErrorCode;
import com.chuhezhe.core.file.entity.FileMeta;
import com.chuhezhe.core.file.mapper.FileMetaMapper;
import com.chuhezhe.core.kb.dto.KbProgressVO;
import com.chuhezhe.core.kb.entity.KnowledgeBase;
import com.chuhezhe.core.kb.mapper.KnowledgeBaseMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class KbServiceImpl implements KbService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final FileMetaMapper fileMetaMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Long triggerVectorize(Long fileId) {
        Long tenantId = TenantContext.getTenantId();
        Long userId = TenantContext.getUserId();
        if (tenantId == null) {
            tenantId = 0L;
        }

        FileMeta folder = fileMetaMapper.selectById(fileId);
        if (folder == null || !Boolean.TRUE.equals(folder.getIsDir())) {
            throw new BusinessException(ErrorCode.NOT_A_DIRECTORY);
        }

        KnowledgeBase existing = knowledgeBaseMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getTenantId, tenantId)
                        .eq(KnowledgeBase::getFileId, fileId)
        );

        if (existing != null) {
            if ("processing".equals(existing.getStatus())) {
                return existing.getId();
            }
            existing.setStatus("processing");
            existing.setProcessedFiles(0);
            existing.setUpdatedAt(LocalDateTime.now());
            knowledgeBaseMapper.updateById(existing);
            sendKbIndexMessage(existing.getId(), tenantId);
            return existing.getId();
        }

        List<FileMeta> allFiles = collectAllFiles(fileId, tenantId);
        int total = allFiles.size();

        KnowledgeBase kb = new KnowledgeBase();
        kb.setTenantId(tenantId);
        kb.setOwnerId(userId != null ? userId : 0L);
        kb.setFileId(fileId);
        kb.setName(folder.getName());
        kb.setStatus("processing");
        kb.setTotalFiles(total);
        kb.setProcessedFiles(0);
        knowledgeBaseMapper.insert(kb);

        sendKbIndexMessage(kb.getId(), tenantId);
        return kb.getId();
    }

    @Override
    public List<KbProgressVO> getProgress() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            tenantId = 0L;
        }
        List<KnowledgeBase> kbList = knowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getTenantId, tenantId)
                        .orderByDesc(KnowledgeBase::getCreatedAt)
        );

        List<KbProgressVO> result = new ArrayList<>();
        for (KnowledgeBase kb : kbList) {
            result.add(toProgressVO(kb));
        }
        return result;
    }

    @Override
    public KbProgressVO getProgressDetail(Long kbId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.KB_NOT_FOUND);
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.equals(kb.getTenantId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return toProgressVO(kb);
    }

    private List<FileMeta> collectAllFiles(Long folderId, Long tenantId) {
        List<FileMeta> allFiles = new ArrayList<>();
        List<FileMeta> children = fileMetaMapper.listByParent(folderId);
        for (FileMeta child : children) {
            if (Boolean.TRUE.equals(child.getIsDir())) {
                allFiles.addAll(collectAllFiles(child.getId(), tenantId));
            } else {
                allFiles.add(child);
            }
        }
        return allFiles;
    }

    private void sendKbIndexMessage(Long kbId, Long tenantId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("kbId", kbId);
            message.put("tenantId", tenantId);
            message.put("attemptId", UUID.randomUUID().toString());
            String json = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(RabbitMqConstants.EXCHANGE,
                    RabbitMqConstants.KB_INDEX_ROUTING_KEY, json);
            log.info("Sent kb.index.request: kbId={}, attemptId={}", kbId, message.get("attemptId"));
        } catch (Exception e) {
            log.error("Failed to send kb.index.request: kbId={}", kbId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private KbProgressVO toProgressVO(KnowledgeBase kb) {
        KbProgressVO vo = new KbProgressVO();
        vo.setKbId(kb.getId());
        vo.setFileId(kb.getFileId());
        vo.setFolderName(kb.getName());
        vo.setTotalFiles(kb.getTotalFiles());
        vo.setProcessedFiles(kb.getProcessedFiles());
        vo.setStatus(kb.getStatus());
        if (kb.getFailedFilesJson() != null) {
            try {
                List<KbProgressVO.FailedFile> failedFiles = objectMapper.readValue(
                        kb.getFailedFilesJson(),
                        new TypeReference<List<KbProgressVO.FailedFile>>() {}
                );
                vo.setFailedFiles(failedFiles);
            } catch (Exception e) {
                log.warn("Failed to parse failed_files JSON for kbId={}", kb.getId(), e);
            }
        }
        return vo;
    }
}
