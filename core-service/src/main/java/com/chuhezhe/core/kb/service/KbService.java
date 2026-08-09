package com.chuhezhe.core.kb.service;

import com.chuhezhe.core.kb.dto.KbProgressVO;

import java.util.List;

public interface KbService {

    Long triggerVectorize(Long fileId);

    List<KbProgressVO> getProgress();

    KbProgressVO getProgressDetail(Long kbId);
}
