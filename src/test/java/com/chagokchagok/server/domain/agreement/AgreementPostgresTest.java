package com.chagokchagok.server.domain.agreement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest;
import com.chagokchagok.server.domain.agreement.dto.AgreementConsentRequest.Event;
import com.chagokchagok.server.domain.agreement.entity.AgreementAction;
import com.chagokchagok.server.domain.agreement.repository.AgreementConsentRepository;
import com.chagokchagok.server.domain.agreement.service.AgreementService;
import com.chagokchagok.server.global.exception.GeneralException;
import com.chagokchagok.server.global.security.jwt.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgreementPostgresTest {

    private static final String CURRENT = "/api/v1/agreements/current";
    private static final String STATUS = "/api/v1/agreements/status";
    private static final String CONSENTS = "/api/v1/agreements/consents";
    private static final UUID INSTALLATION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_INSTALLATION = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String TOKEN = "test-installation-token-first";
    private static final String OTHER_TOKEN = "test-installation-token-second";
    private static final UUID LOW = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID HIGH = UUID.fromString("ffffffff-ffff-4fff-8fff-ffffffffffff");
    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");
    private static final Instant EFFECTIVE = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant SCHEDULED = Instant.parse("2026-12-01T00:00:00Z");
    private static final Instant ACCEPTED_AT = Instant.parse("2026-11-08T00:00:00.123456Z");
    private static final Duration SKEW = Duration.ofMinutes(5);

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
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private AgreementService service;
    @Autowired private MutableClock clock;
    @MockitoSpyBean private AgreementConsentRepository consentRepository;

    @BeforeEach
    void seed() throws Exception {
        clock.set(NOW);
        jdbc.execute("TRUNCATE agreement_histories, agreement_documents, mission_results, trips, missions, mission_packs, copilots, installations RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO installations(id, token_hash) VALUES (?, ?)", INSTALLATION, hash(TOKEN));
        jdbc.update("INSERT INTO installations(id, token_hash) VALUES (?, ?)", OTHER_INSTALLATION, hash(OTHER_TOKEN));
        Timestamp old = Timestamp.from(EFFECTIVE.minus(Duration.ofDays(30)));
        jdbc.update("""
                INSERT INTO agreement_documents(id, code, version, title, content, is_required, status, effective_at) VALUES
                (1, 'TERMS', 1, '약관 v1', '원문 v1', TRUE, 'ARCHIVED', ?),
                (2, 'TERMS', 2, '약관 v2', '원문 v2', TRUE, 'PUBLISHED', ?),
                (3, 'TERMS', 3, '약관 v3', '원문 v3', TRUE, 'PUBLISHED', ?),
                (4, 'TERMS', 4, '약관 v4', '원문 v4', TRUE, 'DRAFT', NULL),
                (5, 'OPTIONAL', 1, '선택 v1', '선택 원문', FALSE, 'PUBLISHED', ?),
                (6, 'RETIRED', 1, '종료 v1', '종료 원문', TRUE, 'ARCHIVED', ?)
                """, old, Timestamp.from(EFFECTIVE), Timestamp.from(SCHEDULED), Timestamp.from(EFFECTIVE), old);
    }

    @Test
    void currentReturnsHighestEffectivePublishedVersionAndSwitchesAtScheduledTime() throws Exception {
        authenticated(get(CURRENT)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documents.length()").value(2))
                .andExpect(jsonPath("$.data.documents[0].code").value("OPTIONAL"))
                .andExpect(jsonPath("$.data.documents[0].required").value(false))
                .andExpect(jsonPath("$.data.documents[1].documentId").value(2))
                .andExpect(jsonPath("$.data.documents[1].version").value(2))
                .andExpect(jsonPath("$.data.documents[1].title").value("약관 v2"))
                .andExpect(jsonPath("$.data.documents[1].content").value("원문 v2"))
                .andExpect(jsonPath("$.data.documents[1].required").value(true))
                .andExpect(jsonPath("$.data.documents[1].effectiveAt").value("2026-11-01T00:00:00Z"));
        clock.set(SCHEDULED.minusNanos(1000));
        authenticated(get(CURRENT)).andExpect(jsonPath("$.data.documents[1].version").value(2));
        clock.set(SCHEDULED);
        authenticated(get(CURRENT)).andExpect(jsonPath("$.data.documents[1].version").value(3));
    }

    @Test
    void savesConsentsAndReturnsIdenticalResponseForReorderedRetry() throws Exception {
        Event terms = event(LOW, "TERMS", 2);
        Event optional = event(HIGH, "OPTIONAL", 1);
        String original = send(List.of(optional, terms)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consents[0].eventId").value(LOW.toString()))
                .andExpect(jsonPath("$.data.consents[0].code").value("TERMS"))
                .andExpect(jsonPath("$.data.consents[0].action").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.consents[0].occurredAt").value("2026-11-08T00:00:00.123456Z"))
                .andExpect(jsonPath("$.data.consents[1].eventId").value(HIGH.toString()))
                .andReturn().getResponse().getContentAsString();
        String retry = send(List.of(terms, optional)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(retry).isEqualTo(original);
        assertThat(count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agreement_histories WHERE occurred_at = '2026-11-08T00:00:00.123456Z'::timestamptz",
                Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agreement_histories WHERE verification_method IS NULL AND verified_at IS NULL",
                Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT installation_id FROM agreement_histories WHERE id = ?", UUID.class, LOW))
                .isEqualTo(INSTALLATION);
    }

    @Test
    void ignoresClientVerificationFields() throws Exception {
        var tree = (ObjectNode) mapper.readTree(mapper.writeValueAsString(new AgreementConsentRequest(
                List.of(event(LOW, "TERMS", 2)))));
        ((ObjectNode) tree.get("events").get(0)).put("verificationMethod", "FORGED").put("verifiedAt", NOW.toString());
        sendJson(mapper.writeValueAsString(tree)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agreement_histories WHERE verification_method IS NULL AND verified_at IS NULL",
                Long.class)).isEqualTo(1);
    }

    @Test
    void sameEventIdWithDifferentContentReturnsConflictWithoutChangingData() throws Exception {
        send(List.of(event(LOW, "TERMS", 2))).andExpect(status().isOk());
        for (Event changed : List.of(
                new Event(LOW, "TERMS", 2, AgreementAction.ACCEPTED, ACCEPTED_AT.plusSeconds(1)),
                new Event(LOW, "TERMS", 2, AgreementAction.ACCEPTED, ACCEPTED_AT.plusNanos(1000)),
                new Event(LOW, "OPTIONAL", 1, AgreementAction.ACCEPTED, ACCEPTED_AT),
                new Event(LOW, "TERMS", 99, AgreementAction.ACCEPTED, ACCEPTED_AT))) {
            send(List.of(event(HIGH, "TERMS", 1), changed)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("AGREEMENT_409"));
        }
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT agreement_document_id FROM agreement_histories", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM agreement_histories WHERE occurred_at = '2026-11-08T00:00:00.123456Z'::timestamptz",
                Long.class)).isEqualTo(1);
    }

    @Test
    void anotherInstallationGetsNotFoundBeforeContentComparison() throws Exception {
        send(List.of(event(LOW, "TERMS", 2))).andExpect(status().isOk());
        send(List.of(event(LOW, "TERMS", 2)), OTHER_INSTALLATION, OTHER_TOKEN).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGREEMENT_404"));
        send(List.of(event(LOW, "OPTIONAL", 1)), OTHER_INSTALLATION, OTHER_TOKEN).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGREEMENT_404"));
        assertThat(jdbc.queryForObject("SELECT installation_id FROM agreement_histories", UUID.class)).isEqualTo(INSTALLATION);
        authenticated(get(STATUS), OTHER_INSTALLATION, OTHER_TOKEN).andExpect(jsonPath("$.data.consentRequired").value(true));
    }

    @Test
    void statusTracksCurrentVersionAndIgnoresOptionalDocument() throws Exception {
        authenticated(get(STATUS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consentRequired").value(true))
                .andExpect(jsonPath("$.data.documents[1].code").value("TERMS"))
                .andExpect(jsonPath("$.data.documents[1].state").value("NOT_ACCEPTED"));
        send(List.of(event(LOW, "TERMS", 2))).andExpect(status().isOk());
        authenticated(get(STATUS)).andExpect(jsonPath("$.data.consentRequired").value(false))
                .andExpect(jsonPath("$.data.documents[0].state").value("NOT_ACCEPTED"))
                .andExpect(jsonPath("$.data.documents[0].required").value(false))
                .andExpect(jsonPath("$.data.documents[1].state").value("ACCEPTED"));
        clock.set(SCHEDULED);
        authenticated(get(STATUS)).andExpect(jsonPath("$.data.consentRequired").value(true))
                .andExpect(jsonPath("$.data.documents[1].documentId").value(3))
                .andExpect(jsonPath("$.data.documents[1].state").value("OUTDATED"));
    }

    @Test
    void mixedBatchReplaysCommittedEventAndInsertsNewOne() throws Exception {
        Event terms = event(LOW, "TERMS", 2);
        String original = send(List.of(terms)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String recordedAt = JsonPath.read(original, "$.data.consents[0].recordedAt");
        send(List.of(event(HIGH, "OPTIONAL", 1), terms)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consents[0].eventId").value(LOW.toString()))
                .andExpect(jsonPath("$.data.consents[0].recordedAt").value(recordedAt))
                .andExpect(jsonPath("$.data.consents[1].eventId").value(HIGH.toString()));
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void reconsentWithNewEventIdAppendsHistoryAndKeepsAcceptedState() throws Exception {
        send(List.of(event(LOW, "TERMS", 2))).andExpect(status().isOk());
        send(List.of(event(HIGH, "TERMS", 2))).andExpect(status().isOk());
        assertThat(count()).isEqualTo(2);
        authenticated(get(STATUS)).andExpect(jsonPath("$.data.documents.length()").value(2))
                .andExpect(jsonPath("$.data.documents[1].state").value("ACCEPTED"));
    }

    @Test
    void recordsLateConsentToArchivedVersionWithoutSatisfyingCurrent() throws Exception {
        send(List.of(new Event(LOW, "TERMS", 1, AgreementAction.ACCEPTED, EFFECTIVE.minusSeconds(60))))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT agreement_document_id FROM agreement_histories", Long.class)).isEqualTo(1);
        authenticated(get(STATUS)).andExpect(jsonPath("$.data.consentRequired").value(true))
                .andExpect(jsonPath("$.data.documents[1].state").value("OUTDATED"));
    }

    @Test
    void replayRemainsIdenticalAfterArchiveAndServerClockMovesBack() throws Exception {
        Event atUpperBound = new Event(LOW, "TERMS", 2, AgreementAction.ACCEPTED, NOW.plus(SKEW));
        String original = send(List.of(atUpperBound)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        jdbc.update("UPDATE agreement_documents SET status = 'ARCHIVED', updated_at = now() WHERE id = 2");
        // 커밋된 이벤트 재전송은 서버 시각과 문서 상태에 무관
        for (Instant moved : List.of(NOW.minusSeconds(600), EFFECTIVE.minusSeconds(1))) {
            clock.set(moved);
            assertThat(send(List.of(atUpperBound)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString()).isEqualTo(original);
        }
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void rejectsInvalidDocumentsAndPayloadsBeforeWriting() throws Exception {
        Event valid = event(LOW, "OPTIONAL", 1);
        for (Event invalid : List.of(event(HIGH, "TERMS", 4), event(HIGH, "TERMS", 99), event(HIGH, "TERMS", 3),
                event(HIGH, "UNKNOWN", 1))) {
            send(List.of(valid, invalid)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AGREEMENT_400_1"));
        }
        for (Event invalid : List.of(
                new Event(HIGH, "TERMS", 2, AgreementAction.WITHDRAWN, ACCEPTED_AT),
                new Event(HIGH, "TERMS", 2, AgreementAction.ACCEPTED, NOW.plus(SKEW).plusNanos(1000)),
                new Event(HIGH, "TERMS", 2, AgreementAction.ACCEPTED, EFFECTIVE.minus(SKEW).minusNanos(1000)),
                new Event(HIGH, "TERMS", 2, AgreementAction.ACCEPTED, ACCEPTED_AT.plusNanos(1)),
                new Event(LOW, "TERMS", 2, AgreementAction.ACCEPTED, ACCEPTED_AT),
                new Event(HIGH, "OPTIONAL", 1, AgreementAction.ACCEPTED, ACCEPTED_AT))) {
            send(List.of(valid, invalid)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AGREEMENT_400"));
        }
        sendJson("{\"events\":[]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
        sendJson("{\"events\":[null]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
        sendJson("{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400"));
        sendJson(mapper.writeValueAsString(new AgreementConsentRequest(List.of(valid))).replace("ACCEPTED", "FOO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_400_1"));
        assertThat(count()).isZero();
        send(List.of(new Event(HIGH, "TERMS", 2, AgreementAction.ACCEPTED, EFFECTIVE.minus(Duration.ofMinutes(4)))))
                .andExpect(status().isOk());
    }

    @Test
    void transactionalServiceRechecksRevokedInstallation() {
        jdbc.update("UPDATE installations SET status = 'REVOKED' WHERE id = ?", INSTALLATION);
        assertThatThrownBy(() -> service.record(INSTALLATION, new AgreementConsentRequest(List.of(event(LOW, "TERMS", 2)))))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getErrorCode().getCode()).isEqualTo("COMMON_401"));
        assertThat(count()).isZero();
    }

    @Test
    void allAgreementPathsRequireInstallationAuthentication() throws Exception {
        String memberJwt = "Bearer " + jwtProvider.createAccessToken(42L);
        for (String path : List.of(CURRENT, STATUS)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMON_401"));
            mvc.perform(get(path).header("Authorization", memberJwt)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("COMMON_401"));
            authenticated(get(path), INSTALLATION, "wrong-token").andExpect(status().isUnauthorized());
            authenticated(get(path), OTHER_INSTALLATION, TOKEN).andExpect(status().isUnauthorized());
            mvc.perform(put(path).header("Authorization", memberJwt)).andExpect(status().isUnauthorized());
            // 매처 누락 시 설치 토큰이 JWT로 해석되어 401이 되므로 200 단언이 누락 검출
            authenticated(get(path)).andExpect(status().isOk());
        }
        for (String encoded : List.of("/api/v1/agreements/%63urrent", "/api/v1/agreements/%73tatus")) {
            mvc.perform(get(URI.create(encoded)).header("Authorization", memberJwt)).andExpect(status().isUnauthorized());
            authenticated(get(URI.create(encoded))).andExpect(status().isOk());
        }
        String body = mapper.writeValueAsString(new AgreementConsentRequest(List.of(event(LOW, "TERMS", 2))));
        for (URI path : List.of(URI.create(CONSENTS), URI.create("/api/v1/agreements/%63onsents"))) {
            // 필터 미적용 시 검증 400이 나오는 본문으로 필터 단계 차단 확인
            mvc.perform(post(path).header("Authorization", memberJwt).contentType(MediaType.APPLICATION_JSON)
                    .content("{}")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMON_401"));
        }
        assertThat(count()).isZero();
        mvc.perform(post(URI.create("/api/v1/agreements/%63onsents")).header("X-Installation-Id", INSTALLATION.toString())
                .header("Authorization", "Bearer " + TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        jdbc.update("UPDATE installations SET status = 'REVOKED' WHERE id = ?", INSTALLATION);
        authenticated(get(STATUS)).andExpect(status().isUnauthorized());
    }

    @Test
    void trailingSlashPathIsNotMapped() throws Exception {
        URI trailing = URI.create(STATUS + "/");
        mvc.perform(get(trailing).header("Authorization", "Bearer " + jwtProvider.createAccessToken(42L)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMON_404"));
        authenticated(get(trailing)).andExpect(status().isUnauthorized());
    }

    @Test
    void scheduledReplacementAfterArchivingLeavesCodeWithoutCurrentDocument() throws Exception {
        // 수용한 fail-open 동작 고정, 이전 버전 ARCHIVE는 새 버전 적용 이후로 운영 절차에서 보장
        jdbc.update("UPDATE agreement_documents SET status = 'ARCHIVED', updated_at = now() WHERE id = 2");
        authenticated(get(STATUS)).andExpect(jsonPath("$.data.documents.length()").value(1))
                .andExpect(jsonPath("$.data.consentRequired").value(false));
        clock.set(SCHEDULED);
        authenticated(get(STATUS)).andExpect(jsonPath("$.data.consentRequired").value(true));
    }

    @Test
    void databaseFailureAfterFirstInsertRollsBackEverythingAndRetrySucceeds() throws Exception {
        jdbc.execute("""
                CREATE FUNCTION reject_agreement_history() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'Injected test failure' USING ERRCODE = '23514'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER reject_agreement_history BEFORE INSERT ON agreement_histories FOR EACH ROW
                WHEN (NEW.id = 'ffffffff-ffff-4fff-8fff-ffffffffffff') EXECUTE FUNCTION reject_agreement_history()
                """);
        List<Event> events = List.of(event(HIGH, "OPTIONAL", 1), event(LOW, "TERMS", 2));
        try {
            send(events).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMON_409"));
            assertThat(count()).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_agreement_history ON agreement_histories");
            jdbc.execute("DROP FUNCTION reject_agreement_history()");
        }
        send(events).andExpect(status().isOk());
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void concurrentIdenticalRequestsAllReturnSameCommittedResponse() throws Exception {
        coordinateInserts(8);
        List<Event> events = List.of(event(LOW, "TERMS", 2));
        List<Callable<MvcResult>> calls = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(() -> send(events).andReturn());
        }
        var responses = concurrent(calls);
        assertThat(statuses(responses)).containsOnly(200);
        assertThat(responses.stream().map(response -> new String(response.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8)).distinct().count()).isEqualTo(1);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void concurrentDifferentOwnersCannotClaimSameEvent() throws Exception {
        coordinateInserts(2);
        List<Event> events = List.of(event(LOW, "TERMS", 2));
        var responses = concurrent(List.of(() -> send(events).andReturn(),
                () -> send(events, OTHER_INSTALLATION, OTHER_TOKEN).andReturn()));
        assertThat(statuses(responses)).containsExactlyInAnyOrder(200, 404);
        assertThat(code(loser(responses))).isEqualTo("AGREEMENT_404");
        boolean firstWon = responses.get(0).getResponse().getStatus() == 200;
        assertThat(jdbc.queryForObject("SELECT installation_id FROM agreement_histories", UUID.class))
                .isEqualTo(firstWon ? INSTALLATION : OTHER_INSTALLATION);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void concurrentCrossOrderedBatchesDoNotDeadlock() throws Exception {
        coordinateInserts(2);
        Event terms = event(LOW, "TERMS", 2);
        Event optional = event(HIGH, "OPTIONAL", 1);
        var responses = concurrent(List.of(() -> send(List.of(terms, optional)).andReturn(),
                () -> send(List.of(optional, terms)).andReturn()));
        assertThat(statuses(responses)).containsOnly(200);
        assertThat(count()).isEqualTo(2);
    }

    @Test
    void databaseProtectsPublishedDocumentsAndAllowsOnlyForwardTransitions() {
        for (String rejected : List.of(
                "UPDATE agreement_documents SET content = '변경' WHERE id = 2",
                "UPDATE agreement_documents SET title = '변경' WHERE id = 2",
                "UPDATE agreement_documents SET is_required = FALSE WHERE id = 2",
                "UPDATE agreement_documents SET effective_at = now() WHERE id = 2",
                "UPDATE agreement_documents SET content = '변경' WHERE id = 1",
                "UPDATE agreement_documents SET status = 'DRAFT' WHERE id = 2",
                "UPDATE agreement_documents SET status = 'PUBLISHED' WHERE id = 1",
                "UPDATE agreement_documents SET status = 'DRAFT' WHERE id = 1",
                "UPDATE agreement_documents SET status = 'ARCHIVED' WHERE id = 4",
                "DELETE FROM agreement_documents WHERE id = 2",
                "DELETE FROM agreement_documents WHERE id = 6")) {
            assertSqlState(rejected, "23000");
        }
        assertSqlState("INSERT INTO agreement_documents(id, code, version, title, content, is_required, status) VALUES (100, 'NEW', 1, 't', 'c', TRUE, 'PUBLISHED')",
                "23514");
        assertSqlState("UPDATE agreement_documents SET status = 'PUBLISHED' WHERE id = 4", "23514");

        assertThatCode(() -> {
            jdbc.update("UPDATE agreement_documents SET content = '확정 원문', status = 'PUBLISHED', effective_at = ? WHERE id = 4",
                    Timestamp.from(SCHEDULED.plusSeconds(1)));
            jdbc.update("UPDATE agreement_documents SET status = 'ARCHIVED', updated_at = now() WHERE id = 5");
            jdbc.update("UPDATE agreement_documents SET updated_at = now() WHERE id = 1");
            jdbc.update("INSERT INTO agreement_documents(id, code, version, title, content, is_required) VALUES (101, 'NEW', 1, 't', 'c', TRUE)");
            jdbc.update("DELETE FROM agreement_documents WHERE id = 101");
        }).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT content FROM agreement_documents WHERE id = 2", String.class)).isEqualTo("원문 v2");
    }

    private void assertSqlState(String sql, String sqlState) {
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOfSatisfying(DataIntegrityViolationException.class,
                exception -> assertThat(((SQLException) exception.getMostSpecificCause()).getSQLState())
                        .as(sql).isEqualTo(sqlState));
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

    private void coordinateInserts(int requestCount) {
        CountDownLatch arrived = new CountDownLatch(requestCount);
        doAnswer(invocation -> {
            arrived.countDown();
            if (!arrived.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Requests did not reach INSERT concurrently");
            }
            return invocation.callRealMethod();
        }).when(consentRepository).insertHistory(any(UUID.class), anyLong(), any(Event.class));
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

    private ResultActions authenticated(MockHttpServletRequestBuilder request) throws Exception {
        return authenticated(request, INSTALLATION, TOKEN);
    }

    private ResultActions authenticated(MockHttpServletRequestBuilder request, UUID installation, String token)
            throws Exception {
        return mvc.perform(request.header("X-Installation-Id", installation.toString())
                .header("Authorization", "Bearer " + token));
    }

    private ResultActions send(List<Event> events) throws Exception {
        return send(events, INSTALLATION, TOKEN);
    }

    private ResultActions send(List<Event> events, UUID installation, String token) throws Exception {
        return authenticated(post(CONSENTS).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new AgreementConsentRequest(events))), installation, token);
    }

    private ResultActions sendJson(String json) throws Exception {
        return authenticated(post(CONSENTS).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Event event(UUID id, String code, int version) {
        return new Event(id, code, version, AgreementAction.ACCEPTED, ACCEPTED_AT);
    }

    private long count() {
        return jdbc.queryForObject("SELECT count(*) FROM agreement_histories", Long.class);
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
