package com.chagokchagok.server.domain.mission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chagokchagok.server.domain.mission.entity.Mission;
import com.chagokchagok.server.domain.mission.entity.MissionPack;
import com.chagokchagok.server.domain.mission.entity.TriggerType;
import com.chagokchagok.server.domain.mission.service.MissionConfigValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

class AgeBasedMissionConfigTest {

    private final MissionConfigValidator validator = new MissionConfigValidator();

    @Test
    void seededMissionsMatchClientCodesAndValidateAllAgeScripts() throws Exception {
        List<Mission> missions = seededMissions();
        assertThat(missions).extracting(Mission::getCode).containsExactly(
                "BLINK", "BLINK_LEARN", "WIPER", "RAIN_STOP", "NEXT_TURN", "SPEED_GUESS",
                "SIGNAL_WAIT", "DEPART", "BLINK_MEMORY", "WAIT_BET", "SPEED_CHANGE", "NEAR_DEST", "TODAY_REPORT");
        for (Mission mission : missions) {
            validator.validate(mission);
            assertThat(mission.getConfig().path("ageContent").size()).isEqualTo(3);
        }
        var speed = missions.get(5).getConfig().path("ageContent");
        assertThat(speed.path("AGE_5_6").path("thresholdKph").asInt()).isEqualTo(60);
        assertThat(speed.path("AGE_9_PLUS").path("thresholdKph").asInt()).isEqualTo(50);
        assertThat(missions.get(9).getConfig().path("execution").path("resolution")
                .path("discardAfterSeconds").asInt()).isEqualTo(600);
        assertThat(missions.get(12).getConfig().path("execution").path("presentationOnly").asBoolean()).isTrue();
    }

    @Test
    void speedChangeFollowsPlanningThresholdAndAgeComparisonWindows() throws Exception {
        var config = seededMissions().stream().filter(m -> m.getCode().equals("SPEED_CHANGE"))
                .findFirst().orElseThrow().getConfig();
        assertThat(config.path("trigger").path("windowSeconds").asInt()).isEqualTo(20);
        assertThat(config.path("trigger").path("deltaKphExclusive").asInt()).isEqualTo(15);
        assertThat(config.path("trigger").has("deltaKphAtLeast")).isFalse();
        var ages = config.path("ageContent");
        assertThat(ages.path("AGE_5_6").path("comparisonSeconds").asInt()).isEqualTo(20);
        assertThat(ages.path("AGE_7_8").path("comparisonSeconds").asInt()).isEqualTo(20);
        assertThat(ages.path("AGE_9_PLUS").path("comparisonSeconds").asInt()).isEqualTo(60);
    }

    @Test
    void rejectsMissingAgeUnknownAnswerKindAndMismatchedTrigger() throws Exception {
        Mission mission = seededMissions().getFirst();
        ObjectNode config = (ObjectNode) mission.getConfig().deepCopy();
        ((ObjectNode) config.path("ageContent")).remove("AGE_9_PLUS");
        assertInvalid(mission, config);

        config = (ObjectNode) mission.getConfig().deepCopy();
        ((ObjectNode) config.path("ageContent").path("AGE_5_6")).put("answerKind", "NUMBER");
        assertInvalid(mission, config);

        config = (ObjectNode) mission.getConfig().deepCopy();
        ((ObjectNode) config.path("trigger")).put("event", "WIPER_ON");
        assertInvalid(mission, config);
    }

    @Test
    void reportMustRemainPresentationOnly() throws Exception {
        Mission report = seededMissions().getLast();
        ObjectNode config = (ObjectNode) report.getConfig().deepCopy();
        ((ObjectNode) config.path("execution")).put("presentationOnly", false);
        assertInvalid(report, config);
        config = (ObjectNode) report.getConfig().deepCopy();
        ((ObjectNode) config.path("ageContent").path("AGE_5_6")).put("answerKind", "FREE");
        assertInvalid(report, config);
    }

    private void assertInvalid(Mission original, ObjectNode config) {
        assertThatThrownBy(() -> validator.validate(new Mission(original.getMissionPack(),
                original.getCode(), original.getTriggerType(), config))).isInstanceOf(IllegalArgumentException.class);
    }

    private List<Mission> seededMissions() throws Exception {
        String sql = new ClassPathResource("db/migration/V10__seed_age_based_mission_pack.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        var matcher = Pattern.compile("\\(new_pack_id, '([^']+)', '([^']+)', \\$config\\$\\s*(.*?)\\$config\\$::jsonb\\)",
                Pattern.DOTALL).matcher(sql);
        MissionPack pack = new MissionPack("DEFAULT", 4);
        ReflectionTestUtils.setField(pack, "schemaVersion", 3);
        List<Mission> missions = new ArrayList<>();
        while (matcher.find()) {
            missions.add(new Mission(pack, matcher.group(1), TriggerType.valueOf(matcher.group(2)),
                    new ObjectMapper().readTree(matcher.group(3))));
        }
        return missions;
    }
}
