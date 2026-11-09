package com.chagokchagok.server.domain.mission.repository;

import com.chagokchagok.server.domain.mission.entity.Mission;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    List<Mission> findByMissionPackIdAndEnabledTrueOrderByCodeAsc(Long missionPackId);

    List<Mission> findByMissionPackIdOrderByCodeAsc(Long missionPackId);
}
