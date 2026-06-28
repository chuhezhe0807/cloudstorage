package com.chuhezhe.core.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuhezhe.core.file.entity.FileMeta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileMetaMapper extends BaseMapper<FileMeta> {

    /** 查询指定目录下的直接子节点（排除回收站） */
    @Select("SELECT * FROM file_meta WHERE parent_id = #{parentId} AND status = 'active' ORDER BY is_dir DESC, name")
    List<FileMeta> listByParent(Long parentId);

    /** 目录下是否有同名节点 */
    @Select("SELECT COUNT(*) FROM file_meta WHERE parent_id = #{parentId} AND name = #{name} AND status = 'active'")
    int countByNameInParent(Long parentId, String name);

    /** 物化路径前缀查询（用于移动子树） */
    @Select("SELECT * FROM file_meta WHERE path LIKE #{prefix} || '%' AND status = 'active'")
    List<FileMeta> listByPathPrefix(String prefix);

    /** 回收站列表 */
    @Select("SELECT * FROM file_meta WHERE status = 'recycled' AND deleted_at > #{cutoff} ORDER BY deleted_at DESC")
    List<FileMeta> listRecycled(java.time.LocalDateTime cutoff);

    /** 过期回收站项（30天前删除） */
    @Select("SELECT * FROM file_meta WHERE status = 'recycled' AND deleted_at < #{cutoff} LIMIT #{limit}")
    List<FileMeta> listExpiredRecycled(java.time.LocalDateTime cutoff, int limit);
}
