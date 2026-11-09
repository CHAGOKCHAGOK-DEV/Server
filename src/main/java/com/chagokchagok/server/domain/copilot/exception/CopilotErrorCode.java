package com.chagokchagok.server.domain.copilot.exception;

import com.chagokchagok.server.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum CopilotErrorCode implements BaseErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "COPILOT_404", "부기장 프로필이 없습니다."),
    ALREADY_EXISTS(HttpStatus.CONFLICT, "COPILOT_409", "부기장 프로필이 이미 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    CopilotErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
