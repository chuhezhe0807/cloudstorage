package com.chuhezhe.common.result;

import lombok.Getter;

@Getter
public enum ErrorCode {

    SUCCESS(200, "success"),

    UNAUTHORIZED(401, "error.unauthorized"),
    TOKEN_EXPIRED(401, "error.token_expired"),
    FORBIDDEN(403, "error.forbidden"),
    NOT_FOUND(404, "error.not_found"),
    CONFLICT(409, "error.conflict"),
    TOO_MANY_REQUESTS(429, "error.too_many_requests"),
    INTERNAL_ERROR(500, "error.internal"),

    ACCOUNT_EXISTS(1001, "error.account_exists"),
    ACCOUNT_LOCKED(1002, "error.account_locked"),
    INVALID_CREDENTIALS(1003, "error.invalid_credentials"),
    INVALID_REFRESH_TOKEN(1004, "error.invalid_refresh_token"),

    TENANT_NOT_FOUND(1101, "error.tenant_not_found"),

    NAME_CONFLICT(2001, "error.name_conflict"),
    FILE_NOT_FOUND(2002, "error.file_not_found"),
    NOT_A_DIRECTORY(2003, "error.not_a_directory"),
    CONCURRENT_MOVE(2004, "error.concurrent_move"),

    INVALID_FILENAME(3001, "error.invalid_filename"),
    FILE_TYPE_NOT_ALLOWED(3002, "error.file_type_not_allowed"),
    FILE_TOO_LARGE(3003, "error.file_too_large"),
    CHUNK_MISSING(3004, "error.chunk_missing"),
    UPLOAD_NOT_FOUND(3005, "error.upload_not_found"),
    QUOTA_EXCEEDED(3006, "error.quota_exceeded"),

    SHARE_EXPIRED(4001, "error.share_expired"),
    SHARE_EXHAUSTED(4002, "error.share_exhausted"),
    SHARE_LOCKED(4003, "error.share_locked"),
    INVALID_SHARE_CODE(4004, "error.invalid_share_code"),
    INVALID_SHARE_PASSWORD(4005, "error.invalid_share_password"),
    SHARE_NOT_FOUND(4006, "error.share_not_found"),

    KB_NOT_READY(5001, "error.kb_not_ready"),
    KB_NOT_FOUND(5002, "error.kb_not_found"),
    UNSUPPORTED_FILE_TYPE(5003, "error.unsupported_file_type"),
    VECTORIZE_FAILED(5004, "error.vectorize_failed"),
    LLM_CALL_FAILED(5005, "error.llm_call_failed");

    private final int code;
    private final String i18nKey;

    ErrorCode(int code, String i18nKey) {
        this.code = code;
        this.i18nKey = i18nKey;
    }
}
