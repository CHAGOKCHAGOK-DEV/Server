package com.chagokchagok.server.domain.mission.service;

import com.chagokchagok.server.domain.mission.entity.Mission;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.springframework.stereotype.Component;

@Component
public class MissionConfigValidator {

    private final Schema schemaV1;
    private final Schema schemaV2;

    public MissionConfigValidator() {
        var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
        schemaV1 = registry
                .getSchema(SchemaLocation.of("classpath:schema/mission-config-v1.json"));
        schemaV1.initializeValidators();
        schemaV2 = registry.getSchema(SchemaLocation.of("classpath:schema/mission-config-v2.json"));
        schemaV2.initializeValidators();
    }

    public void validate(Mission mission) {
        JsonNode config = mission.getConfig();
        int schemaVersion = mission.getMissionPack().getSchemaVersion();
        Schema schema = switch (schemaVersion) {
            case 1 -> schemaV1;
            case 2 -> schemaV2;
            default -> throw new IllegalArgumentException("Unsupported mission schema version: " + schemaVersion);
        };
        var errors = schema.validate(config.toString(), InputFormat.JSON);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid mission config for " + mission.getCode() + ": " + errors.getFirst());
        }

        if (schemaVersion == 2) {
            validateV2(mission);
            return;
        }

        String code = mission.getCode();
        String trigger = config.path("trigger").path("event").asText();
        String answerKind = config.path("completion").path("answerKind").asText();
        String expected = config.path("completion").path("expectedAnswer").asText();
        String validity = config.path("execution").path("validity").path("mode").asText();

        boolean valid = switch (code) {
            case "BLINK" -> trigger.equals("TURN_SIGNAL_OFF") && answerKind.equals("YES_NO")
                    && expected.equals("YES") && validity.equals("TIME")
                    && config.path("trigger").has("lookbackMs");
            case "WIPER" -> trigger.equals("WIPER_ON") && answerKind.equals("YES_NO")
                    && expected.equals("YES") && validity.equals("TIME");
            case "NEXT_TURN" -> trigger.equals("NAVI_NEXT_TURN") && answerKind.equals("LEFT_RIGHT")
                    && expected.equals("NAVI_DIRECTION") && validity.equals("NAVIGATION_DISTANCE");
            case "NEAR_DEST" -> trigger.equals("NEAR_DESTINATION") && answerKind.equals("FREE")
                    && expected.equals("ANY_SPOKEN") && validity.equals("ROUTE_ACTIVE")
                    && config.path("trigger").has("etaSecondsAtMost")
                    && config.path("execution").path("maxPerTrip").asInt() == 1;
            default -> false;
        };
        if (!valid || !mission.getTriggerType().name().equals(trigger)) {
            throw new IllegalArgumentException("Mission code, trigger, completion and validity disagree: " + code);
        }
    }

    private void validateV2(Mission mission) {
        String event = mission.getConfig().path("trigger").path("event").asText();
        String expectedEvent = switch (mission.getCode()) {
            case "V-01", "V-02", "V-03" -> "TURN_SIGNAL_OFF";
            case "V-04" -> "WIPER_ON";
            case "N-01" -> "NAVI_NEXT_TURN";
            case "N-02" -> "NAVI_TRIP_INFO";
            case "T-01" -> "TRIP_STARTED";
            case "T-05" -> "MISSION_CORRECT";
            case "T-08" -> "TRIP_PAUSED";
            case "N-09_T-06" -> "DESTINATION_ARRIVED";
            default -> throw new IllegalArgumentException("Unknown v2 mission code: " + mission.getCode());
        };
        if (!event.equals(expectedEvent) || !mission.getTriggerType().name().equals(event)) {
            throw new IllegalArgumentException("Mission code and trigger disagree: " + mission.getCode());
        }
    }
}
