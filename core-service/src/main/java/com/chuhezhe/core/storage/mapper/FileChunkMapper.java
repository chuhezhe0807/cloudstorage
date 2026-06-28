package com.chuhezhe.core.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuhezhe.core.storage.entity.FileChunk;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileChunkMapper extends BaseMapper<FileChunk> {
}
