package com.chagokchagok.server.domain.trip.dto;

import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TripDetailResponse(
        UUID tripId,
        String missionPackCode,
        int missionPackVersion,
        TripStatus status,
        TripEndReason endReason,
        Instant startedAt,
        Instant endedAt,
        Instant syncedAt,
        TripResultSummary summary,
        List<TripMissionResultItem> missionResults
) {
    public static TripDetailResponse of(TripHistoryItem trip, List<TripMissionResultItem> results) {
        return new TripDetailResponse(trip.tripId(), trip.missionPackCode(), trip.missionPackVersion(), trip.status(),
                trip.endReason(), trip.startedAt(), trip.endedAt(), trip.syncedAt(), trip.summary(), results);
    }
}
