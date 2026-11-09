package com.chagokchagok.server.domain.trip.controller;

import com.chagokchagok.server.domain.trip.dto.TripDetailResponse;
import com.chagokchagok.server.domain.trip.dto.TripHistoryPageResponse;
import com.chagokchagok.server.domain.trip.service.TripHistoryService;
import com.chagokchagok.server.global.common.response.ApiResponse;
import com.chagokchagok.server.global.exception.ErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import com.chagokchagok.server.global.security.InstallationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TripHistoryController {

    private final TripHistoryService service;

    @Operation(summary = "주행 이력 목록 조회",
            description = "설치 토큰과 X-Installation-Id 헤더가 필요합니다. 시작 시각 최신순으로 반환하며 nextCursor가 null이면 마지막 페이지입니다.",
            security = @SecurityRequirement(name = "Installation Token"),
            parameters = @Parameter(name = "X-Installation-Id", in = ParameterIn.HEADER, required = true,
                    description = "인증할 설치 UUID", schema = @Schema(type = "string", format = "uuid")))
    @GetMapping("/api/v1/trips")
    public ApiResponse<TripHistoryPageResponse> list(
            @AuthenticationPrincipal InstallationPrincipal principal,
            @Parameter(description = "이전 응답의 nextCursor, 첫 페이지는 생략")
            @RequestParam(name = "cursor", required = false) String cursor,
            @Parameter(description = "페이지 크기 1~100")
            @RequestParam(name = "size", defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        if (principal == null) {
            throw new GeneralException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(service.list(principal.installationId(), cursor, size));
    }

    @Operation(summary = "주행 상세 및 미션 결과 조회",
            description = "설치 토큰과 X-Installation-Id 헤더가 필요합니다. 다른 설치의 주행과 존재하지 않는 주행은 모두 404입니다.",
            security = @SecurityRequirement(name = "Installation Token"),
            parameters = @Parameter(name = "X-Installation-Id", in = ParameterIn.HEADER, required = true,
                    description = "인증할 설치 UUID", schema = @Schema(type = "string", format = "uuid")))
    @GetMapping("/api/v1/trips/{tripId}")
    public ApiResponse<TripDetailResponse> detail(
            @AuthenticationPrincipal InstallationPrincipal principal,
            @PathVariable("tripId") UUID tripId
    ) {
        if (principal == null) {
            throw new GeneralException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(service.detail(principal.installationId(), tripId));
    }
}
