package com.chagokchagok.server.domain.trip.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "TripSyncResponse", description = "주행 동기화 결과 응답")
public record TripSyncResponse(UUID tripId, Instant syncedAt, int missionResultCount) {
}
