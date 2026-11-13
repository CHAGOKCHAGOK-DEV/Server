package com.chagokchagok.server.domain.trip.dto;

import com.chagokchagok.server.domain.trip.entity.MissionResultStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "TripMissionResultItem", description = "주행 상세의 미션 결과")
public record TripMissionResultItem(
        UUID resultId,
        String missionCode,
        MissionResultStatus status,
        boolean participated,
        Boolean correct,
        Instant startedAt,
        Instant endedAt
) {
}
