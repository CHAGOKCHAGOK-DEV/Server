package com.chagokchagok.server.domain.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record TripSyncResponse(UUID tripId, Instant syncedAt, int missionResultCount) {
}
