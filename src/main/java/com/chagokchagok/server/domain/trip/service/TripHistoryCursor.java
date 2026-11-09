package com.chagokchagok.server.domain.trip.service;

import com.chagokchagok.server.domain.trip.exception.TripErrorCode;
import com.chagokchagok.server.global.exception.GeneralException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record TripHistoryCursor(Instant startedAt, UUID tripId) {

    private static final byte VERSION = 1;
    private static final int BYTE_LENGTH = 25;
    private static final int ENCODED_LENGTH = 34;
    private static final long MIN_MICROS = toMicros(Instant.parse("0001-01-01T00:00:00Z"));
    private static final long MAX_MICROS = toMicros(Instant.parse("9999-12-31T23:59:59.999999Z"));

    public String encode() {
        ByteBuffer buffer = ByteBuffer.allocate(BYTE_LENGTH).put(VERSION).putLong(toMicros(startedAt))
                .putLong(tripId.getMostSignificantBits()).putLong(tripId.getLeastSignificantBits());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
    }

    public static TripHistoryCursor decode(String value) {
        if (value.length() != ENCODED_LENGTH) {
            throw invalid();
        }
        byte[] bytes;
        try {
            bytes = Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw invalid();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        if (bytes.length != BYTE_LENGTH || buffer.get() != VERSION) {
            throw invalid();
        }
        long micros = buffer.getLong();
        if (micros < MIN_MICROS || micros > MAX_MICROS) {
            throw invalid();
        }
        TripHistoryCursor cursor = new TripHistoryCursor(
                Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L),
                new UUID(buffer.getLong(), buffer.getLong()));
        // 같은 위치의 다른 인코딩 표현 거부
        if (!cursor.encode().equals(value)) {
            throw invalid();
        }
        return cursor;
    }

    private static long toMicros(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000L), instant.getNano() / 1_000);
    }

    private static GeneralException invalid() {
        return new GeneralException(TripErrorCode.INVALID_CURSOR);
    }
}
