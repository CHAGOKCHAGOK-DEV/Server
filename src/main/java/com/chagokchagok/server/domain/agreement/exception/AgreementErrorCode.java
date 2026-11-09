package com.chagokchagok.server.domain.agreement.exception;

import com.chagokchagok.server.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AgreementErrorCode implements BaseErrorCode {
    INVALID_CONSENT(HttpStatus.BAD_REQUEST, "AGREEMENT_400", "동의 행위, 발생 시각 또는 이벤트 구성이 올바르지 않습니다."),
    INVALID_DOCUMENT(HttpStatus.BAD_REQUEST, "AGREEMENT_400_1", "동의할 수 없는 문서 코드 또는 버전입니다."),
    CONSENT_NOT_FOUND(HttpStatus.NOT_FOUND, "AGREEMENT_404", "동의 이력을 찾을 수 없습니다."),
    CONSENT_CONFLICT(HttpStatus.CONFLICT, "AGREEMENT_409", "같은 이벤트 식별자로 다른 동의 이력이 저장되어 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    AgreementErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
