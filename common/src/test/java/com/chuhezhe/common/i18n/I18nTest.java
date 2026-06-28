package com.chuhezhe.common.i18n;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class I18nTest {

    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        source.setFallbackToSystemLocale(false);
        this.messageSource = source;
    }

    @Test
    void zhErrorMessage() {
        String msg = messageSource.getMessage("error.quota_exceeded", null, Locale.SIMPLIFIED_CHINESE);
        assertEquals("存储空间已满", msg);
    }

    @Test
    void enErrorMessage() {
        String msg = messageSource.getMessage("error.quota_exceeded", null, Locale.ENGLISH);
        assertEquals("storage quota exceeded", msg);
    }

    @Test
    void zhAccountExists() {
        String msg = messageSource.getMessage("error.account_exists", null, Locale.SIMPLIFIED_CHINESE);
        assertEquals("账号已存在", msg);
    }

    @Test
    void enAccountExists() {
        String msg = messageSource.getMessage("error.account_exists", null, Locale.ENGLISH);
        assertEquals("account already exists", msg);
    }

    @Test
    void fallbackToKeyWhenMissing() {
        // 不存在的 key 返回 key 本身
        String msg = messageSource.getMessage("error.nonexistent", null, "error.nonexistent", Locale.ENGLISH);
        assertEquals("error.nonexistent", msg);
    }

    @Test
    void defaultToZh() {
        // 不支持的语言回退到默认 zh
        String msg = messageSource.getMessage("error.quota_exceeded", null, Locale.FRENCH);
        assertEquals("存储空间已满", msg);
    }
}
