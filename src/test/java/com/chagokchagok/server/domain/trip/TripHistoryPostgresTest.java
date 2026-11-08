package com.chagokchagok.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chagokchagok.server.domain.trip.service.TripHistoryCursor;
import com.chagokchagok.server.global.security.jwt.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripHistoryPostgresTest {

    private static final UUID INSTALLATION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_INSTALLATION = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String TOKEN = "test-installation-token-first";
    private static final String OTHER_TOKEN = "test-installation-token-second";
    private static final Instant BASE = Instant.parse("2026-11-01T00:00:00Z");
    private static final String PROGRESS = "/api/v1/missions/daily-progress";

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry properties) {
        String url = System.getenv("PG_TEST_URL");
        if (url == null || !url.matches("jdbc:postgresql://[^/]+/trip_sync_test")) {
            throw new IllegalStateException("Tests require the dedicated trip_sync_test database");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> System.getenv("PG_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("PG_TEST_PASSWORD"));
        properties.add("spring.flyway.enabled", () -> true);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.jpa.open-in-view", () -> false);
        properties.add("logging.level.org.springframework.security", () -> "ERROR");
    }

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private MutableClock clock;

    @BeforeEach
    void seed() throws Exception {
        jdbc.execute("TRUNCATE agreement_histories, agreement_documents, mission_results, trips, missions, mission_packs, copilots, installations RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO installations(id, token_hash) VALUES (?, ?)", INSTALLATION, hash(TOKEN));
        jdbc.update("INSERT INTO installations(id, token_hash) VALUES (?, ?)", OTHER_INSTALLATION, hash(OTHER_TOKEN));
        jdbc.update("INSERT INTO mission_packs(id, code, version, status) VALUES (1, 'DEFAULT', 1, 'PUBLISHED')");
        jdbc.update("INSERT INTO missions(id, mission_pack_id, code, trigger_type, config) VALUES (1, 1, 'V-01', 'WIPER_ON', '{}'::jsonb), (2, 1, 'V-02', 'WIPER_ON', '{}'::jsonb)");
        clock.set(BASE);
    }

    @Test
    void listsOnlyOwnTripsNewestFirstWithPerTripSummary() throws Exception {
        trip(id(2), INSTALLATION, BASE.plusSeconds(3600), BASE.plusSeconds(4000));
        trip(id(3), OTHER_INSTALLATION, BASE.plusSeconds(10800), BASE.plusSeconds(11000));
        trip(id(1), INSTALLATION, BASE.plusSeconds(7200), BASE.plusSeconds(9000));
        Instant at = BASE.plusSeconds(7300);
        result(id(11), id(1), 1, "COMPLETED", true, true, at);
        result(id(12), id(1), 2, "COMPLETED", true, false, at);
        result(id(13), id(1), 1, "EXPIRED", false, null, at);
        result(id(14), id(1), 1, "CANCELLED", true, null, at);
        result(id(15), id(3), 1, "COMPLETED", true, true, BASE.plusSeconds(10900));

        own(get("/api/v1/trips")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].tripId", contains(id(1).toString(), id(2).toString())))
                .andExpect(jsonPath("$.data.items[0].missionPackCode").value("DEFAULT"))
                .andExpect(jsonPath("$.data.items[0].missionPackVersion").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.items[0].endReason").value("MANUAL_STOP"))
                .andExpect(jsonPath("$.data.items[0].startedAt").value("2026-11-01T02:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].endedAt").value("2026-11-01T02:30:00Z"))
                .andExpect(jsonPath("$.data.items[0].syncedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].summary.missionResultCount").value(4))
                .andExpect(jsonPath("$.data.items[0].summary.participatedCount").value(3))
                .andExpect(jsonPath("$.data.items[0].summary.completedCount").value(2))
                .andExpect(jsonPath("$.data.items[0].summary.correctCount").value(1))
                .andExpect(jsonPath("$.data.items[1].summary.missionResultCount").value(0))
                .andExpect(jsonPath("$.data.items[1].summary.participatedCount").value(0))
                .andExpect(jsonPath("$.data.items[1].summary.completedCount").value(0))
                .andExpect(jsonPath("$.data.items[1].summary.correctCount").value(0))
                .andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
    }

    @Test
    void keysetPagingHasNoDuplicatesOrSkipsAcrossTiesAndOlderTripSyncedMidway() throws Exception {
        // 삽입 순서, id 순서, 기대 순서를 서로 다르게 두고 동률은 마이크로초 단위
        Instant tie = BASE.plusSeconds(9 * 3600).plusNanos(123_456_000);
        trip(id(3), INSTALLATION, tie);
        trip(id(1), INSTALLATION, BASE.plusSeconds(8 * 3600));
        trip(id(2), INSTALLATION, tie);
        trip(id(0), INSTALLATION, BASE.plusSeconds(10 * 3600));
        trip(id(4), INSTALLATION, tie);
        trip(id(5), INSTALLATION, tie.plusNanos(1_000));
        trip(id(9), OTHER_INSTALLATION, BASE.plusSeconds(8 * 3600 + 60));

        Page first = page(null, 2);
        assertThat(first.ids()).containsExactly(id(0).toString(), id(5).toString());
        assertThat(first.nextCursor()).isNotNull();
        // 오프라인 지연 동기화된 과거 주행과 탐색 시작 이후의 최신 주행
        trip(id(6), INSTALLATION, BASE.plusSeconds(8 * 3600 + 1800));
        trip(id(7), INSTALLATION, BASE.plusSeconds(11 * 3600));

        Page second = page(first.nextCursor(), 2);
        assertThat(second.ids()).containsExactly(id(4).toString(), id(3).toString());
        assertThat(page(first.nextCursor(), 2)).isEqualTo(second);
        Page third = page(second.nextCursor(), 2);
        assertThat(third.ids()).containsExactly(id(2).toString(), id(6).toString());
        Page fourth = page(third.nextCursor(), 2);
        assertThat(fourth.ids()).containsExactly(id(1).toString());
        assertThat(fourth.nextCursor()).isNull();
        assertThat(page(null, 100).ids()).containsExactly(id(7).toString(), id(0).toString(), id(5).toString(),
                id(4).toString(), id(3).toString(), id(2).toString(), id(6).toString(), id(1).toString());
    }

    @Test
    void rejectsOutOfRangeSizeAndMalformedOrTamperedCursor() throws Exception {
        trip(id(1), INSTALLATION, BASE);
        for (String size : List.of("0", "101", "-1")) {
            own(get("/api/v1/trips").param("size", size)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_400"));
        }
        own(get("/api/v1/trips").param("size", "abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_3"));
        own(get("/api/v1/trips").param("size", "100")).andExpect(status().isOk());
        own(get("/api/v1/trips").param("size", "1")).andExpect(status().isOk());

        String valid = new TripHistoryCursor(BASE.plusSeconds(1), id(9)).encode();
        for (String cursor : List.of("", "not-a-cursor", valid + "A", "*" + valid.substring(1))) {
            own(get("/api/v1/trips").param("cursor", cursor)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("TRIP_400_2"));
        }
        own(get("/api/v1/trips").param("cursor", valid)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].tripId", contains(id(1).toString())));
    }

    @Test
    void detailReturnsSummaryAndResultsOrderedByStartedAtThenId() throws Exception {
        trip(id(1), INSTALLATION, BASE, BASE.plusSeconds(600));
        trip(id(2), INSTALLATION, BASE.plusSeconds(3600), BASE.plusSeconds(4200));
        // 시작 시각 순서와 id 순서가 어긋나도록 구성
        result(id(30), id(1), 1, "COMPLETED", true, false, BASE.plusSeconds(10));
        result(id(20), id(1), 2, "EXPIRED", false, null, BASE.plusSeconds(10));
        result(id(10), id(1), 1, "COMPLETED", true, true, BASE.plusSeconds(20));

        own(get("/api/v1/trips/{tripId}", id(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").value(id(1).toString()))
                .andExpect(jsonPath("$.data.missionPackCode").value("DEFAULT"))
                .andExpect(jsonPath("$.data.summary.missionResultCount").value(3))
                .andExpect(jsonPath("$.data.summary.participatedCount").value(2))
                .andExpect(jsonPath("$.data.summary.completedCount").value(2))
                .andExpect(jsonPath("$.data.summary.correctCount").value(1))
                .andExpect(jsonPath("$.data.missionResults[*].resultId",
                        contains(id(20).toString(), id(30).toString(), id(10).toString())))
                .andExpect(jsonPath("$.data.missionResults[0].missionCode").value("V-02"))
                .andExpect(jsonPath("$.data.missionResults[0].status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.missionResults[0].participated").value(false))
                .andExpect(jsonPath("$.data.missionResults[0].correct").value(nullValue()))
                .andExpect(jsonPath("$.data.missionResults[0].startedAt").value("2026-11-01T00:00:10Z"))
                .andExpect(jsonPath("$.data.missionResults[0].endedAt").value("2026-11-01T00:00:15Z"))
                .andExpect(jsonPath("$.data.missionResults[1].missionCode").value("V-01"))
                .andExpect(jsonPath("$.data.missionResults[1].participated").value(true))
                .andExpect(jsonPath("$.data.missionResults[1].correct").value(false));
        own(get("/api/v1/trips/{tripId}", id(2))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.missionResultCount").value(0))
                .andExpect(jsonPath("$.data.missionResults").isEmpty());
    }

    @Test
    void detailHidesOtherInstallationTripAndUnknownIdWithIdenticalNotFound() throws Exception {
        trip(id(1), OTHER_INSTALLATION, BASE);
        result(id(11), id(1), 1, "COMPLETED", true, true, BASE.plusSeconds(10));
        String foreign = own(get("/api/v1/trips/{tripId}", id(1))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_404")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String unknown = own(get("/api/v1/trips/{tripId}", id(2))).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(foreign).isEqualTo(unknown);
        own(get("/api/v1/trips/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_3"));
        as(OTHER_INSTALLATION, OTHER_TOKEN, get("/api/v1/trips/{tripId}", id(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionResults[*].resultId", contains(id(11).toString())));
    }

    @Test
    void installationAuthenticationAppliesToEveryHistoryPathIncludingEncodedForms() throws Exception {
        trip(id(1), INSTALLATION, BASE);
        String memberJwt = "Bearer " + jwtProvider.createAccessToken(42L);
        List<URI> valid = List.of(URI.create("/api/v1/trips"), URI.create("/api/v1/trips/" + id(1)),
                URI.create(PROGRESS + "?date=2026-11-01"), URI.create("/api/v1/trip%73"),
                URI.create("/api/v1/trip%73/" + id(1)), URI.create("/api/v1/missions/daily-progres%73?date=2026-11-01"));
        for (URI path : valid) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMON_401"));
            mvc.perform(get(path).header("Authorization", memberJwt)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON_401"));
            // 필터 누락 시 JWT 필터가 설치 토큰을 무시하므로 200 단언이 matcher 누락 검출
            own(get(path)).andExpect(status().isOk());
        }
        // 필터 미적용 시 바인딩 단계 400이 나오는 요청으로 필터 단계 차단 확인
        List<URI> invalidInput = List.of(URI.create("/api/v1/trip%73?size=0"), URI.create("/api/v1/trip%73/not-a-uuid"),
                URI.create("/api/v1/missions/daily-progres%73?date=invalid"));
        for (URI path : invalidInput) {
            mvc.perform(get(path).header("Authorization", memberJwt)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON_401"));
        }
    }

    @Test
    void getOnSyncPathIsInstallationScopedAndParsedAsTripId() throws Exception {
        String memberJwt = "Bearer " + jwtProvider.createAccessToken(42L);
        mvc.perform(get("/api/v1/trips/sync").header("Authorization", memberJwt)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401"));
        own(get("/api/v1/trips/sync")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_3"));
    }

    @Test
    void dailyProgressAttributesEachResultBySeoulDateOfItsOwnStartAndSumsTrips() throws Exception {
        Instant midnight = Instant.parse("2026-11-07T15:00:00Z");
        Instant nextMidnight = Instant.parse("2026-11-08T15:00:00Z");
        trip(id(1), INSTALLATION, midnight.minusSeconds(3600), midnight.plusSeconds(3600));
        trip(id(2), INSTALLATION, Instant.parse("2026-11-08T14:30:00Z"), Instant.parse("2026-11-08T15:30:00Z"));
        trip(id(3), OTHER_INSTALLATION, midnight, midnight.plusSeconds(600));
        trip(id(4), INSTALLATION, midnight.plusSeconds(7200), midnight.plusSeconds(7800));
        result(id(11), id(1), 1, "COMPLETED", true, true, midnight.minusNanos(1_000));
        result(id(12), id(1), 1, "COMPLETED", true, true, midnight);
        result(id(13), id(1), 2, "EXPIRED", false, null, midnight.plusSeconds(60));
        result(id(14), id(2), 1, "COMPLETED", true, false, nextMidnight.minusNanos(1_000));
        result(id(15), id(2), 1, "CANCELLED", true, null, nextMidnight.minusSeconds(30), nextMidnight.plusSeconds(30));
        result(id(16), id(2), 1, "COMPLETED", true, true, nextMidnight);
        result(id(17), id(3), 1, "COMPLETED", true, true, midnight.plusSeconds(10));

        own(get(PROGRESS).param("date", "2026-11-08")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-11-08"))
                .andExpect(jsonPath("$.data.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.data.goal").value(5))
                .andExpect(jsonPath("$.data.contributingTripCount").value(2))
                .andExpect(jsonPath("$.data.missionResultCount").value(4))
                .andExpect(jsonPath("$.data.participatedCount").value(3))
                .andExpect(jsonPath("$.data.completedCount").value(2))
                .andExpect(jsonPath("$.data.expiredCount").value(1))
                .andExpect(jsonPath("$.data.cancelledCount").value(1))
                .andExpect(jsonPath("$.data.correctCount").value(1))
                .andExpect(jsonPath("$.data.achieved").doesNotExist());
        own(get(PROGRESS).param("date", "2026-11-07")).andExpect(jsonPath("$.data.missionResultCount").value(1))
                .andExpect(jsonPath("$.data.contributingTripCount").value(1));
        own(get(PROGRESS).param("date", "2026-11-09")).andExpect(jsonPath("$.data.missionResultCount").value(1))
                .andExpect(jsonPath("$.data.correctCount").value(1));
        as(OTHER_INSTALLATION, OTHER_TOKEN, get(PROGRESS).param("date", "2026-11-08"))
                .andExpect(jsonPath("$.data.missionResultCount").value(1));
    }

    @Test
    void dailyProgressDefaultsToTodayInSeoulFromClock() throws Exception {
        clock.set(Instant.parse("2026-11-08T14:59:59.999999Z"));
        own(get(PROGRESS)).andExpect(status().isOk()).andExpect(jsonPath("$.data.date").value("2026-11-08"));
        clock.set(Instant.parse("2026-11-08T15:00:00Z"));
        own(get(PROGRESS)).andExpect(status().isOk()).andExpect(jsonPath("$.data.date").value("2026-11-09"));
    }

    @Test
    void dailyProgressRejectsMalformedAndOutOfRangeDates() throws Exception {
        for (String date : List.of("2026-13-01", "2026-02-30", "20261108", "abc")) {
            own(get(PROGRESS).param("date", date)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_400_3"));
        }
        for (String date : List.of("0001-01-01", "1500-03-10", "1582-12-31", "+10000-01-01")) {
            own(get(PROGRESS).param("date", date)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_400"));
        }
        for (String date : List.of("1583-01-01", "9999-12-31")) {
            own(get(PROGRESS).param("date", date)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.missionResultCount").value(0));
        }
    }

    private Page page(String cursor, int size) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/trips").param("size", String.valueOf(size));
        if (cursor != null) {
            request.param("cursor", cursor);
        }
        String body = own(request).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> ids = JsonPath.read(body, "$.data.items[*].tripId");
        String next = JsonPath.read(body, "$.data.nextCursor");
        return new Page(ids, next);
    }

    private record Page(List<String> ids, String nextCursor) {
    }

    private ResultActions own(MockHttpServletRequestBuilder request) throws Exception {
        return as(INSTALLATION, TOKEN, request);
    }

    private ResultActions as(UUID installation, String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("X-Installation-Id", installation.toString())
                .header("Authorization", "Bearer " + token));
    }

    // 선행 바이트를 작게 두어 PostgreSQL uuid 순서와 숫자 순서 일치
    private static UUID id(int value) {
        return UUID.fromString("%08x-0000-4000-8000-000000000000".formatted(value));
    }

    private void trip(UUID id, UUID installation, Instant start) {
        trip(id, installation, start, start.plusSeconds(600));
    }

    private void trip(UUID id, UUID installation, Instant start, Instant end) {
        jdbc.update("""
                INSERT INTO trips(id, installation_id, mission_pack_id, status, end_reason, started_at, ended_at, payload_hash)
                VALUES (?, ?, 1, 'COMPLETED', 'MANUAL_STOP', ?, ?, ?)
                """, id, installation, Timestamp.from(start), Timestamp.from(end), "0".repeat(64));
    }

    private void result(UUID id, UUID trip, long missionId, String status, boolean participated, Boolean correct,
                        Instant start) {
        result(id, trip, missionId, status, participated, correct, start, start.plusSeconds(5));
    }

    private void result(UUID id, UUID trip, long missionId, String status, boolean participated, Boolean correct,
                        Instant start, Instant end) {
        jdbc.update("""
                INSERT INTO mission_results(id, trip_id, mission_id, status, participated, is_correct, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, trip, missionId, status, participated, correct, Timestamp.from(start), Timestamp.from(end));
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }

    static final class MutableClock extends Clock {
        private volatile Instant now = Instant.EPOCH;

        void set(Instant value) {
            now = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
