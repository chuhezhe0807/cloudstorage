package com.chuhezhe.common.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;

@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver(MessageSource messageSource) {
        return new AcceptLanguageLocaleResolver();
    }

    public static class AcceptLanguageLocaleResolver implements LocaleResolver {

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            String header = request.getHeader("Accept-Language");
            if (header != null && header.toLowerCase().startsWith("en")) {
                return Locale.ENGLISH;
            }
            return Locale.SIMPLIFIED_CHINESE;
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        }
    }
}
