package com.chagokchagok.server.domain.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chagokchagok.server.domain.mission.entity.Mission;
import com.chagokchagok.server.domain.mission.entity.MissionPack;
import com.chagokchagok.server.domain.mission.entity.TriggerType;
import com.chagokchagok.server.domain.mission.repository.MissionPackRepository;
import com.chagokchagok.server.domain.mission.service.MissionConfigValidator;
import com.chagokchagok.server.domain.mission.service.MissionPackService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MissionPackPostgresTest {

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
    @Autowired private MissionPackRepository packs;
    @Autowired private MissionConfigValidator validator;
    @Autowired private MissionPackService service;

    @BeforeEach
    void resetToDefaultPack() throws Exception {
        jdbc.execute("TRUNCATE agreement_histories, agreement_documents, mission_results, trips, missions, mission_packs, copilots, installations RESTART IDENTITY CASCADE");
        String seed = new String(new ClassPathResource("db/migration/V3__seed_default_mission_pack.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute(seed);
        String ageGroupMigration = new String(new ClassPathResource("db/migration/V5__rename_age_group_9_plus.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute(ageGroupMigration);
    }

    @Test
    void flywaySeedsPublishedPackAndPublicApiReturnsAllFourMissions() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mission_packs WHERE code='DEFAULT' AND version=1 AND status='PUBLISHED'", Integer.class))
                .isEqualTo(1);
        mvc.perform(get("/api/v1/mission-packs/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("DEFAULT"))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.schemaVersion").value(1))
                .andExpect(jsonPath("$.data.missions.length()").value(4))
                .andExpect(jsonPath("$.data.missions[0].code").value("BLINK"))
                .andExpect(jsonPath("$.data.missions[0].config.ageContent.AGE_5_6.question").isNotEmpty())
                .andExpect(jsonPath("$.data.missions[0].config.ageContent.AGE_9_PLUS.question").isNotEmpty())
                .andExpect(jsonPath("$.data.missions[0].config.ageContent.AGE_9_10").doesNotExist())
                .andExpect(jsonPath("$.data.missions[0].config.execution.cooldownSecondsByIntensity.NORMAL").value(180));
    }

    @Test
    @Transactional
    void selectsLatestCompatiblePublishedVersion() throws Exception {
        jdbc.update("INSERT INTO mission_packs(code,version,schema_version,status) VALUES('DEFAULT',2,1,'DRAFT')");
        jdbc.update("INSERT INTO mission_packs(code,version,schema_version,status) VALUES('DEFAULT',3,1,'DRAFT')");
        jdbc.update("INSERT INTO missions(mission_pack_id,code,trigger_type,config) SELECT p.id,m.code,m.trigger_type,m.config FROM mission_packs p JOIN missions m ON m.mission_pack_id=(SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=1) WHERE p.code='DEFAULT' AND p.version=2");
        jdbc.update("UPDATE mission_packs SET status='PUBLISHED', published_at=now() WHERE code='DEFAULT' AND version=2");

        mvc.perform(get("/api/v1/mission-packs/current").param("supportedSchemaVersion", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        mvc.perform(get("/api/v1/mission-packs/current").param("supportedSchemaVersion", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
    }

    @Test
    void rejectsMissingPacksAndInvalidLookupParameters() throws Exception {
        mvc.perform(get("/api/v1/mission-packs/current").param("code", "MISSING"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MISSION_PACK_404"));
        mvc.perform(get("/api/v1/mission-packs/current").param("supportedSchemaVersion", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void reportsUnsupportedSchemaWhenOnlyNewerPublishedContentExists() throws Exception {
        jdbc.update("INSERT INTO mission_packs(code,version,schema_version,status) VALUES('NEW',1,2,'DRAFT')");
        jdbc.update("INSERT INTO missions(mission_pack_id,code,trigger_type,config) SELECT p.id,m.code,m.trigger_type,m.config FROM mission_packs p JOIN missions m ON m.mission_pack_id=(SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=1) WHERE p.code='NEW'");
        jdbc.update("UPDATE mission_packs SET status='PUBLISHED', published_at=now() WHERE code='NEW'");
        mvc.perform(get("/api/v1/mission-packs/current").param("code", "NEW"))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("MISSION_PACK_406"));
    }

    @Test
    @Transactional
    void publicationValidatesAndTransitionsOnlyNonEmptyDraft() {
        jdbc.update("INSERT INTO mission_packs(code,version,status) VALUES('EMPTY',1,'DRAFT')");
        Long emptyId = jdbc.queryForObject("SELECT id FROM mission_packs WHERE code='EMPTY'", Long.class);
        assertThatThrownBy(() -> service.publish(emptyId)).isInstanceOf(IllegalArgumentException.class);

        jdbc.update("INSERT INTO mission_packs(code,version,status) VALUES('READY',1,'DRAFT')");
        Long readyId = jdbc.queryForObject("SELECT id FROM mission_packs WHERE code='READY'", Long.class);
        jdbc.update("INSERT INTO missions(mission_pack_id,code,trigger_type,config) SELECT ?,m.code,m.trigger_type,m.config FROM missions m WHERE m.mission_pack_id=(SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=1)", readyId);
        service.publish(readyId);
        packs.flush();
        assertThat(jdbc.queryForObject("SELECT status FROM mission_packs WHERE id=?", String.class, readyId))
                .isEqualTo("PUBLISHED");
    }

    @Test
    void publishedPackAndMissionCannotBeEditedInDatabase() {
        Long packId = jdbc.queryForObject("SELECT id FROM mission_packs WHERE code='DEFAULT' AND version=1", Long.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE mission_packs SET version=99 WHERE id=?", packId))
                .hasMessageContaining("Published mission packs cannot be modified");
        assertThatThrownBy(() -> jdbc.update("UPDATE missions SET enabled=false WHERE mission_pack_id=? AND code='BLINK'", packId))
                .hasMessageContaining("Published missions cannot be modified");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO missions(mission_pack_id,code,trigger_type,config) VALUES(?, 'EXTRA', 'WIPER_ON', '{}'::jsonb)", packId))
                .hasMessageContaining("Missions cannot be added to a published pack");
    }

    @Test
    void rejectsStructurallyInvalidAndMismatchedConfig() throws Exception {
        MissionPack pack = packs.findFirstByCodeAndStatusAndSchemaVersionLessThanEqualOrderByVersionDesc(
                "DEFAULT", com.chagokchagok.server.domain.mission.entity.MissionPackStatus.PUBLISHED, 1).orElseThrow();
        String json = jdbc.queryForObject("SELECT config::text FROM missions WHERE mission_pack_id=? AND code='BLINK'", String.class, pack.getId());
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode missingAge = (ObjectNode) mapper.readTree(json);
        ((ObjectNode) missingAge.get("ageContent")).remove("AGE_9_PLUS");
        assertThatThrownBy(() -> validator.validate(new Mission(pack, "BLINK", TriggerType.TURN_SIGNAL_OFF, missingAge)))
                .isInstanceOf(IllegalArgumentException.class);

        ObjectNode mismatched = (ObjectNode) mapper.readTree(json);
        ((ObjectNode) mismatched.get("trigger")).put("event", "WIPER_ON");
        assertThatThrownBy(() -> validator.validate(new Mission(pack, "BLINK", TriggerType.TURN_SIGNAL_OFF, mismatched)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
