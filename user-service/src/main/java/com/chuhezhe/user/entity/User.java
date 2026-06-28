package com.chuhezhe.user.entity;

import com.chuhezhe.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("\"user\"")
public class User extends BaseEntity {

    private Long tenantId;
    private String account;
    private String passwordHash;
    private String role;
    private String locale;
    private String theme;
}
