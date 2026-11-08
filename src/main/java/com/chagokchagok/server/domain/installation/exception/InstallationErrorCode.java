package com.chagokchagok.server.domain.installation.exception;

import com.chagokchagok.server.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum InstallationErrorCode implements BaseErrorCode {
    REGISTRATION_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "INSTALLATION_429", "설치 등록 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    InstallationErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
