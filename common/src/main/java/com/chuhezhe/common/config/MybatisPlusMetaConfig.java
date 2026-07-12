package com.chuhezhe.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Configuration
@ConditionalOnClass(MetaObjectHandler.class)
public class MybatisPlusMetaConfig {

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
                this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
            }
        };
    }
}
