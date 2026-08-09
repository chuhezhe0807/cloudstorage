package com.chuhezhe.core.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuhezhe.core.kb.entity.KbChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    List<KbChunk> searchTopK(@Param("kbId") Long kbId, @Param("queryVector") String queryVector, @Param("k") int k);

    String toVectorLiteral(@Param("values") float[] values);
}
