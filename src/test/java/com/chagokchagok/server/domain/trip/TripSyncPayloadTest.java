package com.chagokchagok.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static java.util.Map.entry;

import com.chagokchagok.server.domain.trip.dto.TripSyncRequest;
import com.chagokchagok.server.domain.trip.entity.MissionResultStatus;
import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import com.chagokchagok.server.domain.trip.service.TripSyncPayload;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TripSyncPayloadTest {

    private final TripSyncPayload payload = new TripSyncPayload();
    private final Instant start = Instant.parse("2026-11-01T00:00:00Z");
    private final UUID tripId = UUID.randomUUID();
    private static final UUID FIXED_TRIP = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID FIXED_FIRST = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID FIXED_SECOND = UUID.fromString("55555555-5555-4555-8555-555555555555");

    @Test
    void resultOrderAndEquivalentInstantsDoNotChangeHash() {
        var first = result(UUID.randomUUID(), true, null);
        var second = result(UUID.randomUUID(), false, null);
        var original = request(List.of(first, second));
        var reversed = request(List.of(second, first));
        payload.validate(original);
        assertThat(payload.hash(original)).hasSize(64).isEqualTo(payload.hash(reversed));
        var offset = new TripSyncRequest(tripId, "DEFAULT", 1, TripStatus.COMPLETED,
                TripEndReason.MANUAL_STOP, Instant.parse("2026-11-01T09:00:00+09:00"),
                start.plusSeconds(600), List.of(first, second));
        assertThat(payload.hash(offset)).isEqualTo(payload.hash(original));
    }

    @Test
    void distinguishesNullFalseAndTrueCorrectness() {
        UUID id = UUID.randomUUID();
        assertThat(payload.hash(request(List.of(result(id, true, null)))))
                .isNotEqualTo(payload.hash(request(List.of(result(id, true, false)))))
                .isNotEqualTo(payload.hash(request(List.of(result(id, true, true)))));
    }

    @Test
    void rejectsDuplicateResultIds() {
        var result = result(UUID.randomUUID(), true, true);
        assertThatThrownBy(() -> payload.validate(request(List.of(result, result))))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void rejectsCorrectnessWithoutParticipation() {
        assertThatThrownBy(() -> payload.validate(request(List.of(result(UUID.randomUUID(), false, false)))))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    void rejectsNormalStatusWithInterruptedReason() {
        var invalid = new TripSyncRequest(tripId, "DEFAULT", 1, TripStatus.COMPLETED,
                TripEndReason.APP_INTERRUPTED, start, start.plusSeconds(600), List.of());
        assertThatThrownBy(() -> payload.validate(invalid)).isInstanceOf(GeneralException.class);
    }

    @Test
    void rejectsResultOutsideTripAndSubmicrosecondTime() {
        var outside = new TripSyncRequest.Result(UUID.randomUUID(), "V-01", MissionResultStatus.COMPLETED,
                true, true, start.minusSeconds(1), start.plusSeconds(1));
        var imprecise = new TripSyncRequest.Result(UUID.randomUUID(), "V-01", MissionResultStatus.COMPLETED,
                true, true, start.plusNanos(1), start.plusSeconds(1));
        assertThatThrownBy(() -> payload.validate(request(List.of(outside)))).isInstanceOf(GeneralException.class);
        assertThatThrownBy(() -> payload.validate(request(List.of(imprecise)))).isInstanceOf(GeneralException.class);
    }

    @Test
    void rejectsTimestampOutsideDatabaseConversionRange() {
        var invalid = new TripSyncRequest(tripId, "DEFAULT", 1, TripStatus.COMPLETED,
                TripEndReason.MANUAL_STOP, start, Instant.MAX, List.of());
        assertThatThrownBy(() -> payload.validate(invalid)).isInstanceOf(GeneralException.class);
    }

    @Test
    void hashMatchesFixedEncoding() {
        var request = fixedTrip(List.of(fixedSecond(), fixedFirst()));
        payload.validate(request);
        assertThat(payload.hash(request)).isEqualTo("a8a93df8c759c1b10a81878ccd8235a03c86784bd2a683470d69ef9dc2628b60");
    }

    @Test
    void everyHashedFieldChangesHash() {
        var first = fixedFirst();
        var second = fixedSecond();
        String base = payload.hash(fixedTrip(List.of(first, second)));
        Instant end = start.plusSeconds(600);
        Map<String, TripSyncRequest> variants = Map.ofEntries(
                entry("tripId", new TripSyncRequest(UUID.fromString("66666666-6666-4666-8666-666666666666"),
                        "DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP, start, end, List.of(first, second))),
                entry("missionPackCode", fixedTrip("OTHER", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                        start, end, List.of(first, second))),
                entry("missionPackVersion", fixedTrip("DEFAULT", 2, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                        start, end, List.of(first, second))),
                entry("status", fixedTrip("DEFAULT", 1, TripStatus.INTERRUPTED, TripEndReason.MANUAL_STOP,
                        start, end, List.of(first, second))),
                entry("endReason", fixedTrip("DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.PARKING_BRAKE,
                        start, end, List.of(first, second))),
                entry("startedAt", fixedTrip("DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                        start.minusSeconds(1), end, List.of(first, second))),
                entry("endedAt", fixedTrip("DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                        start, end.plusSeconds(1), List.of(first, second))),
                entry("droppedResult", fixedTrip(List.of(first))),
                entry("resultId", fixedTrip(List.of(fixedResult(UUID.fromString("77777777-7777-4777-8777-777777777777"),
                        "V-01", MissionResultStatus.COMPLETED, true, true, 10, 20), second))),
                entry("missionCode", fixedTrip(List.of(fixedResult(FIXED_FIRST, "V-02",
                        MissionResultStatus.COMPLETED, true, true, 10, 20), second))),
                entry("resultStatus", fixedTrip(List.of(fixedResult(FIXED_FIRST, "V-01",
                        MissionResultStatus.CANCELLED, true, true, 10, 20), second))),
                entry("participated", fixedTrip(List.of(first, new TripSyncRequest.Result(FIXED_SECOND, "V-01",
                        MissionResultStatus.EXPIRED, false, null, second.startedAt(), second.endedAt())))),
                entry("correct", fixedTrip(List.of(fixedResult(FIXED_FIRST, "V-01",
                        MissionResultStatus.COMPLETED, true, false, 10, 20), second))),
                entry("resultStartedAt", fixedTrip(List.of(fixedResult(FIXED_FIRST, "V-01",
                        MissionResultStatus.COMPLETED, true, true, 11, 20), second))),
                entry("resultEndedAt", fixedTrip(List.of(fixedResult(FIXED_FIRST, "V-01",
                        MissionResultStatus.COMPLETED, true, true, 10, 21), second))));
        assertThat(variants).allSatisfy((field, variant) ->
                assertThat(payload.hash(variant)).as(field).isNotEqualTo(base));
    }

    private TripSyncRequest fixedTrip(String packCode, int packVersion, TripStatus status, TripEndReason reason,
            Instant startedAt, Instant endedAt, List<TripSyncRequest.Result> results) {
        return new TripSyncRequest(FIXED_TRIP, packCode, packVersion, status, reason, startedAt, endedAt, results);
    }

    private TripSyncRequest fixedTrip(List<TripSyncRequest.Result> results) {
        return fixedTrip("DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                start, start.plusSeconds(600), results);
    }

    private TripSyncRequest.Result fixedResult(UUID id, String code, MissionResultStatus status,
            boolean participated, Boolean correct, int startSecond, int endSecond) {
        return new TripSyncRequest.Result(id, code, status, participated, correct,
                start.plusSeconds(startSecond), start.plusSeconds(endSecond));
    }

    private TripSyncRequest.Result fixedFirst() {
        return fixedResult(FIXED_FIRST, "V-01", MissionResultStatus.COMPLETED, true, true, 10, 20);
    }

    private TripSyncRequest.Result fixedSecond() {
        return new TripSyncRequest.Result(FIXED_SECOND, "V-01", MissionResultStatus.EXPIRED,
                true, null, start.plusSeconds(30).plusNanos(123_000), start.plusSeconds(40));
    }

    private TripSyncRequest request(List<TripSyncRequest.Result> results) {
        return new TripSyncRequest(tripId, "DEFAULT", 1, TripStatus.COMPLETED,
                TripEndReason.MANUAL_STOP, start, start.plusSeconds(600), results);
    }

    private TripSyncRequest.Result result(UUID id, boolean participated, Boolean correct) {
        return new TripSyncRequest.Result(id, "V-01", MissionResultStatus.COMPLETED,
                participated, correct, start.plusSeconds(10), start.plusSeconds(20));
    }
}
