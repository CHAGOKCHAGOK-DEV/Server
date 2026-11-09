package com.chagokchagok.server.domain.copilot.dto;

import com.chagokchagok.server.domain.copilot.entity.AgeGroup;
import com.chagokchagok.server.domain.copilot.entity.Copilot;
import com.chagokchagok.server.domain.copilot.entity.MissionIntensity;

public record CopilotResponse(String nickname, AgeGroup ageGroup, MissionIntensity missionIntensity) {
    public static CopilotResponse from(Copilot value) {
        return new CopilotResponse(value.getNickname(), value.getAgeGroup(), value.getMissionIntensity());
    }
}
