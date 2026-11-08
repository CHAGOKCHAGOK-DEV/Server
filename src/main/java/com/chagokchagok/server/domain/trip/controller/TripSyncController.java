package com.chagokchagok.server.domain.trip.controller;

import com.chagokchagok.server.domain.trip.dto.TripSyncRequest;
import com.chagokchagok.server.domain.trip.dto.TripSyncResponse;
import com.chagokchagok.server.domain.trip.service.TripSyncService;
import com.chagokchagok.server.global.common.response.ApiResponse;
import com.chagokchagok.server.global.exception.ErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import com.chagokchagok.server.global.security.InstallationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@SecurityScheme(name = "Installation Token", type = SecuritySchemeType.HTTP,
        scheme = "bearer", bearerFormat = "opaque installation token")
public class TripSyncController {

    private final TripSyncService service;

    @Operation(summary = "종료된 주행 및 미션 결과 동기화",
            description = "설치 토큰과 X-Installation-Id 헤더가 필요합니다. 신규 저장과 동일 재전송 모두 최초 기록을 반환합니다.",
            security = @SecurityRequirement(name = "Installation Token"),
            parameters = @Parameter(name = "X-Installation-Id", in = ParameterIn.HEADER, required = true,
                    description = "인증할 설치 UUID", schema = @Schema(type = "string", format = "uuid")))
    @PostMapping("/api/v1/trips/sync")
    public ApiResponse<TripSyncResponse> sync(
            @AuthenticationPrincipal InstallationPrincipal principal,
            @Valid @RequestBody TripSyncRequest request
    ) {
        if (principal == null) {
            throw new GeneralException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(service.sync(principal.installationId(), request));
    }
}
