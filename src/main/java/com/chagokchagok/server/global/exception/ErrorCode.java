package com.chagokchagok.server.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode implements BaseErrorCode {

    INVALID_INPUT_VALUE(
        HttpStatus.BAD_REQUEST,
        "COMMON_400",
        "입력값이 올바르지 않습니다."
    ),

    INVALID_REQUEST_BODY(
        HttpStatus.BAD_REQUEST,
        "COMMON_400_1",
        "요청 본문을 읽을 수 없습니다."
    ),

    MISSING_REQUEST_PARAMETER(
        HttpStatus.BAD_REQUEST,
        "COMMON_400_2",
        "필수 요청 파라미터가 누락되었습니다."
    ),

    TYPE_MISMATCH(
        HttpStatus.BAD_REQUEST,
        "COMMON_400_3",
        "요청 값의 타입이 올바르지 않습니다."
    ),

    UNAUTHORIZED(
        HttpStatus.UNAUTHORIZED,
        "COMMON_401",
        "인증이 필요합니다."
    ),

    FORBIDDEN(
        HttpStatus.FORBIDDEN,
        "COMMON_403",
        "접근 권한이 없습니다."
    ),

    RESOURCE_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "COMMON_404",
        "요청한 리소스를 찾을 수 없습니다."
    ),

    METHOD_NOT_ALLOWED(
        HttpStatus.METHOD_NOT_ALLOWED,
        "COMMON_405",
        "지원하지 않는 HTTP 메서드입니다."
    ),

    DATA_INTEGRITY_VIOLATION(
        HttpStatus.CONFLICT,
        "COMMON_409",
        "데이터 무결성 제약 조건을 위반했습니다."
    ),

    UNSUPPORTED_MEDIA_TYPE(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "COMMON_415",
        "지원하지 않는 Content-Type입니다."
    ),

    INTERNAL_SERVER_ERROR(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "COMMON_500",
        "서버 내부 오류가 발생했습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
