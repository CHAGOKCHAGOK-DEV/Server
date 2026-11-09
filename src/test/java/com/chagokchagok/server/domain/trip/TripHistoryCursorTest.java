package com.chagokchagok.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chagokchagok.server.domain.trip.service.TripHistoryCursor;
import com.chagokchagok.server.global.exception.GeneralException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TripHistoryCursorTest {

    private static final UUID TRIP = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant MIN = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX = Instant.parse("9999-12-31T23:59:59.999999Z");

    @Test
    void roundTripsMicrosecondPositionsAcrossSupportedRange() {
        for (Instant at : List.of(Instant.parse("2026-11-01T00:00:00.123456Z"),
                Instant.parse("1969-12-31T23:59:59.999999Z"), MIN, MAX)) {
            var cursor = new TripHistoryCursor(at, TRIP);
            String encoded = cursor.encode();
            assertThat(encoded).hasSize(34).matches("[A-Za-z0-9_-]+");
            assertThat(TripHistoryCursor.decode(encoded)).isEqualTo(cursor);
        }
    }

    @Test
    void rejectsMalformedNonCanonicalUnknownVersionAndOutOfRangeCursor() {
        String valid = new TripHistoryCursor(Instant.parse("2026-11-01T00:00:00Z"), TRIP).encode();
        String nonCanonical = valid.substring(0, 33) + (char) (valid.charAt(33) + 1);
        // 재인코딩 비교만이 거부 근거가 되는 입력인지 전제 확인
        assertThat(valid).endsWith("w");
        assertThat(Base64.getUrlDecoder().decode(nonCanonical)).isEqualTo(Base64.getUrlDecoder().decode(valid));

        for (String value : List.of("", "abc", valid + "=", valid + "A", valid.substring(1), "*" + valid.substring(1),
                nonCanonical, raw(2, 0L), raw(1, Long.MAX_VALUE), raw(1, Long.MIN_VALUE),
                raw(1, micros(MIN) - 1), raw(1, micros(MAX) + 1))) {
            assertThatThrownBy(() -> TripHistoryCursor.decode(value)).isInstanceOfSatisfying(GeneralException.class,
                    exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("TRIP_400_2"));
        }
        assertThat(TripHistoryCursor.decode(raw(1, micros(MIN))).startedAt()).isEqualTo(MIN);
        assertThat(TripHistoryCursor.decode(raw(1, micros(MAX))).startedAt()).isEqualTo(MAX);
    }

    private static long micros(Instant instant) {
        return ChronoUnit.MICROS.between(Instant.EPOCH, instant);
    }

    private String raw(int version, long micros) {
        ByteBuffer buffer = ByteBuffer.allocate(25).put((byte) version).putLong(micros)
                .putLong(TRIP.getMostSignificantBits()).putLong(TRIP.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }
}
