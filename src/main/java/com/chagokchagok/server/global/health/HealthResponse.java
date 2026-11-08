package com.chagokchagok.server.global.health;

import java.time.OffsetDateTime;
import java.time.ZoneId;

public record HealthResponse(
    String status,
    OffsetDateTime serverTime
) {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public static HealthResponse up() {
        return new HealthResponse("UP", OffsetDateTime.now(SERVICE_ZONE));
    }
}
