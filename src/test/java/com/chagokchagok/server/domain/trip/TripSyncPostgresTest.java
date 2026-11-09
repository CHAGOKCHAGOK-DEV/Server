package com.chagokchagok.server.domain.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chagokchagok.server.domain.trip.dto.TripSyncRequest;
import com.chagokchagok.server.domain.trip.entity.MissionResultStatus;
import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import com.chagokchagok.server.domain.trip.repository.TripSyncRepository;
import com.chagokchagok.server.domain.trip.service.TripSyncService;
import com.chagokchagok.server.global.exception.GeneralException;
import com.chagokchagok.server.global.security.jwt.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TripSyncPostgresTest {

    private static final String ENDPOINT = "/api/v1/trips/sync";
    private static final UUID INSTALLATION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_INSTALLATION = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String TOKEN = "test-installation-token-first";
    private static final String OTHER_TOKEN = "test-installation-token-second";
    private static final Instant START = Instant.parse("2026-11-01T00:00:00.123456Z");

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

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private TripSyncService service;
    @MockitoSpyBean private TripSyncRepository syncRepository;

    @BeforeEach
    void seed() throws Exception {
        jdbc.execute("TRUNCATE agreement_histories, agreement_documents, mission_results, trips, missions, mission_packs, copilots, installations RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO installations(id, token_hash) VALUES (?, ?)", INSTALLATION, hash(TOKEN));
        jdbc.update("INSERT INTO installations(id, token_hash) VALUES (?, ?)", OTHER_INSTALLATION, hash(OTHER_TOKEN));
        jdbc.update("INSERT INTO mission_packs(id, code, version, status) VALUES (1, 'DEFAULT', 1, 'DRAFT'), (2, 'DEFAULT', 2, 'DRAFT'), (3, 'DRAFT', 1, 'DRAFT')");
        jdbc.update("INSERT INTO missions(id, mission_pack_id, code, trigger_type, config, enabled) VALUES (1, 1, 'V-01', 'WIPER_ON', '{}'::jsonb, true), (2, 2, 'V-01', 'WIPER_ON', '{}'::jsonb, false), (3, 2, 'V-02', 'WIPER_ON', '{}'::jsonb, true)");
        jdbc.update("UPDATE mission_packs SET status='PUBLISHED', published_at=now() WHERE id=1");
        jdbc.update("UPDATE mission_packs SET status='PUBLISHED', published_at=now() WHERE id=2");
        jdbc.update("UPDATE mission_packs SET status='ARCHIVED' WHERE id=2");
    }

    @Test
    void savesRepeatedMissionAndReturnsIdenticalResponseForReorderedRetry() throws Exception {
        var first = result(UUID.randomUUID(), "V-01", true);
        var second = result(UUID.randomUUID(), "V-01", false);
        UUID tripId = UUID.randomUUID();
        var request = request(tripId, List.of(first, second));
        String original = send(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.missionResultCount").value(2))
                .andReturn().getResponse().getContentAsString();
        String retry = send(request(tripId, List.of(second, first))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(retry).isEqualTo(original);
        assertThat(count("trips")).isEqualTo(1);
        assertThat(count("mission_results")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT started_at FROM trips", OffsetDateTime.class).toInstant()).isEqualTo(START);
    }

    @Test
    void storesPreGregorianInstantsExactlyAndReplaysIdentically() throws Exception {
        // 기존 Timestamp 바인딩 기준 UTC에서는 Julian 1500-02-29, Asia/Seoul에서는 10일 이동 저장되는 시각
        Instant early = Instant.parse("1500-03-10T15:32:08Z");
        var request = new TripSyncRequest(UUID.randomUUID(), "DEFAULT", 1, TripStatus.COMPLETED,
                TripEndReason.MANUAL_STOP, early, early.plusSeconds(600), List.of(new TripSyncRequest.Result(
                UUID.randomUUID(), "V-01", MissionResultStatus.COMPLETED, true, true, early, early.plusSeconds(10))));
        String original = send(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(send(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .isEqualTo(original);
        assertThat(utc("SELECT started_at FROM trips")).isEqualTo("1500-03-10T15:32:08.000000");
        assertThat(utc("SELECT ended_at FROM trips")).isEqualTo("1500-03-10T15:42:08.000000");
        assertThat(utc("SELECT started_at FROM mission_results")).isEqualTo("1500-03-10T15:32:08.000000");
        assertThat(utc("SELECT ended_at FROM mission_results")).isEqualTo("1500-03-10T15:32:18.000000");
    }

    // JDBC 읽기 변환을 거치지 않는 PostgreSQL 계산 기준 저장값
    private String utc(String column) {
        return jdbc.queryForObject("""
                SELECT to_char(v AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US') FROM (%s) q(v)
                """.formatted(column), String.class);
    }

    @Test
    void sameTripIdWithDifferentPayloadReturnsConflictWithoutChangingData() throws Exception {
        UUID tripId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        send(request(tripId, List.of(result(resultId, "V-01", true)))).andExpect(status().isOk());
        send(request(tripId, List.of(result(resultId, "V-01", false))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRIP_409"));
        assertThat(jdbc.queryForObject("SELECT is_correct FROM mission_results", Boolean.class)).isTrue();
        assertThat(count("trips")).isEqualTo(1);
    }

    @Test
    void anotherInstallationCannotReplayOrOverwriteTrip() throws Exception {
        var request = request(UUID.randomUUID(), List.of());
        send(request).andExpect(status().isOk());
        send(request, OTHER_INSTALLATION, OTHER_TOKEN).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_404"));
        assertThat(jdbc.queryForObject("SELECT installation_id FROM trips", UUID.class)).isEqualTo(INSTALLATION);
    }

    @Test
    void authenticatesInstallationTokenAndRejectsMissingWrongRevokedAndMemberJwt() throws Exception {
        var request = request(UUID.randomUUID(), List.of());
        mvc.perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        send(request, INSTALLATION, "wrong-token").andExpect(status().isUnauthorized());
        send(request, OTHER_INSTALLATION, TOKEN).andExpect(status().isUnauthorized());
        send(request, INSTALLATION, jwtProvider.createAccessToken(42L)).andExpect(status().isUnauthorized());
        mvc.perform(post(ENDPOINT).header("X-Installation-Id", "invalid-uuid")
                .header("Authorization", "Bearer " + TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request))).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE installations SET status = 'REVOKED' WHERE id = ?", INSTALLATION);
        send(request).andExpect(status().isUnauthorized());
        assertThat(count("trips")).isZero();
    }

    @Test
    void rejectsMalformedEnumNullNestedResultAndMissingParticipation() throws Exception {
        var request = request(UUID.randomUUID(), List.of(result(UUID.randomUUID(), "V-01", true)));
        String json = mapper.writeValueAsString(request);
        sendJson(json.replace("COMPLETED", "IN_PROGRESS")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_1"));
        sendJson(json.replace("\"participated\":true", "\"participated\":null")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        var tree = mapper.readTree(json);
        ((tools.jackson.databind.node.ObjectNode) tree).putArray("missionResults").addNull();
        sendJson(mapper.writeValueAsString(tree)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        assertThat(count("trips")).isZero();
    }

    @Test
    void validatesStatusPeriodResultBoundsAndParticipation() throws Exception {
        UUID id = UUID.randomUUID();
        send(new TripSyncRequest(id, "DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.UNKNOWN,
                START, START.plusSeconds(600), List.of())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_400"));
        send(new TripSyncRequest(id, "DEFAULT", 1, TripStatus.INTERRUPTED, TripEndReason.MANUAL_STOP,
                START, START.plusSeconds(600), List.of())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_400"));
        send(new TripSyncRequest(id, "DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                START, START.minusSeconds(1), List.of())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_400"));
        send(request(id, List.of(new TripSyncRequest.Result(UUID.randomUUID(), "V-01", MissionResultStatus.COMPLETED,
                true, true, START.minusSeconds(1), START.plusSeconds(1))))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_400"));
        send(request(id, List.of(new TripSyncRequest.Result(UUID.randomUUID(), "V-01", MissionResultStatus.COMPLETED,
                false, false, START, START.plusSeconds(1))))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_400"));
        assertThat(count("trips")).isZero();
    }

    @Test
    void rejectsOversizedResultListAndUnsupportedTimestampPrecision() throws Exception {
        List<TripSyncRequest.Result> results = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            results.add(result(UUID.randomUUID(), "V-01", null));
        }
        send(request(UUID.randomUUID(), results)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
        send(new TripSyncRequest(UUID.randomUUID(), "DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                START.plusNanos(1), START.plusSeconds(600), List.of())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_400"));
        assertThat(count("trips")).isZero();
    }

    @Test
    void transactionalServiceRechecksRevokedInstallation() {
        jdbc.update("UPDATE installations SET status = 'REVOKED' WHERE id = ?", INSTALLATION);
        assertThatThrownBy(() -> service.sync(INSTALLATION, request(UUID.randomUUID(), List.of())))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("COMMON_401"));
        assertThat(count("trips")).isZero();
    }

    @Test
    void replayRemainsIdenticalAfterContentIsArchived() throws Exception {
        var request = request(UUID.randomUUID(), List.of(result(UUID.randomUUID(), "V-01", true)));
        String original = send(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        jdbc.update("UPDATE mission_packs SET status = 'ARCHIVED' WHERE id = 1");
        String retry = send(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(retry).isEqualTo(original);
    }

    @Test
    void rejectsDuplicateIdsUnknownPackDraftAndMissionFromDifferentVersion() throws Exception {
        var result = result(UUID.randomUUID(), "V-01", true);
        send(request(UUID.randomUUID(), List.of(result, result))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_400"));
        send(new TripSyncRequest(UUID.randomUUID(), "DEFAULT", 99, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                START, START.plusSeconds(600), List.of())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_400_1"));
        send(new TripSyncRequest(UUID.randomUUID(), "DRAFT", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                START, START.plusSeconds(600), List.of())).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRIP_400_1"));
        send(request(UUID.randomUUID(), List.of(result(UUID.randomUUID(), "V-02", true))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRIP_400_1"));
        assertThat(count("trips")).isZero();
    }

    @Test
    void acceptsArchivedVersionDisabledMissionAndInterruptedTripForOfflineHistory() throws Exception {
        send(new TripSyncRequest(UUID.randomUUID(), "DEFAULT", 2, TripStatus.INTERRUPTED, TripEndReason.APP_INTERRUPTED,
                START, START.plusSeconds(600), List.of(result(UUID.randomUUID(), "V-01", true))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT mission_pack_id FROM trips", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT mission_id FROM mission_results", Long.class)).isEqualTo(2);
    }

    @Test
    void sharedResultIdRollsBackNewTripAndKeepsOriginalResult() throws Exception {
        UUID resultId = UUID.randomUUID();
        UUID originalTrip = UUID.randomUUID();
        send(request(originalTrip, List.of(result(resultId, "V-01", true)))).andExpect(status().isOk());
        send(request(UUID.randomUUID(), List.of(result(UUID.randomUUID(), "V-01", true), result(resultId, "V-01", true))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TRIP_409"));
        assertThat(count("trips")).isEqualTo(1);
        assertThat(count("mission_results")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT trip_id FROM mission_results", UUID.class)).isEqualTo(originalTrip);
    }

    @Test
    void databaseFailureAfterFirstResultRollsBackEverythingAndRetrySucceeds() throws Exception {
        jdbc.execute("""
                CREATE FUNCTION reject_cancelled_result() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'Injected test failure' USING ERRCODE = '23514'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_cancelled_result BEFORE INSERT ON mission_results FOR EACH ROW
                WHEN (NEW.status = 'CANCELLED') EXECUTE FUNCTION reject_cancelled_result()
                """);
        var first = result(UUID.fromString("00000000-0000-4000-8000-000000000001"), "V-01", true);
        var second = new TripSyncRequest.Result(UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff"),
                "V-01", MissionResultStatus.CANCELLED, false, null, START, START.plusSeconds(1));
        var request = request(UUID.randomUUID(), List.of(first, second));
        try {
            send(request).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMON_409"));
            assertThat(count("trips")).isZero();
            assertThat(count("mission_results")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_cancelled_result ON mission_results");
            jdbc.execute("DROP FUNCTION reject_cancelled_result()");
        }
        send(request).andExpect(status().isOk());
        assertThat(count("trips")).isEqualTo(1);
        assertThat(count("mission_results")).isEqualTo(2);
    }

    @Test
    void concurrentIdenticalRequestsAllReturnSameCommittedResponse() throws Exception {
        coordinateTripInserts(8);
        var request = request(UUID.randomUUID(), List.of(result(UUID.randomUUID(), "V-01", true)));
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> send(request).andReturn());
        }
        var responses = concurrent(calls);
        assertThat(responses).allSatisfy(response -> assertThat(response.getResponse().getStatus()).isEqualTo(200));
        assertThat(responses.stream().map(response -> new String(response.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8)).distinct().count()).isEqualTo(1);
        assertThat(count("trips")).isEqualTo(1);
        assertThat(count("mission_results")).isEqualTo(1);
    }

    @Test
    void concurrentDifferentPayloadsHaveOneWinnerAndOneConflict() throws Exception {
        coordinateTripInserts(2);
        UUID tripId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        var responses = concurrent(List.of(
                () -> send(request(tripId, List.of(result(resultId, "V-01", true)))).andReturn(),
                () -> send(request(tripId, List.of(result(resultId, "V-01", false)))).andReturn()));
        assertThat(statuses(responses)).containsExactlyInAnyOrder(200, 409);
        assertThat(code(loser(responses))).isEqualTo("TRIP_409");
        assertThat(count("trips")).isEqualTo(1);
        assertThat(count("mission_results")).isEqualTo(1);
    }

    @Test
    void concurrentDifferentOwnersCannotClaimSameTrip() throws Exception {
        coordinateTripInserts(2);
        UUID tripId = UUID.randomUUID();
        var otherPayload = new TripSyncRequest(tripId, "DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                START, START.plusSeconds(601), List.of());
        var responses = concurrent(List.of(() -> send(request(tripId, List.of())).andReturn(),
                () -> send(otherPayload, OTHER_INSTALLATION, OTHER_TOKEN).andReturn()));
        assertThat(statuses(responses)).containsExactlyInAnyOrder(200, 404);
        assertThat(code(loser(responses))).isEqualTo("TRIP_404");
        boolean firstWon = responses.get(0).getResponse().getStatus() == 200;
        assertThat(jdbc.queryForObject("SELECT installation_id FROM trips", UUID.class))
                .isEqualTo(firstWon ? INSTALLATION : OTHER_INSTALLATION);
        assertThat(count("trips")).isEqualTo(1);
    }

    @Test
    void concurrentTripsSharingResultIdsHaveOneWinnerWithoutPartialData() throws Exception {
        coordinateTripInserts(2);
        var first = result(UUID.randomUUID(), "V-01", true);
        var second = result(UUID.randomUUID(), "V-01", false);
        var responses = concurrent(List.of(
                () -> send(request(UUID.randomUUID(), List.of(first, second))).andReturn(),
                () -> send(request(UUID.randomUUID(), List.of(second, first)), OTHER_INSTALLATION, OTHER_TOKEN).andReturn()));
        assertThat(statuses(responses)).containsExactlyInAnyOrder(200, 409);
        assertThat(code(loser(responses))).isEqualTo("TRIP_409");
        assertThat(count("trips")).isEqualTo(1);
        assertThat(count("mission_results")).isEqualTo(2);
    }

    @Test
    void anotherInstallationWithDifferentPayloadGetsNotFoundBeforeHashComparison() throws Exception {
        UUID tripId = UUID.randomUUID();
        send(request(tripId, List.of(result(UUID.randomUUID(), "V-01", true)))).andExpect(status().isOk());
        send(request(tripId, List.of()), OTHER_INSTALLATION, OTHER_TOKEN).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRIP_404"));
        assertThat(jdbc.queryForObject("SELECT installation_id FROM trips", UUID.class)).isEqualTo(INSTALLATION);
        assertThat(count("mission_results")).isEqualTo(1);
    }

    @Test
    void encodedEquivalentPathRequiresInstallationAuthentication() throws Exception {
        URI encoded = URI.create("/api/v1/trips/%73ync");
        String memberJwt = "Bearer " + jwtProvider.createAccessToken(42L);
        String body = mapper.writeValueAsString(request(UUID.randomUUID(), List.of()));
        // 필터 미적용 시 컨트롤러 검증 400이 나오는 본문으로 필터 단계 차단 확인
        mvc.perform(post(encoded).header("Authorization", memberJwt).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMON_401"));
        mvc.perform(post(encoded).header("Authorization", memberJwt).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isUnauthorized());
        assertThat(count("trips")).isZero();
        mvc.perform(post(encoded).header("X-Installation-Id", INSTALLATION.toString())
                .header("Authorization", "Bearer " + TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT installation_id FROM trips", UUID.class)).isEqualTo(INSTALLATION);
    }

    @Test
    void trailingSlashPathIsNotMapped() throws Exception {
        URI trailing = URI.create(ENDPOINT + "/");
        String body = mapper.writeValueAsString(request(UUID.randomUUID(), List.of()));
        mvc.perform(post(trailing).header("Authorization", "Bearer " + jwtProvider.createAccessToken(42L))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMON_404"));
        mvc.perform(post(trailing).header("X-Installation-Id", INSTALLATION.toString())
                .header("Authorization", "Bearer " + TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(count("trips")).isZero();
    }

    private MvcResult loser(List<MvcResult> responses) {
        return responses.stream().filter(response -> response.getResponse().getStatus() != 200).findFirst().orElseThrow();
    }

    private String code(MvcResult response) throws Exception {
        return JsonPath.read(response.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.code");
    }

    private List<Integer> statuses(List<MvcResult> responses) {
        return responses.stream().map(response -> response.getResponse().getStatus()).toList();
    }

    private void coordinateTripInserts(int requestCount) {
        CountDownLatch arrived = new CountDownLatch(requestCount);
        doAnswer(invocation -> {
            arrived.countDown();
            if (!arrived.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Requests did not reach INSERT concurrently");
            }
            return invocation.callRealMethod();
        }).when(syncRepository).insertTrip(any(UUID.class), anyLong(), any(TripSyncRequest.class), anyString());
    }

    private List<MvcResult> concurrent(List<Callable<MvcResult>> calls) throws Exception {
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.size())) {
            var futures = calls.stream().map(call -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent request start timed out");
                }
                return call.call();
            })).toList();
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            List<MvcResult> responses = new ArrayList<>();
            for (var future : futures) {
                responses.add(future.get(20, TimeUnit.SECONDS));
            }
            return responses;
        }
    }

    private ResultActions send(TripSyncRequest request) throws Exception {
        return send(request, INSTALLATION, TOKEN);
    }

    private ResultActions send(TripSyncRequest request, UUID installation, String token) throws Exception {
        return mvc.perform(post(ENDPOINT).header("X-Installation-Id", installation.toString())
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)));
    }

    private ResultActions sendJson(String json) throws Exception {
        return mvc.perform(post(ENDPOINT).header("X-Installation-Id", INSTALLATION.toString())
                .header("Authorization", "Bearer " + TOKEN).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private TripSyncRequest request(UUID tripId, List<TripSyncRequest.Result> results) {
        return new TripSyncRequest(tripId, "DEFAULT", 1, TripStatus.COMPLETED, TripEndReason.MANUAL_STOP,
                START, START.plusSeconds(600), results);
    }

    private TripSyncRequest.Result result(UUID id, String missionCode, Boolean correct) {
        return new TripSyncRequest.Result(id, missionCode, MissionResultStatus.COMPLETED,
                true, correct, START.plusSeconds(10), START.plusSeconds(20));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
}
