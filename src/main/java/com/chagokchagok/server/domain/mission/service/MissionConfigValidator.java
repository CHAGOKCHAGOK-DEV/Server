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

    private final Schema schema;

    public MissionConfigValidator() {
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(SchemaLocation.of("classpath:schema/mission-config-v1.json"));
        schema.initializeValidators();
    }

    public void validate(Mission mission) {
        JsonNode config = mission.getConfig();
        var errors = schema.validate(config.toString(), InputFormat.JSON);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid mission config for " + mission.getCode() + ": " + errors.getFirst());
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
}
