package com.chuhezhe.core.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuhezhe.core.share.entity.ShareLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ShareLinkMapper extends BaseMapper<ShareLink> {

    /** 按 code 查询（全局唯一，无需 tenant_id） */
    @Select("SELECT * FROM share_link WHERE code = #{code}")
    ShareLink findByCode(String code);
}
