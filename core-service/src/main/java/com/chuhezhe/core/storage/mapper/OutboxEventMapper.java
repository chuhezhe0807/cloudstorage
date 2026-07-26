package com.chuhezhe.core.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuhezhe.core.storage.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("SELECT * FROM outbox_event WHERE status = 'pending' ORDER BY created_at ASC LIMIT #{limit}")
    List<OutboxEvent> selectPendingEvents(@Param("limit") int limit);

    @Update("UPDATE outbox_event SET status = 'sent', updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markSent(@Param("id") Long id);

    @Update("UPDATE outbox_event SET retries = #{retries}, status = CASE WHEN #{retries} >= #{maxRetries} THEN 'failed' ELSE 'pending' END, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markRetryOrFailed(@Param("id") Long id, @Param("retries") int retries, @Param("maxRetries") int maxRetries);
}
