package com.chagokchagok.server.domain.trip.dto;

import com.chagokchagok.server.domain.trip.entity.MissionResultStatus;
import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TripSyncRequest(
        @NotNull UUID tripId,
        @NotBlank @Size(max = 50) String missionPackCode,
        @NotNull @Positive Integer missionPackVersion,
        @NotNull TripStatus status,
        @NotNull TripEndReason endReason,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @NotNull @Size(max = 1000) List<@NotNull @Valid Result> missionResults
) {
    public record Result(
            @NotNull UUID resultId,
            @NotBlank @Size(max = 50) String missionCode,
            @NotNull MissionResultStatus status,
            @NotNull Boolean participated,
            Boolean correct,
            @NotNull Instant startedAt,
            @NotNull Instant endedAt
    ) {
    }
}
