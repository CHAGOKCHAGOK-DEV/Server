package com.chagokchagok.server.domain.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoMissionPackPostgresTest {

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
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seedBothVersions() throws Exception {
        jdbc.execute("TRUNCATE agreement_histories, agreement_documents, mission_results, trips, missions, mission_packs, copilots, installations RESTART IDENTITY CASCADE");
        for (String name : new String[]{"V3__seed_default_mission_pack.sql", "V5__seed_demo_mission_pack.sql"}) {
            String sql = new String(new ClassPathResource("db/migration/" + name)
                    .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            jdbc.execute(sql);
        }
    }

    @Test
    void servesTenDemoMissionsOnlyToSchemaV2Clients() throws Exception {
        mvc.perform(get("/api/v1/mission-packs/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.missions.length()").value(4));

        mvc.perform(get("/api/v1/mission-packs/current").param("supportedSchemaVersion", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.schemaVersion").value(2))
                .andExpect(jsonPath("$.data.missions.length()").value(10));

        assertThat(jdbc.queryForList("SELECT code FROM missions WHERE mission_pack_id = "
                + "(SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=2)", String.class))
                .contains("N-02", "N-09_T-06")
                .doesNotContain("T-07");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM missions WHERE mission_pack_id = "
                + "(SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=2)", Integer.class))
                .isEqualTo(10);
    }

    @Test
    void rerunningSeedDoesNotChangePublishedDemoPack() throws Exception {
        Long packId = jdbc.queryForObject("SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=2", Long.class);
        String sql = new String(new ClassPathResource("db/migration/V5__seed_demo_mission_pack.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute(sql);
        assertThat(jdbc.queryForObject("SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=2", Long.class))
                .isEqualTo(packId);
    }

    @Test
    void rejectsExistingDefaultVersionTwoWithDifferentSchemaVersion() throws Exception {
        jdbc.execute("TRUNCATE agreement_histories, agreement_documents, mission_results, trips, missions, mission_packs, copilots, installations RESTART IDENTITY CASCADE");
        String originalSeed = new String(new ClassPathResource("db/migration/V3__seed_default_mission_pack.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute(originalSeed);
        jdbc.update("INSERT INTO mission_packs(code, version, schema_version, status) VALUES ('DEFAULT', 2, 1, 'DRAFT')");

        String demoSeed = new String(new ClassPathResource("db/migration/V5__seed_demo_mission_pack.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> jdbc.execute(demoSeed))
                .hasStackTraceContaining("DEFAULT version 2 has schema_version 1, expected 2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM missions WHERE mission_pack_id = "
                + "(SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=2)", Integer.class))
                .isZero();
    }
}
