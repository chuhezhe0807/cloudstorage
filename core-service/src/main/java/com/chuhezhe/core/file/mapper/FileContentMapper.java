package com.chuhezhe.core.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuhezhe.core.file.entity.FileContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileContentMapper extends BaseMapper<FileContent> {

    /** 按租户+hash查找已有文件内容（秒传判断） */
    @Select("SELECT * FROM file_content WHERE hash = #{hash}")
    FileContent findByHash(String hash);
}
