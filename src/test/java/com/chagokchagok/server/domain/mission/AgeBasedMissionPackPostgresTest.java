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
class AgeBasedMissionPackPostgresTest {

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
    void seedVersions() throws Exception {
        jdbc.execute("TRUNCATE agreement_histories, agreement_documents, mission_results, trips, missions, mission_packs, copilots, installations RESTART IDENTITY CASCADE");
        for (String name : new String[]{"V3__seed_default_mission_pack.sql", "V5__rename_age_group_9_plus.sql",
                "V6__seed_demo_mission_pack.sql", "V8__seed_fixed_demo_mission_pack.sql",
                "V10__seed_age_based_mission_pack.sql"}) {
            jdbc.execute(new ClassPathResource("db/migration/" + name).getContentAsString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void servesThirteenMissionsOnlyToCompatibleClients() throws Exception {
        for (String supported : new String[]{"3", "99"}) {
            mvc.perform(get("/api/v1/mission-packs/current").param("supportedSchemaVersion", supported))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.version").value(4))
                    .andExpect(jsonPath("$.data.schemaVersion").value(3))
                    .andExpect(jsonPath("$.data.missions.length()").value(13));
        }
        mvc.perform(get("/api/v1/mission-packs/current").param("supportedSchemaVersion", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(3))
                .andExpect(jsonPath("$.data.missions.length()").value(10));
        mvc.perform(get("/api/v1/mission-packs/current"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.missions.length()").value(4));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mission_packs WHERE code='DEFAULT'", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void preservesPublishedContentAndRejectsAccidentalReseeding() throws Exception {
        String seed = new ClassPathResource("db/migration/V10__seed_age_based_mission_pack.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> jdbc.execute(seed)).hasStackTraceContaining("refusing to overwrite");
        assertThatThrownBy(() -> jdbc.update("UPDATE missions SET enabled=false WHERE mission_pack_id="
                + "(SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=4)"))
                .hasMessageContaining("Published missions cannot be modified");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM missions WHERE mission_pack_id="
                + "(SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=4)", Integer.class)).isEqualTo(13);
    }
}
