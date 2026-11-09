package com.chagokchagok.server.domain.mission.controller;

import com.chagokchagok.server.domain.mission.dto.MissionPackResponse;
import com.chagokchagok.server.domain.mission.service.MissionPackService;
import com.chagokchagok.server.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/mission-packs")
public class MissionPackController {

    private final MissionPackService missionPacks;

    @Operation(summary = "현재 사용할 미션팩 조회",
            description = "인증 없이 호출할 수 있습니다. 발행된 미션팩 중 클라이언트와 서버가 모두 지원하는 스키마 버전에서 가장 높은 콘텐츠 버전을 반환합니다.")
    @GetMapping("/current")
    public ApiResponse<MissionPackResponse> current(
            @Parameter(description = "미션팩 코드", example = "DEFAULT")
            @RequestParam(defaultValue = "DEFAULT") @NotBlank String code,
            @Parameter(description = "클라이언트가 지원하는 최대 JSON 스키마 버전", example = "1")
            @RequestParam(defaultValue = "1") @Min(1) int supportedSchemaVersion) {
        return ApiResponse.success(missionPacks.current(code, supportedSchemaVersion));
    }
}
