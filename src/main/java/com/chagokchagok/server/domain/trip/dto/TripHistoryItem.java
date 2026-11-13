package com.chagokchagok.server.domain.trip.dto;

import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "TripHistoryItem", description = "주행 이력 목록 항목")
public record TripHistoryItem(
        UUID tripId,
        String missionPackCode,
        int missionPackVersion,
        TripStatus status,
        TripEndReason endReason,
        Instant startedAt,
        Instant endedAt,
        Instant syncedAt,
        TripResultSummary summary
) {
}
