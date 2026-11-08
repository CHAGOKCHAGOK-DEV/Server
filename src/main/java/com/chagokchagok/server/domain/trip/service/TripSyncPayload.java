package com.chagokchagok.server.domain.trip.service;

import com.chagokchagok.server.domain.trip.dto.TripSyncRequest;
import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import com.chagokchagok.server.domain.trip.exception.TripErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TripSyncPayload {

    private static final Instant MIN_TIMESTAMP = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_TIMESTAMP = Instant.parse("9999-12-31T23:59:59.999999Z");
    private static final Set<TripEndReason> NORMAL_REASONS = EnumSet.of(
            TripEndReason.PARKING_BRAKE, TripEndReason.STOP_TIMEOUT, TripEndReason.MANUAL_STOP);

    public void validate(TripSyncRequest request) {
        if ((request.status() == TripStatus.COMPLETED) != NORMAL_REASONS.contains(request.endReason())) {
            invalid();
        }
        validatePeriod(request.startedAt(), request.endedAt());
        Set<UUID> ids = new HashSet<>();
        for (TripSyncRequest.Result result : request.missionResults()) {
            validatePeriod(result.startedAt(), result.endedAt());
            if (!ids.add(result.resultId())
                    || result.startedAt().isBefore(request.startedAt())
                    || result.endedAt().isAfter(request.endedAt())
                    || (!result.participated() && result.correct() != null)) {
                invalid();
            }
        }
    }

    private void validatePeriod(Instant start, Instant end) {
        if (end.isBefore(start) || start.isBefore(MIN_TIMESTAMP) || end.isAfter(MAX_TIMESTAMP)
                || start.getNano() % 1000 != 0 || end.getNano() % 1000 != 0) {
            invalid();
        }
    }

    private void invalid() {
        throw new GeneralException(TripErrorCode.INVALID_TRIP);
    }

    public String hash(TripSyncRequest request) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                // 인코딩 변경 시 버전 식별자 변경 및 기존 해시 호환 정책 필요
                out.writeUTF("trip-sync-v1");
                out.writeUTF(request.tripId().toString());
                out.writeUTF(request.missionPackCode());
                out.writeInt(request.missionPackVersion());
                out.writeUTF(request.status().name());
                out.writeUTF(request.endReason().name());
                out.writeUTF(request.startedAt().toString());
                out.writeUTF(request.endedAt().toString());
                out.writeInt(request.missionResults().size());
                for (TripSyncRequest.Result result : request.missionResults().stream()
                        .sorted(Comparator.comparing(r -> r.resultId().toString())).toList()) {
                    out.writeUTF(result.resultId().toString());
                    out.writeUTF(result.missionCode());
                    out.writeUTF(result.status().name());
                    out.writeBoolean(result.participated());
                    out.writeByte(result.correct() == null ? 0 : result.correct() ? 1 : 2);
                    out.writeUTF(result.startedAt().toString());
                    out.writeUTF(result.endedAt().toString());
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot calculate trip payload hash", exception);
        }
    }
}
