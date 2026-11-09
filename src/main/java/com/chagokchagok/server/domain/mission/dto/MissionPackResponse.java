package com.chagokchagok.server.domain.mission.dto;

import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

public record MissionPackResponse(
        String code,
        int version,
        int schemaVersion,
        Instant publishedAt,
        List<MissionResponse> missions
) {
    public record MissionResponse(String code, String triggerType, JsonNode config) {
    }
}
