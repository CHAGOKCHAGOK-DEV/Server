package com.chagokchagok.server.domain.mission.service;

import com.chagokchagok.server.domain.mission.dto.MissionPackResponse;
import com.chagokchagok.server.domain.mission.entity.MissionPackStatus;
import com.chagokchagok.server.domain.mission.exception.MissionPackErrorCode;
import com.chagokchagok.server.domain.mission.repository.MissionPackRepository;
import com.chagokchagok.server.domain.mission.repository.MissionRepository;
import com.chagokchagok.server.global.exception.GeneralException;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class MissionPackService {

    private static final int SUPPORTED_SCHEMA_VERSION = 2;

    private final MissionPackRepository packs;
    private final MissionRepository missions;
    private final MissionConfigValidator validator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MissionPackResponse current(String code, int supportedSchemaVersion) {
        if (code == null || code.isBlank() || supportedSchemaVersion < 1) {
            throw new IllegalArgumentException("Invalid mission pack lookup parameters");
        }
        var pack = packs.findFirstByCodeAndStatusAndSchemaVersionLessThanEqualOrderByVersionDesc(
                        code, MissionPackStatus.PUBLISHED,
                        Math.min(supportedSchemaVersion, SUPPORTED_SCHEMA_VERSION))
                .orElseThrow(() -> new GeneralException(
                        packs.existsByCodeAndStatus(code, MissionPackStatus.PUBLISHED)
                                ? MissionPackErrorCode.UNSUPPORTED_SCHEMA : MissionPackErrorCode.NOT_FOUND));
        var content = missions.findByMissionPackIdAndEnabledTrueOrderByCodeAsc(pack.getId()).stream()
                .map(mission -> {
                    validator.validate(mission);
                    return new MissionPackResponse.MissionResponse(
                            mission.getCode(), mission.getTriggerType().name(),
                            objectMapper.readTree(mission.getConfig().toString()));
                }).toList();
        if (content.isEmpty()) {
            throw new IllegalStateException("Published mission pack has no enabled missions: " + pack.getId());
        }
        return new MissionPackResponse(pack.getCode(), pack.getVersion(), pack.getSchemaVersion(),
                pack.getPublishedAt(), content);
    }

    @Transactional
    public void publish(long packId) {
        var pack = packs.findById(packId).orElseThrow(() -> new GeneralException(MissionPackErrorCode.NOT_FOUND));
        if (pack.getSchemaVersion() < 1 || pack.getSchemaVersion() > SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported mission schema version: " + pack.getSchemaVersion());
        }
        var content = missions.findByMissionPackIdAndEnabledTrueOrderByCodeAsc(packId);
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Cannot publish an empty mission pack");
        }
        content.forEach(validator::validate);
        pack.publish(clock.instant());
    }
}
