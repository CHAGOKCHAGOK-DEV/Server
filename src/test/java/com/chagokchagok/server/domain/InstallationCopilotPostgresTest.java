package com.chagokchagok.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.chagokchagok.server.domain.copilot.repository.CopilotRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InstallationCopilotPostgresTest {

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

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean CopilotRepository copilots;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE agreement_histories, agreement_documents, mission_results, trips, missions, mission_packs, copilots, installations RESTART IDENTITY CASCADE");
    }

    @Test
    void createsOneProfileOnV1SchemaAndRevokesToken() throws Exception {
        String registration = mvc.perform(post("/api/v1/installations"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(registration, "$.data.installationId"));
        String token = JsonPath.read(registration, "$.data.token");
        assertThat(jdbc.queryForObject("SELECT token_hash FROM installations WHERE id = ?", String.class, id).trim())
                .doesNotContain(token);

        String body = "{\"nickname\":\"부기장1\",\"ageGroup\":\"AGE_5_6\",\"missionIntensity\":\"NORMAL\"}";
        mvc.perform(post("/api/v1/copilot").header("X-Installation-Id", id)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/copilot").header("X-Installation-Id", id)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPILOT_409"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM copilots WHERE installation_id = ?", Integer.class, id))
                .isEqualTo(1);
        mvc.perform(delete("/api/v1/installations/me").header("X-Installation-Id", id)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/copilot").header("X-Installation-Id", id)
                .header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateInsertAfterStaleExistsCheckReturnsCopilotConflict() throws Exception {
        String registration = mvc.perform(post("/api/v1/installations"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(registration, "$.data.installationId"));
        String token = JsonPath.read(registration, "$.data.token");
        String body = "{\"nickname\":\"부기장1\",\"ageGroup\":\"AGE_5_6\",\"missionIntensity\":\"NORMAL\"}";

        // 두 요청이 모두 사전 조회를 통과한 경합을 재현한다.
        doReturn(false).when(copilots).existsById(id);
        mvc.perform(post("/api/v1/copilot").header("X-Installation-Id", id)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/copilot").header("X-Installation-Id", id)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COPILOT_409"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM copilots WHERE installation_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void migratesLegacyAgeGroupAndRestoresDatabaseConstraint() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO installations(id, token_hash) VALUES (?, ?)", id, "a".repeat(64));
        jdbc.execute("ALTER TABLE copilots DROP CONSTRAINT ck_copilots_age_group");
        jdbc.update("INSERT INTO copilots(installation_id, nickname, age_group) VALUES (?, 'copilot', 'AGE_9_10')", id);

        String migration = new ClassPathResource("db/migration/V5__rename_age_group_9_plus.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(migration);

        assertThat(jdbc.queryForObject(
                "SELECT age_group FROM copilots WHERE installation_id = ?", String.class, id))
                .isEqualTo("AGE_9_PLUS");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE copilots SET age_group = 'AGE_9_10' WHERE installation_id = ?", id))
                .hasMessageContaining("ck_copilots_age_group");
        assertThat(jdbc.queryForObject(
                "SELECT tgenabled FROM pg_trigger WHERE tgname = 'trg_protect_mission_content'", String.class))
                .isEqualTo("O");
    }
}
