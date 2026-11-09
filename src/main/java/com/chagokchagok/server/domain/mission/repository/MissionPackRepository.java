package com.chagokchagok.server.domain.mission.repository;

import com.chagokchagok.server.domain.mission.entity.MissionPack;
import com.chagokchagok.server.domain.mission.entity.MissionPackStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionPackRepository extends JpaRepository<MissionPack, Long> {
    Optional<MissionPack> findFirstByCodeAndStatusAndSchemaVersionLessThanEqualOrderByVersionDesc(
            String code, MissionPackStatus status, int schemaVersion);

    boolean existsByCodeAndStatus(String code, MissionPackStatus status);
}
