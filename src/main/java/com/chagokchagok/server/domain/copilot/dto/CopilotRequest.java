package com.chagokchagok.server.domain.copilot.dto;

import com.chagokchagok.server.domain.copilot.entity.AgeGroup;
import com.chagokchagok.server.domain.copilot.entity.MissionIntensity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(name = "CopilotRequest", description = "부기장 프로필 생성·수정 요청")
public record CopilotRequest(
        @NotNull @Pattern(regexp = "[가-힣A-Za-z0-9]{1,8}") String nickname,
        @NotNull AgeGroup ageGroup,
        @NotNull MissionIntensity missionIntensity
) {
}
