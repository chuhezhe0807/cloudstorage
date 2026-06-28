package com.chuhezhe.common.config;

import com.chuhezhe.common.context.TenantContext;
import com.chuhezhe.common.context.TenantContextFilter;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Set;

/**
 * 公共自动配置：注册 TenantContextFilter + MyBatis-Plus 多租户插件。
 */
@Configuration
public class CommonAutoConfiguration {

    /** 注册 TenantContextFilter，仅 Servlet 环境生效（Gateway 使用 Reactive） */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration() {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantContextFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * MyBatis-Plus 多租户插件：自动为 SQL 注入 WHERE tenant_id = ?。
     * 仅在有 MybatisPlusInterceptor 的类路径时激活（Gateway 不激活）。
     */
    @Configuration
    @ConditionalOnClass(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class)
    public static class MybatisPlusTenantConfig {

        // 不注入 tenant_id 过滤的表：tenant/user 需跨租户查询，share_link 需访客访问，outbox_event 需全局扫描
        private static final Set<String> IGNORE_TABLES = Set.of("tenant", "user", "share_link", "outbox_event");

        @Bean
        public com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor mybatisPlusInterceptor() {
            com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor interceptor =
                    new com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor();

            com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor tenantInterceptor =
                    new com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor(
                            new com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler() {
                                @Override
                                public Expression getTenantId() {
                                    Long tenantId = TenantContext.getTenantId();
                                    if (tenantId == null) {
                                        return new LongValue(0); // 无租户上下文时填0，避免 NPE
                                    }
                                    return new LongValue(tenantId);
                                }

                                @Override
                                public String getTenantIdColumn() {
                                    return "tenant_id";
                                }

                                @Override
                                public boolean ignoreTable(String tableName) {
                                    return IGNORE_TABLES.contains(tableName.toLowerCase());
                                }
                            });
            interceptor.addInnerInterceptor(tenantInterceptor);
            return interceptor;
        }
    }
}
