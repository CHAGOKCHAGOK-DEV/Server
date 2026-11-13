package com.chagokchagok.server.domain.agreement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chagokchagok.server.domain.trip.dto.TripSyncRequest;
import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgreementAccessControlPostgresTest {

    private static final UUID INSTALLATION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String TOKEN = "test-installation-consent-token";
    private static final Instant NOW = Instant.parse("2026-11-16T00:00:00Z");

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
    @Autowired private ObjectMapper mapper;
    @Autowired private MutableClock clock;

    @BeforeEach
    void resetDatabase() throws Exception {
        clock.set(NOW);
        jdbc.execute("TRUNCATE agreement_histories, agreement_documents, mission_results, trips, missions, mission_packs, copilots, installations RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO installations(id, token_hash) VALUES (?, ?)", INSTALLATION, tokenHash(TOKEN));
        jdbc.update("INSERT INTO mission_packs(id, code, version, status) VALUES (1, 'DEFAULT', 1, 'DRAFT')");
        jdbc.update("INSERT INTO missions(id, mission_pack_id, code, trigger_type, config) VALUES (1, 1, 'V-01', 'WIPER_ON', '{}'::jsonb)");
        jdbc.update("UPDATE mission_packs SET status='PUBLISHED', published_at=now() WHERE id=1");
    }

    @Test
    void requiredDocumentConfigurationFailsClosedAndConsentReadApisRemainAvailable() throws Exception {
        profileCall().andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("REQUIRED_AGREEMENT_NOT_CONFIGURED"));
        mvc.perform(post("/api/v1/trips/sync").headers(installationHeaders())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(validTripRequest())))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("REQUIRED_AGREEMENT_NOT_CONFIGURED"));
        mvc.perform(get("/api/v1/copilot").headers(installationHeaders()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("REQUIRED_AGREEMENT_NOT_CONFIGURED"));
        mvc.perform(put("/api/v1/copilot").headers(installationHeaders()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"copilot\",\"ageGroup\":\"AGE_5_6\",\"missionIntensity\":\"NORMAL\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("REQUIRED_AGREEMENT_NOT_CONFIGURED"));
        mvc.perform(get("/api/v1/trips").headers(installationHeaders()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("REQUIRED_AGREEMENT_NOT_CONFIGURED"));
        mvc.perform(get("/api/v1/trips/{tripId}", UUID.randomUUID()).headers(installationHeaders()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("REQUIRED_AGREEMENT_NOT_CONFIGURED"));
        mvc.perform(get("/api/v1/missions/daily-progress").headers(installationHeaders()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("REQUIRED_AGREEMENT_NOT_CONFIGURED"));
        mvc.perform(get("/api/v1/agreements/current").headers(installationHeaders())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/agreements/status").headers(installationHeaders())).andExpect(status().isOk());
        mvc.perform(post("/api/v1/agreements/consents").headers(installationHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"eventId\":\"00000000-0000-4000-8000-000000000001\","
                                + "\"code\":\"TERMS_OF_SERVICE\",\"version\":1,\"action\":\"ACCEPTED\","
                                + "\"occurredAt\":\"2026-11-15T23:59:55Z\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AGREEMENT_400_1"));
    }

    @Test
    void unacceptedOrOutdatedRequiredDocumentsBlockAccessWithDistinctCodes() throws Exception {
        seedRequiredDocuments();
        profileCall().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));

        long termsV1 = documentId("TERMS_OF_SERVICE", 1);
        jdbc.update("UPDATE agreement_documents SET status = 'ARCHIVED' WHERE id = ?", termsV1);
        jdbc.update("INSERT INTO agreement_documents(code, version, title, content, is_required, status, effective_at) VALUES (?, ?, ?, ?, TRUE, 'PUBLISHED', ?)",
                "TERMS_OF_SERVICE", 2, "terms v2", "terms content v2", Timestamp.from(NOW.minusSeconds(10)));
        long childDocument = documentId("CHILD_PRIVACY_COLLECTION", 1);
        accept(childDocument);
        accept(termsV1);

        profileCall().andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CONSENT_OUTDATED"));
    }

    @Test
    void currentRequiredConsentsPermitProfileAndTripDataSynchronization() throws Exception {
        seedRequiredDocuments();
        accept(documentId("TERMS_OF_SERVICE", 1));
        accept(documentId("CHILD_PRIVACY_COLLECTION", 1));

        profileCall().andExpect(status().isCreated());
        mvc.perform(post("/api/v1/trips/sync").headers(installationHeaders())
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(validTripRequest())))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions profileCall() throws Exception {
        return mvc.perform(post("/api/v1/copilot").headers(installationHeaders())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"copilot\",\"ageGroup\":\"AGE_5_6\",\"missionIntensity\":\"NORMAL\"}"));
    }

    private HttpHeaders installationHeaders() {
        var headers = new HttpHeaders();
        headers.set("X-Installation-Id", INSTALLATION.toString());
        headers.setBearerAuth(TOKEN);
        return headers;
    }

    private TripSyncRequest validTripRequest() {
        return new TripSyncRequest(UUID.randomUUID(), "DEFAULT", 1, TripStatus.COMPLETED,
                TripEndReason.MANUAL_STOP, NOW.minusSeconds(600), NOW, List.of());
    }

    private void seedRequiredDocuments() {
        jdbc.update("""
                INSERT INTO agreement_documents(code, version, title, content, is_required, status, effective_at)
                VALUES ('TERMS_OF_SERVICE', 1, 'terms', 'terms content', TRUE, 'PUBLISHED', ?),
                       ('CHILD_PRIVACY_COLLECTION', 1, 'child privacy', 'child privacy content', TRUE, 'PUBLISHED', ?),
                       ('PRIVACY_POLICY', 1, 'privacy policy', 'privacy policy content', FALSE, 'PUBLISHED', ?)
                """, Timestamp.from(NOW.minusSeconds(60)), Timestamp.from(NOW.minusSeconds(60)),
                Timestamp.from(NOW.minusSeconds(60)));
    }

    private long documentId(String code, int version) {
        return jdbc.queryForObject("SELECT id FROM agreement_documents WHERE code = ? AND version = ?", Long.class,
                code, version);
    }

    private void accept(long documentId) {
        jdbc.update("""
                INSERT INTO agreement_histories(id, installation_id, agreement_document_id, action, occurred_at)
                VALUES (?, ?, ?, 'ACCEPTED', ?)
                """, UUID.randomUUID(), INSTALLATION, documentId, Timestamp.from(NOW.minusSeconds(5)));
    }

    private String tokenHash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
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
