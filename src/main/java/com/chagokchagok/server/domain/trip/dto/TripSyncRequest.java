package com.chagokchagok.server.domain.trip.dto;

import com.chagokchagok.server.domain.trip.entity.MissionResultStatus;
import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "TripSyncRequest", description = "완료된 주행과 미션 결과 동기화 요청")
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
    @Schema(name = "TripSyncResult", description = "주행 중 수행한 미션 결과")
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
