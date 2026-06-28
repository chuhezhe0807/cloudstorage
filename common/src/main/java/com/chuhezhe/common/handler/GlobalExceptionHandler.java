package com.chuhezhe.common.handler;

import com.chuhezhe.common.exception.BusinessException;
import com.chuhezhe.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;

/**
 * 全局异常处理器：BusinessException 按 errorCode 输出 i18n 消息。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        Locale locale = resolveLocale(request);
        String message = messageSource.getMessage(ex.getI18nKey(), ex.getArgs(), ex.getI18nKey(), locale);
        log.warn("业务异常: code={}, key={}, message={}", ex.getCode(), ex.getI18nKey(), message);

        HttpStatus status = mapHttpStatus(ex.getCode());
        return ResponseEntity.status(status).body(Result.fail(ex.getCode(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("未处理异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "Internal Server Error"));
    }

    /** Locale 解析，与 LocaleConfig 保持相同优先级 */
    private Locale resolveLocale(HttpServletRequest request) {
        String localeHeader = request.getHeader("X-Locale");
        if (localeHeader != null) {
            if (localeHeader.startsWith("en")) return Locale.ENGLISH;
            if (localeHeader.startsWith("zh")) return Locale.SIMPLIFIED_CHINESE;
        }
        String header = request.getHeader("Accept-Language");
        if (header != null) {
            if (header.toLowerCase().startsWith("en")) return Locale.ENGLISH;
            if (header.contains("zh")) return Locale.SIMPLIFIED_CHINESE;
        }
        return java.util.Locale.SIMPLIFIED_CHINESE;
    }

    private HttpStatus mapHttpStatus(int code) {
        if (code >= 1000 && code < 2000) return HttpStatus.CONFLICT;
        if (code >= 2000 && code < 3000) return HttpStatus.BAD_REQUEST;
        if (code >= 3000 && code < 4000) return HttpStatus.BAD_REQUEST;
        if (code >= 4000 && code < 5000) return HttpStatus.GONE;
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
