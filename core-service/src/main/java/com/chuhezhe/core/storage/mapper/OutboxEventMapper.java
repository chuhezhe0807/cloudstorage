package com.chuhezhe.core.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuhezhe.core.storage.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
}
