package com.chagokchagok.server.domain.trip.exception;

import com.chagokchagok.server.global.exception.BaseErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum TripErrorCode implements BaseErrorCode {
    INVALID_TRIP(HttpStatus.BAD_REQUEST, "TRIP_400", "주행 상태, 시간 또는 미션 결과가 올바르지 않습니다."),
    INVALID_CONTENT(HttpStatus.BAD_REQUEST, "TRIP_400_1", "사용할 수 없는 미션팩 버전 또는 미션 코드입니다."),
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_404", "주행 기록을 찾을 수 없습니다."),
    SYNC_CONFLICT(HttpStatus.CONFLICT, "TRIP_409", "같은 식별자로 다른 주행 또는 미션 결과가 저장되어 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    TripErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
