package com.chagokchagok.server.domain.trip.dto;

import com.chagokchagok.server.domain.trip.entity.MissionResultStatus;
import java.time.Instant;
import java.util.UUID;

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
