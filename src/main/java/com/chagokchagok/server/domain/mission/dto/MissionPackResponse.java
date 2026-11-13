package com.chagokchagok.server.domain.mission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

@Schema(name = "MissionPackResponse", description = "현재 사용 가능한 미션팩 응답")
public record MissionPackResponse(
        String code,
        int version,
        int schemaVersion,
        Instant publishedAt,
        List<MissionResponse> missions
) {
    @Schema(name = "MissionResponse", description = "미션팩에 포함된 미션")
    public record MissionResponse(String code, String triggerType, JsonNode config) {
    }
}
