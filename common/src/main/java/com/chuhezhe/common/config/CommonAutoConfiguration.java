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

@Configuration
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<TenantContextFilter> tenantContextFilterRegistration() {
        FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantContextFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Configuration
    @ConditionalOnClass(com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor.class)
    public static class MybatisPlusTenantConfig {

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
                                        return new LongValue(0);
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
