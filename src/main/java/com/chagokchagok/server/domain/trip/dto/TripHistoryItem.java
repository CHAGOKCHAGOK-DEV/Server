package com.chagokchagok.server.domain.trip.dto;

import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import java.time.Instant;
import java.util.UUID;

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
