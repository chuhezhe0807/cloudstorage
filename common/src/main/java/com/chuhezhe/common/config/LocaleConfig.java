package com.chuhezhe.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;

/**
 * 国际化 Locale 解析器。
 * 优先级：网关 X-Locale 头（JWT中用户偏好） > Accept-Language 请求头 > 默认 zh。
 * 仅在 SERVLET（WebMVC）环境下生效，WebFlux（Gateway）下不加载。
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
@ConditionalOnClass(LocaleResolver.class)
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver(MessageSource messageSource) {
        return new AcceptLanguageLocaleResolver();
    }

    public static class AcceptLanguageLocaleResolver implements LocaleResolver {

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            // 1. 优先来自网关注入的 X-Locale（JWT 中的用户偏好）
            String localeHeader = request.getHeader("X-Locale");
            if (localeHeader != null) {
                if (localeHeader.startsWith("en")) return Locale.ENGLISH;
                if (localeHeader.startsWith("zh")) return Locale.SIMPLIFIED_CHINESE;
            }

            // 2. 其次 Accept-Language 请求头
            String acceptLang = request.getHeader("Accept-Language");
            if (acceptLang != null) {
                if (acceptLang.toLowerCase().startsWith("en")) return Locale.ENGLISH;
                if (acceptLang.contains("zh")) return Locale.SIMPLIFIED_CHINESE;
            }

            // 3. 默认中文
            return Locale.SIMPLIFIED_CHINESE;
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        }
    }
}
