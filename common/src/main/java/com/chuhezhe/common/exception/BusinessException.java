package com.chuhezhe.common.exception;

import com.chuhezhe.common.result.ErrorCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;
    private final String i18nKey;
    private final Object[] args;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getI18nKey());
        this.code = errorCode.getCode();
        this.i18nKey = errorCode.getI18nKey();
        this.args = null;
    }

    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.getI18nKey());
        this.code = errorCode.getCode();
        this.i18nKey = errorCode.getI18nKey();
        this.args = args;
    }
}
