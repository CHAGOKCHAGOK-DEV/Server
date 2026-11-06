package com.chagokchagok.server.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chagokchagok.server.domain.copilot.entity.AgeGroup;
import com.chagokchagok.server.domain.copilot.entity.Copilot;
import com.chagokchagok.server.domain.copilot.entity.MissionIntensity;
import com.chagokchagok.server.domain.copilot.repository.CopilotRepository;
import com.chagokchagok.server.domain.installation.entity.Installation;
import com.chagokchagok.server.domain.installation.repository.InstallationRepository;
import com.chagokchagok.server.domain.mission.entity.Mission;
import com.chagokchagok.server.domain.mission.entity.MissionPack;
import com.chagokchagok.server.domain.mission.entity.TriggerType;
import com.chagokchagok.server.domain.mission.repository.MissionPackRepository;
import com.chagokchagok.server.domain.mission.repository.MissionRepository;
import com.chagokchagok.server.domain.trip.entity.MissionResult;
import com.chagokchagok.server.domain.trip.entity.MissionResultStatus;
import com.chagokchagok.server.domain.trip.entity.Trip;
import com.chagokchagok.server.domain.trip.entity.TripEndReason;
import com.chagokchagok.server.domain.trip.entity.TripStatus;
import com.chagokchagok.server.domain.trip.repository.MissionResultRepository;
import com.chagokchagok.server.domain.trip.repository.TripRepository;
import com.chagokchagok.server.global.config.JpaAuditingConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CoreDomainRepositoryTest {

    private static final String HASH = "a".repeat(64);

    @Autowired
    private InstallationRepository installationRepository;

    @Autowired
    private CopilotRepository copilotRepository;

    @Autowired
    private MissionPackRepository missionPackRepository;

    @Autowired
    private MissionRepository missionRepository;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private MissionResultRepository missionResultRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void copilotUsesInstallationIdAsSharedPrimaryKey() {
        Installation installation = installationRepository.save(new Installation(UUID.randomUUID(), HASH));

        Copilot copilot = copilotRepository.saveAndFlush(
                new Copilot(installation, "꼬마", AgeGroup.AGE_5_6)
        );

        assertThat(copilot.getInstallationId()).isEqualTo(installation.getId());
        assertThat(copilot.getMissionIntensity()).isEqualTo(MissionIntensity.NORMAL);
        assertThat(copilot.getCreatedAt()).isNotNull();
        assertThat(copilot.getUpdatedAt()).isNotNull();
    }

    @Test
    void installationTokenHashMustBeUnique() {
        installationRepository.saveAndFlush(new Installation(UUID.randomUUID(), HASH));

        assertThatThrownBy(() -> installationRepository.saveAndFlush(
                new Installation(UUID.randomUUID(), HASH)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void missionCodeMustBeUniqueWithinMissionPack() {
        MissionPack missionPack = missionPackRepository.save(new MissionPack("DEFAULT", 1));
        missionRepository.saveAndFlush(new Mission(
                missionPack,
                "V-01",
                TriggerType.WIPER_ON,
                objectMapper.createObjectNode().put("validSec", 30)
        ));

        assertThatThrownBy(() -> missionRepository.saveAndFlush(new Mission(
                missionPack,
                "V-01",
                TriggerType.WIPER_ON,
                objectMapper.createObjectNode()
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameMissionCanHaveMultipleResultsWithDifferentClientIds() {
        Installation installation = installationRepository.save(
                new Installation(UUID.randomUUID(), HASH)
        );
        MissionPack missionPack = missionPackRepository.save(new MissionPack("DEFAULT", 1));
        Mission mission = missionRepository.save(new Mission(
                missionPack,
                "V-01",
                TriggerType.TURN_SIGNAL_OFF,
                objectMapper.createObjectNode()
        ));
        Instant tripStartedAt = Instant.parse("2026-11-06T01:00:00Z");
        Trip trip = tripRepository.save(new Trip(
                UUID.randomUUID(),
                installation,
                missionPack,
                TripStatus.COMPLETED,
                TripEndReason.MANUAL_STOP,
                tripStartedAt,
                tripStartedAt.plusSeconds(600),
                "b".repeat(64)
        ));

        missionResultRepository.save(new MissionResult(
                UUID.randomUUID(), trip, mission, MissionResultStatus.COMPLETED, true, true,
                tripStartedAt.plusSeconds(60), tripStartedAt.plusSeconds(90)
        ));
        missionResultRepository.save(new MissionResult(
                UUID.randomUUID(), trip, mission, MissionResultStatus.EXPIRED, false, null,
                tripStartedAt.plusSeconds(180), tripStartedAt.plusSeconds(210)
        ));
        missionResultRepository.flush();

        assertThat(missionResultRepository.count()).isEqualTo(2);
    }
}
