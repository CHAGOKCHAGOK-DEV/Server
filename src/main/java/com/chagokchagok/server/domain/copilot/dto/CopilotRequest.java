package com.chagokchagok.server.domain.copilot.dto;

import com.chagokchagok.server.domain.copilot.entity.AgeGroup;
import com.chagokchagok.server.domain.copilot.entity.MissionIntensity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CopilotRequest(
        @NotNull @Pattern(regexp = "[가-힣A-Za-z0-9]{1,8}") String nickname,
        @NotNull AgeGroup ageGroup,
        @NotNull MissionIntensity missionIntensity
) {
}
