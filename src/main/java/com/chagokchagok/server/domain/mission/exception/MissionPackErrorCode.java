package com.chagokchagok.server.domain.mission.exception;

import com.chagokchagok.server.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum MissionPackErrorCode implements BaseErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "MISSION_PACK_404", "배포된 미션팩이 없습니다."),
    UNSUPPORTED_SCHEMA(HttpStatus.NOT_ACCEPTABLE, "MISSION_PACK_406", "지원하는 스키마 버전의 미션팩이 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    MissionPackErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
