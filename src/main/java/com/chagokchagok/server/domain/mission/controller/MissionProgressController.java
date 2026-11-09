package com.chagokchagok.server.domain.mission.controller;

import com.chagokchagok.server.domain.mission.dto.DailyMissionProgressResponse;
import com.chagokchagok.server.domain.mission.service.MissionProgressService;
import com.chagokchagok.server.global.common.response.ApiResponse;
import com.chagokchagok.server.global.exception.ErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import com.chagokchagok.server.global.security.InstallationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MissionProgressController {

    private final MissionProgressService service;

    @Operation(summary = "일일 미션 수행 현황 조회",
            description = "설치 토큰과 X-Installation-Id 헤더가 필요합니다. 각 미션 결과의 시작 시각을 Asia/Seoul 날짜로 귀속하여 여러 주행을 합산하며 달성 여부는 판정하지 않습니다.",
            security = @SecurityRequirement(name = "Installation Token"),
            parameters = @Parameter(name = "X-Installation-Id", in = ParameterIn.HEADER, required = true,
                    description = "인증할 설치 UUID", schema = @Schema(type = "string", format = "uuid")))
    @GetMapping("/api/v1/missions/daily-progress")
    public ApiResponse<DailyMissionProgressResponse> daily(
            @AuthenticationPrincipal InstallationPrincipal principal,
            @Parameter(description = "Asia/Seoul 기준 날짜 YYYY-MM-DD, 생략 시 오늘", example = "2026-11-08")
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        if (principal == null) {
            throw new GeneralException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(service.daily(principal.installationId(), date));
    }
}
